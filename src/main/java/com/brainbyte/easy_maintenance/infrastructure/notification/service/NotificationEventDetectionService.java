package com.brainbyte.easy_maintenance.infrastructure.notification.service;

import com.brainbyte.easy_maintenance.assets.domain.Maintenance;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationEvent;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationEventType;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationReferenceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventDetectionService {

    private final MaintenanceItemRepository itemRepository;
    private final MaintenanceRepository maintenanceRepository;

    private static final Set<Integer> NEAR_DUE_OFFSETS = Set.of(30, 15, 7, 1);
    private static final Set<Integer> OVERDUE_OFFSETS = Set.of(0, -7, -15, -30);

    public List<NotificationEvent> detectEvents() {
        log.info("[DetectionService] Iniciando detecção de eventos de notificação.");
        LocalDate today = LocalDate.now();
        
        List<NotificationEvent> events = new ArrayList<>();
        
        events.addAll(detectItemEvents(today));
        events.addAll(detectMaintenanceEvents(today));
        
        log.info("[DetectionService] Detecção finalizada. Total de eventos: {}", events.size());
        return events;
    }

    private List<NotificationEvent> detectItemEvents(LocalDate today) {
        log.debug("[DetectionService] Detectando eventos para Itens.");
        
        Map<LocalDate, Integer> targetDates = getTargetDates(today);
        List<MaintenanceItem> items = itemRepository.findAllByNextDueAtIn(targetDates.keySet());
        
        log.info("[DetectionService] Encontrados {} itens elegíveis nas datas alvo.", items.size());
        
        return items.stream()
                .map(item -> {
                    int offset = (int) ChronoUnit.DAYS.between(today, item.getNextDueAt());
                    NotificationEventType type = offset > 0 ? NotificationEventType.ITEM_NEAR_DUE : NotificationEventType.ITEM_OVERDUE;
                    
                    return NotificationEvent.builder()
                            .organizationCode(item.getOrganizationCode())
                            .eventType(type)
                            .referenceType(NotificationReferenceType.ITEM)
                            .referenceId(item.getId())
                            .dueDate(item.getNextDueAt())
                            .daysOffset(Math.abs(offset))
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<NotificationEvent> detectMaintenanceEvents(LocalDate today) {
        log.debug("[DetectionService] Detectando eventos para Manutenções.");
        
        Map<LocalDate, Integer> targetDatesMap = getTargetDates(today);
        List<Maintenance> maintenances = maintenanceRepository.findAllByNextDueAtIn(targetDatesMap.keySet());
        
        log.info("[DetectionService] Encontradas {} manutenções elegíveis nas datas alvo.", maintenances.size());
        
        return maintenances.stream()
                .map(m -> {
                    int offset = (int) ChronoUnit.DAYS.between(today, m.getNextDueAt());
                    NotificationEventType type = offset > 0 ? NotificationEventType.MAINTENANCE_NEAR_DUE : NotificationEventType.MAINTENANCE_OVERDUE;
                    
                    // Como a manutenção não tem organizationCode direto na entidade Maintenance, 
                    // precisaremos buscar no item ou garantir que a query já trouxe. 
                    // No MaintenanceRepository que alterei, o JOIN foi feito mas a entidade retornada é Maintenance.
                    // Para evitar N+1 excessivo nesta fase de "log", vamos buscar o organizationCode de forma simples.
                    // Em um cenário de alta performance, poderíamos usar um DTO ou JOIN FETCH.
                    
                    String orgCode = itemRepository.findById(m.getItemId())
                            .map(MaintenanceItem::getOrganizationCode)
                            .orElse(null);

                    return NotificationEvent.builder()
                            .organizationCode(orgCode)
                            .eventType(type)
                            .referenceType(NotificationReferenceType.MAINTENANCE)
                            .referenceId(m.getId())
                            .dueDate(m.getNextDueAt())
                            .daysOffset(Math.abs(offset))
                            .build();
                })
                .collect(Collectors.toList());
    }

    private Map<LocalDate, Integer> getTargetDates(LocalDate today) {
        List<Integer> allOffsets = new ArrayList<>(NEAR_DUE_OFFSETS);
        allOffsets.addAll(OVERDUE_OFFSETS);
        
        return allOffsets.stream()
                .collect(Collectors.toMap(
                        offset -> today.plusDays(offset),
                        offset -> offset,
                        (o1, o2) -> o1 // No dia 0 poderia conflitar, mas aqui tratamos como um só
                ));
    }
}
