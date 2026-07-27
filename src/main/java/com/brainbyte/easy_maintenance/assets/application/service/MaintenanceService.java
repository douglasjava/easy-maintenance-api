package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceAttachmentSimpleResponse;
import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceFilter;
import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceResponse;
import com.brainbyte.easy_maintenance.assets.application.dto.RegisterMaintenanceRequest;
import com.brainbyte.easy_maintenance.commons.dto.CursorPageResponse;
import com.brainbyte.easy_maintenance.assets.component.ServiceBase;
import com.brainbyte.easy_maintenance.assets.domain.Maintenance;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceAttachment;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.rules.StatusCalculator;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceAttachmentRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.specification.MaintenanceSpecs;
import com.brainbyte.easy_maintenance.assets.mapper.IMaintenanceMapper;
import com.brainbyte.easy_maintenance.commons.exceptions.ConflictException;
import com.brainbyte.easy_maintenance.commons.exceptions.ForbiddenException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.commons.exceptions.TenantException;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Role;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceService {

    private final MaintenanceRepository maintenanceRepository;
    private final MaintenanceItemService maintenanceItemService;
    private final MaintenanceAttachmentRepository attachmentRepository;
    private final ServiceBase serviceBase;
    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;

    @Transactional
    public MaintenanceResponse register(String orgId, Long itemId, RegisterMaintenanceRequest req) {

        MaintenanceItem item = maintenanceItemService.findById(itemId);

        // BUGFIX (achado no QA manual, TASK-QA-MAN-011 C2): conferia LocalDate.now() (hoje) em vez
        // de req.performedAt() — bloqueava registrar uma manutenção com data passada sempre que já
        // existisse QUALQUER manutenção do item com performed_at = hoje, mesmo sem nenhum conflito
        // real com a data que estava sendo registrada.
        if (maintenanceRepository.existsByItemIdAndPerformedAt(itemId, req.performedAt())) {
            throw new ConflictException("Já existe uma manutenção registrada para este item na data informada");
        }

        validateRegister(orgId, req, item);

        Maintenance maintenance = IMaintenanceMapper.INSTANCE.toMaintenance(req, itemId);
        Long currentUserId = authenticationService.getCurrentUser().getId();
        maintenance.setCreatedBy(currentUserId);
        maintenance.setUpdatedBy(currentUserId);
        maintenanceRepository.save(maintenance);

        applyPerformedMaintenance(item, req.performedAt());
        item.setStatus(StatusCalculator.calculate(item.getNextDueAt()));
        item.setUpdatedAt(Instant.now());
        item.setUpdatedBy(currentUserId);
        MaintenanceItem savedItem = maintenanceItemService.save(item);

        log.info("Registered maintenance for item {}: {}", itemId, savedItem);

        return IMaintenanceMapper.INSTANCE.toMaintenanceResponse(maintenance);

    }


    public Page<MaintenanceResponse> listByItem(String orgId, MaintenanceFilter filter, Pageable pageable) {

        if (filter.itemId() != null) {
            MaintenanceItem item = maintenanceItemService.findById(filter.itemId());
            validateOrganization(orgId, item);
        }

        Specification<Maintenance> spec = MaintenanceSpecs.filter(orgId, filter);

        Page<MaintenanceResponse> page = maintenanceRepository.findAll(spec, pageable)
                .map(IMaintenanceMapper.INSTANCE::toMaintenanceResponse);

        Map<Long, String> typeMap = buildItemTypeMap(page.getContent());
        return page.map(r -> withItemType(r, typeMap));
    }

    public CursorPageResponse<MaintenanceResponse> listByItemCursor(String orgId,
                                                                     MaintenanceFilter filter,
                                                                     Long cursor,
                                                                     Long prevCursor,
                                                                     int size) {
        if (filter.itemId() != null) {
            MaintenanceItem item = maintenanceItemService.findById(filter.itemId());
            validateOrganization(orgId, item);
        }

        Specification<Maintenance> baseSpec = MaintenanceSpecs.filter(orgId, filter);

        if (cursor == null && prevCursor == null) {
            // OFFSET fallback — first page
            Page<Maintenance> page = maintenanceRepository.findAll(baseSpec, PageRequest.of(0, size, Sort.by("id").ascending()));
            Long nextCursor = page.hasNext() && !page.getContent().isEmpty() ? page.getContent().getLast().getId() : null;
            List<MaintenanceResponse> content = page.getContent().stream().map(IMaintenanceMapper.INSTANCE::toMaintenanceResponse).toList();
            Map<Long, String> typeMap = buildItemTypeMap(content);
            content = content.stream().map(r -> withItemType(r, typeMap)).toList();
            return new CursorPageResponse<>(content, nextCursor, null, page.hasNext(), size, page.getTotalElements(), page.getTotalPages(), page.getNumber());
        }

        if (prevCursor != null) {
            // Backward: fetch items before prevCursor
            Specification<Maintenance> backSpec = baseSpec.and((root, query, cb) -> cb.lessThan(root.get("id"), prevCursor));
            Page<Maintenance> raw = maintenanceRepository.findAll(backSpec, PageRequest.of(0, size + 1, Sort.by("id").descending()));
            boolean hasPrev = raw.getContent().size() > size;
            List<Maintenance> items = hasPrev ? raw.getContent().subList(0, size) : raw.getContent();
            ArrayList<Maintenance> ascending = new java.util.ArrayList<>(items);
            Collections.reverse(ascending);
            List<MaintenanceResponse> content = ascending.stream().map(IMaintenanceMapper.INSTANCE::toMaintenanceResponse).toList();
            Map<Long, String> typeMap = buildItemTypeMap(content);
            content = content.stream().map(r -> withItemType(r, typeMap)).toList();
            Long pc = (hasPrev && !content.isEmpty()) ? ascending.getFirst().getId() : null;
            return CursorPageResponse.ofCursor(content, null, pc, hasPrev, size);
        }

        // Forward: fetch items after cursor
        Specification<Maintenance> fwdSpec = baseSpec.and((root, query, cb) -> cb.greaterThan(root.get("id"), cursor));
        Page<Maintenance> raw = maintenanceRepository.findAll(fwdSpec, PageRequest.of(0, size + 1, Sort.by("id").ascending()));
        boolean hasMore = raw.getContent().size() > size;
        List<Maintenance> items = hasMore ? raw.getContent().subList(0, size) : raw.getContent();
        List<MaintenanceResponse> content = items.stream().map(IMaintenanceMapper.INSTANCE::toMaintenanceResponse).toList();
        Map<Long, String> typeMap = buildItemTypeMap(content);
        content = content.stream().map(r -> withItemType(r, typeMap)).toList();
        Long nc = (hasMore && !content.isEmpty()) ? items.getLast().getId() : null;
        return CursorPageResponse.ofCursor(content, nc, null, hasMore, size);
    }

    public MaintenanceResponse findById(String orgId, Long maintenanceId) {
        log.info("Finding maintenance {} for organization {}", maintenanceId, orgId);

        Maintenance maintenance = maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new NotFoundException(String.format("Manutenção não encontrada: %s", maintenanceId)));

        MaintenanceItem item = maintenanceItemService.findById(maintenance.getItemId());
        validateOrganization(orgId, item);

        List<MaintenanceAttachment> attachments = attachmentRepository.findByMaintenanceId(maintenanceId);
        List<MaintenanceAttachmentSimpleResponse> attachmentResponses = withAttachmentAuthorNames(
                IMaintenanceMapper.INSTANCE.toAttachmentSimpleResponseList(attachments));

        MaintenanceResponse base = IMaintenanceMapper.INSTANCE.toMaintenanceResponse(maintenance, attachmentResponses);
        return withItemType(base, item.getItemType());
    }

    // TASK-139: única consulta que enxerga canceladas de propósito (findCancelledByItemId contorna
    // @SQLRestriction via query nativa) — nunca chamada pela listagem/detalhe/export padrão. Sem
    // paginação: é uma view de auditoria secundária, volume esperado é baixo (correções, não
    // histórico operacional).
    public List<MaintenanceResponse> findCancelledByItem(String orgId, Long itemId) {
        MaintenanceItem item = maintenanceItemService.findById(itemId);
        validateOrganization(orgId, item);

        List<Maintenance> cancelled = maintenanceRepository.findCancelledByItemId(itemId);

        // TASK-141: "quem cancelou" precisa ser um nome, não um ID cru — mesmo padrão de resolução
        // em lote já usado em MaintenanceExportService.resolveUserNames (TASK-104), evita N+1.
        Set<Long> cancelledByIds = cancelled.stream()
                .map(Maintenance::getCancelledBy)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> cancellerNameById = resolveUserNames(cancelledByIds);

        // TASK-142: anexos de TODAS as manutenções canceladas buscados numa única query (não uma
        // por manutenção) — e os nomes de quem fez upload, também resolvidos numa única query sobre
        // o conjunto combinado, não por manutenção individual.
        Set<Long> maintenanceIds = cancelled.stream().map(Maintenance::getId).collect(Collectors.toSet());
        Map<Long, List<MaintenanceAttachment>> attachmentsByMaintenanceId = maintenanceIds.isEmpty()
                ? Map.of()
                : attachmentRepository.findByMaintenanceIdIn(maintenanceIds).stream()
                        .collect(Collectors.groupingBy(MaintenanceAttachment::getMaintenanceId));
        Set<Long> uploaderIds = attachmentsByMaintenanceId.values().stream()
                .flatMap(List::stream)
                .map(MaintenanceAttachment::getUploadedByUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> uploaderNameById = resolveUserNames(uploaderIds);

        return cancelled.stream()
                .map(m -> {
                    List<MaintenanceAttachment> attachments = attachmentsByMaintenanceId.getOrDefault(m.getId(), List.of());
                    List<MaintenanceAttachmentSimpleResponse> attachmentResponses = withAttachmentAuthorNames(
                            IMaintenanceMapper.INSTANCE.toAttachmentSimpleResponseList(attachments), uploaderNameById);
                    MaintenanceResponse base = IMaintenanceMapper.INSTANCE.toMaintenanceResponse(m, attachmentResponses);
                    MaintenanceResponse withType = withItemType(base, item.getItemType());
                    return withCancelledByName(withType, resolvedName(m.getCancelledBy(), cancellerNameById));
                })
                .toList();
    }

    // Sobrecarga de conveniência pra quem só tem os anexos de UMA manutenção (findById) — resolve
    // os nomes ali mesmo, sem precisar montar o mapa de fora.
    private List<MaintenanceAttachmentSimpleResponse> withAttachmentAuthorNames(List<MaintenanceAttachmentSimpleResponse> attachments) {
        Set<Long> ids = attachments.stream()
                .map(MaintenanceAttachmentSimpleResponse::uploadedByUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        return withAttachmentAuthorNames(attachments, resolveUserNames(ids));
    }

    private List<MaintenanceAttachmentSimpleResponse> withAttachmentAuthorNames(List<MaintenanceAttachmentSimpleResponse> attachments,
                                                                                 Map<Long, String> nameById) {
        return attachments.stream()
                .map(a -> new MaintenanceAttachmentSimpleResponse(a.id(), a.fileName(), a.attachmentType(),
                        a.uploadedByUserId(), a.uploadedAt(), resolvedName(a.uploadedByUserId(), nameById)))
                .toList();
    }

    private Map<Long, String> resolveUserNames(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getName));
    }

    private static String resolvedName(Long userId, Map<Long, String> nameById) {
        if (userId == null) return "—";
        return nameById.getOrDefault(userId, "—");
    }

    private static void validateRegister(String orgId, RegisterMaintenanceRequest req, MaintenanceItem item) {

        validateOrganization(orgId, item);
        validatePerformedAt(req);

    }

    private Map<Long, String> buildItemTypeMap(List<MaintenanceResponse> responses) {
        Set<Long> ids = responses.stream().map(MaintenanceResponse::itemId).collect(Collectors.toSet());
        return maintenanceItemService.findAllByIds(ids).stream()
                .collect(Collectors.toMap(MaintenanceItem::getId, MaintenanceItem::getItemType));
    }

    private static MaintenanceResponse withItemType(MaintenanceResponse r, Map<Long, String> typeMap) {
        return withItemType(r, typeMap.get(r.itemId()));
    }

    private static MaintenanceResponse withItemType(MaintenanceResponse r, String itemType) {
        return new MaintenanceResponse(r.id(), r.itemId(), itemType,
                r.performedAt(), r.type(), r.performedBy(), r.costCents(), r.nextDueAt(),
                r.attachments(), r.createdBy(), r.updatedBy(),
                r.cancelled(), r.cancelReason(), r.cancelledAt(), r.cancelledBy(), r.cancelledByName());
    }

    private static MaintenanceResponse withCancelledByName(MaintenanceResponse r, String cancelledByName) {
        return new MaintenanceResponse(r.id(), r.itemId(), r.itemType(),
                r.performedAt(), r.type(), r.performedBy(), r.costCents(), r.nextDueAt(),
                r.attachments(), r.createdBy(), r.updatedBy(),
                r.cancelled(), r.cancelReason(), r.cancelledAt(), r.cancelledBy(), cancelledByName);
    }

    // TASK-137: única forma de "corrigir" uma manutenção errada — nunca editar os campos originais.
    // Ordem deliberada: papel primeiro (checagem barata, independente de recurso, nenhuma info
    // vazada pra quem não tem permissão de jeito nenhum) → existência/idempotência (404 vs 409,
    // já com o filtro de organização embutido na query, ver MaintenanceRepository) → posse da
    // organização (403) → persistir motivo/autor/data → soft-delete (@SQLDelete já popula deleted_at).
    @Transactional
    public void cancel(String orgId, Long maintenanceId, String reason) {

        User currentUser = authenticationService.getCurrentUser();
        requireCancelPermission(currentUser);

        Maintenance maintenance = maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> resolveCancelNotFoundOrConflict(orgId, maintenanceId));

        MaintenanceItem item = maintenanceItemService.findById(maintenance.getItemId());
        validateOrganization(orgId, item);

        maintenance.setCancelledAt(Instant.now());
        maintenance.setCancelledBy(currentUser.getId());
        maintenance.setCancelReason(reason);
        // BUGFIX (QA manual, TASK-QA-MAN-011 C1): libera o dia pra uma nova manutenção no mesmo
        // item — sem isso, a linha cancelada continua ocupando (item_id, performed_at) pra sempre
        // e um novo registro no mesmo dia esbarra na UNIQUE (V85), mesmo já estando cancelada.
        maintenance.setActiveDedupKey(maintenance.getId());
        maintenanceRepository.saveAndFlush(maintenance);
        maintenanceRepository.delete(maintenance);

        recalculateItemAfterCancellation(item, currentUser.getId());

        log.info("Cancelled maintenance {} for item {}", maintenanceId, maintenance.getItemId());

    }

    // TASK-138: depois de cancelar, o item precisa ficar exatamente como estaria se a manutenção
    // cancelada nunca tivesse existido — recalcula a partir da manutenção válida (não cancelada)
    // mais recente por performedAt, não da "próxima que for cadastrada" (RN-016-03). A query já
    // respeita @SQLRestriction, então nunca enxerga a que acabou de ser cancelada nem nenhuma outra.
    private void recalculateItemAfterCancellation(MaintenanceItem item, Long currentUserId) {
        Optional<Maintenance> mostRecentValid =
                maintenanceRepository.findFirstByItemIdOrderByPerformedAtDescIdDesc(item.getId());

        if (mostRecentValid.isPresent()) {
            applyPerformedMaintenance(item, mostRecentValid.get().getPerformedAt());
        } else {
            resetToNeverPerformedState(item);
        }
        item.setStatus(StatusCalculator.calculate(item.getNextDueAt()));
        item.setUpdatedAt(Instant.now());
        item.setUpdatedBy(currentUserId);
        maintenanceItemService.save(item);
    }

    // Lógica de "aplicar uma manutenção performada ao item" compartilhada entre register() e o
    // recálculo do cancelamento — evita duplicar a regra e ela divergir com o tempo (RN-016
    // Critério de Aceite). Quando period == null, nextDueAt é deliberadamente deixado como está
    // (comportamento pré-existente de register(), preservado aqui).
    private void applyPerformedMaintenance(MaintenanceItem item, LocalDate performedAt) {
        item.setLastPerformedAt(performedAt);
        Period period = serviceBase.resolvePeriod(item);
        if (period != null) {
            item.setNextDueAt(performedAt.plus(period));
        }
    }

    // RN-016-04: sem nenhuma manutenção válida remanescente, o item volta ao estado "sem manutenção
    // registrada" — mesma fórmula de MaintenanceItemService.create() para um item sem
    // lastPerformedAt informado (base = hoje). O lastPerformedAt original de quando o item foi
    // criado não é recuperável aqui: register() já o sobrescreve a cada manutenção registrada, então
    // não existe um valor "anterior à primeira manutenção" para restaurar depois que pelo menos uma
    // já foi registrada.
    private void resetToNeverPerformedState(MaintenanceItem item) {
        item.setLastPerformedAt(null);
        Period period = serviceBase.resolvePeriod(item);
        item.setNextDueAt(period != null ? LocalDate.now().plus(period) : null);
    }

    private RuntimeException resolveCancelNotFoundOrConflict(String orgId, Long maintenanceId) {
        if (maintenanceRepository.existsCancelledByIdAndOrgCode(maintenanceId, orgId)) {
            return new ConflictException("Esta manutenção já foi cancelada");
        }
        return new NotFoundException(String.format("Manutenção não encontrada: %s", maintenanceId));
    }

    private static void requireCancelPermission(User user) {
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.SYNDIC) {
            throw new ForbiddenException("Apenas ADMIN ou SYNDIC podem cancelar uma manutenção.");
        }
    }

    private static void validateOrganization(String orgId, MaintenanceItem item) {

        if (!orgId.equals(item.getOrganizationCode())) {
            throw new TenantException(HttpStatus.FORBIDDEN, "Item não pertence a essa organização");
        }

    }

    private static void validatePerformedAt(RegisterMaintenanceRequest req) {

        if (req.performedAt().isAfter(LocalDate.now())) {
            throw new RuleException("performedAt cannot be in the future");
        }

    }


}
