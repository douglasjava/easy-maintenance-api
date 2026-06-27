package com.brainbyte.easy_maintenance.org_users.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.response.BillingSubscriptionResponse;
import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanService;
import com.brainbyte.easy_maintenance.billing.application.service.BillingSubscriptionService;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingPlanRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.commons.exceptions.ConflictException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.affiliates.application.service.AffiliateService;
import com.brainbyte.easy_maintenance.org_users.application.dto.OrganizationDTO;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Plan;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.specifications.OrganizationSpecifications;
import com.brainbyte.easy_maintenance.org_users.mapper.IOrganizationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;


@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationsService {

    private final OrganizationRepository repository;
    private final BillingSubscriptionItemRepository billingSubscriptionItemRepository;
    private final BillingSubscriptionService billingSubscriptionService;
    private final BillingPlanRepository billingPlanRepository;
    private final BillingAccountRepository billingAccountRepository;
    private final UserRepository userRepository;
    private final AffiliateService affiliateService;
    private final BillingPlanFeaturesHelper billingPlanFeaturesHelper;
    private final UserOrganizationRepository userOrganizationRepository;

    @Transactional
    public void applyReferralCode(String orgCode, String referralCode) {
        repository.findByCode(orgCode).ifPresent(org -> {
            org.setReferralCode(referralCode);
            repository.save(org);
            log.info("[Affiliate] referralCode={} applied to org={}", referralCode, orgCode);
        });
    }

    public boolean existsByCode(String code) {
        log.info("Checking if organization with id {} exists", code);
        return repository.existsByCode(code);
    }

    public PageResponse<OrganizationDTO.OrganizationResponse> listAll(String name, Plan plan, String city, String doc, Pageable pageable) {
        log.info("Listing all organizations with filters");

        var spec = Specification.allOf(
                OrganizationSpecifications.withNameLike(name),
                OrganizationSpecifications.withPlan(plan),
                OrganizationSpecifications.withCityLike(city),
                OrganizationSpecifications.withDocLike(doc)
        );

        Page<OrganizationDTO.OrganizationResponse> page =
                repository.findAll(spec, pageable).map(IOrganizationMapper.INSTANCE::toOrganizationResponse);

        return PageResponse.of(page);

    }

    public OrganizationDTO.OrganizationResponse findByIdWithoutBusiness(Long id) {
        log.info("Getting organization without Business and with id {}", id);
        var organization = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(String.format("Organization with id %s not found", id)));

        var organizationResponse = IOrganizationMapper.INSTANCE.toOrganizationResponse(organization);

        organizationResponse = new OrganizationDTO.OrganizationResponse(
                organizationResponse.id(),
                organizationResponse.code(),
                organizationResponse.name(),
                organizationResponse.city(),
                organizationResponse.street(),
                organizationResponse.number(),
                organizationResponse.complement(),
                organizationResponse.neighborhood(),
                organizationResponse.state(),
                organizationResponse.zipCode(),
                organizationResponse.country(),
                organizationResponse.doc(),
                organizationResponse.companyType(),
                null
        );


        return organizationResponse;
    }

    public OrganizationDTO.OrganizationResponse findById(Long id) {
        log.info("Getting organization with id {}", id);
        var organization = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(String.format("Organization with id %s not found", id)));

        var organizationResponse = IOrganizationMapper.INSTANCE.toOrganizationResponse(organization);
        var billingSubscriptionItem = billingSubscriptionItemRepository.findBySourceId(organization.getCode())
                .orElseThrow(() -> new NotFoundException(String.format("Billing Subscription with code %s not found", organization.getCode())));


        organizationResponse = new OrganizationDTO.OrganizationResponse(
                organizationResponse.id(),
                organizationResponse.code(),
                organizationResponse.name(),
                organizationResponse.city(),
                organizationResponse.street(),
                organizationResponse.number(),
                organizationResponse.complement(),
                organizationResponse.neighborhood(),
                organizationResponse.state(),
                organizationResponse.zipCode(),
                organizationResponse.country(),
                organizationResponse.doc(),
                organizationResponse.companyType(),
                billingSubscriptionItem.getId()
        );


        return organizationResponse;
    }

    public OrganizationDTO.OrganizationResponse update(Long id, OrganizationDTO.UpdateOrganizationRequest request) {
        log.info("Updating organization with id {}", id);

        var organization = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(String.format("Organization with id %s not found", id)));

        organization.setName(request.name());
        organization.setCity(request.city());
        organization.setStreet(request.street());
        organization.setNumber(request.number());
        organization.setComplement(request.complement());
        organization.setNeighborhood(request.neighborhood());
        organization.setState(request.state());
        organization.setZipCode(request.zipCode());
        organization.setCountry(request.country());
        organization.setDoc(request.doc());

        organization.setUpdatedAt(Instant.now());

        var organizationSaved = repository.save(organization);

        return IOrganizationMapper.INSTANCE.toOrganizationResponse(organizationSaved);

    }

    public void validateOrgLimit(Long userId) {
        Optional<BillingSubscriptionItem> subscriptionItemOpt = billingSubscriptionItemRepository
                .findBySourceTypeAndSourceId(BillingSubscriptionItemSourceType.USER, userId.toString());

        if (subscriptionItemOpt.isEmpty()) {
            return; // sem assinatura ativa (onboarding ainda não concluído) → permite criação
        }

        int maxOrganizations = billingPlanFeaturesHelper.parse(subscriptionItemOpt.get().getPlan()).getMaxOrganizations();

        if (maxOrganizations <= 0) {
            return; // maxOrganizations=0 significa ilimitado
        }

        long currentOrgs = userOrganizationRepository.countByUserId(userId);

        if (currentOrgs >= maxOrganizations) {
            throw new RuleException(String.format(
                    "Limite de organizações atingido (%d/%d). Faça upgrade do seu plano para adicionar mais organizações.",
                    currentOrgs, maxOrganizations));
        }
    }

    public OrganizationDTO.OrganizationResponse create(OrganizationDTO.CreateOrganizationRequest request) {
        log.info("Creating organization with id {} ", request.code());

        try {

            if (existsByCode(request.code())) {
                throw new ConflictException(String.format("Empresa com esse código %s já existe", request.code()));
            }

            var organization = IOrganizationMapper.INSTANCE.toOrganization(request);

            // Resolve referralCode: explicit value takes priority; fallback to email auto-match
            String referralCode = request.referralCode();
            if (referralCode == null && request.userEmail() != null) {
                referralCode = affiliateService.suggestForEmail(request.userEmail())
                        .map(a -> a.getCode())
                        .orElse(null);
                if (referralCode != null) {
                    log.info("[Affiliate] Auto-matched referralCode={} for email={}", referralCode, request.userEmail());
                }
            }
            organization.setReferralCode(referralCode);

            organization = repository.save(organization);

            return IOrganizationMapper.INSTANCE.toOrganizationResponse(organization);

        } catch (ConflictException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating organization", e);
            throw new RuleException("Error creating organization");
        }

    }

    public List<OrganizationDTO.OrganizationResponse> listAllByCodes(List<String> codes) {
        log.info("Listing all organizations by codes: {}", codes);
        return repository.findAllByCodeIn(codes).stream()
                .map(IOrganizationMapper.INSTANCE::toOrganizationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BillingSubscriptionResponse.SubscriptionItemResponse getOrganizationSubscription(String orgCode) {
        log.info("Getting subscription for organization: {}", orgCode);
        BillingSubscriptionItem item = billingSubscriptionItemRepository
                .findBySourceTypeAndSourceId(BillingSubscriptionItemSourceType.ORGANIZATION, orgCode)
                .orElseThrow(() -> new NotFoundException("Assinatura não encontrada para organização: " + orgCode));

        return new BillingSubscriptionResponse.SubscriptionItemResponse(
                item.getId(),
                item.getSourceId(),
                item.getSourceType().name(),
                item.getPlan().getCode(),
                item.getPlan().getName(),
                item.getValueCents(),
                item.getNextPlan() != null ? item.getNextPlan().getCode() : null,
                item.getPlanChangeEffectiveAt(),
                item.getBillingSubscription().getStatus(),
                item.getBillingSubscription().getCurrentPeriodStart(),
                item.getBillingSubscription().getCurrentPeriodEnd(),
                item.getActivatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<OrganizationDTO.OrganizationWithSubscriptionResponse> listUserOrganizations(Long userId) {
        log.info("Listing all organizations for user id: {}", userId);

        List<Organization> organizations = repository.findAllByUserId(userId);
        List<String> codes = organizations.stream().map(Organization::getCode).toList();

        List<BillingSubscriptionItem> subscriptionItems = billingSubscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                BillingSubscriptionItemSourceType.ORGANIZATION, codes);

        return organizations.stream()
                .map(org -> {
                    var orgResponse = IOrganizationMapper.INSTANCE.toOrganizationResponse(org);
                    var item = subscriptionItems.stream()
                            .filter(i -> i.getSourceId().equals(org.getCode()))
                            .findFirst()
                            .orElse(null);

                    BillingSubscriptionResponse.SubscriptionItemResponse subResponse = null;
                    if (item != null) {
                        subResponse = new BillingSubscriptionResponse.SubscriptionItemResponse(
                                item.getId(),
                                item.getSourceId(),
                                item.getSourceType().name(),
                                item.getPlan().getCode(),
                                item.getPlan().getName(),
                                item.getValueCents(),
                                item.getNextPlan() != null ? item.getNextPlan().getCode() : null,
                                item.getPlanChangeEffectiveAt(),
                                item.getBillingSubscription().getStatus(),
                                item.getBillingSubscription().getCurrentPeriodStart(),
                                item.getBillingSubscription().getCurrentPeriodEnd(),
                                item.getActivatedAt()
                        );
                    }

                    return new OrganizationDTO.OrganizationWithSubscriptionResponse(orgResponse, subResponse);
                })
                .toList();
    }

    @Transactional
    public BillingSubscriptionResponse.SubscriptionItemResponse addOrganizationSubscription(String orgCode,
                                                                BillingSubscriptionResponse.SubscriptionItemRequest request) {

        log.info("1. Obter ou inicializar BillingSubscription do usuário {}", request.payerUserId());
        var billingSubscription = billingSubscriptionService.findByUser(request.payerUserId())
                .orElseGet(() -> initializeSubscriptionForUser(request.payerUserId(), request.paymentMethod()));

        BillingPlan billingPlan = billingPlanRepository.findByCode(request.planCode())
                .orElseThrow(() -> new NotFoundException(String.format("Não existe plano %s cadastrado", request.planCode())));

        billingSubscriptionService.addItem(billingSubscription,
                BillingSubscriptionItemSourceType.ORGANIZATION, orgCode, billingPlan);

        return getOrganizationSubscription(orgCode);
    }

    // Cria BillingAccount + BillingSubscription (TRIAL) para usuários criados pelo admin sem onboarding
    private BillingSubscription initializeSubscriptionForUser(Long userId, PaymentMethodType paymentMethod) {
        log.info("Inicializando BillingAccount e BillingSubscription para usuário {} (sem onboarding)", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado: " + userId));
        BillingAccount account = billingAccountRepository.findByUserId(userId)
                .orElseGet(() -> billingAccountRepository.save(BillingAccount.builder()
                        .user(user)
                        .name(user.getName())
                        .billingEmail(user.getEmail())
                        .paymentMethod(paymentMethod)
                        .build()));
        return billingSubscriptionService.createTrial(account, Duration.ofDays(7));
    }

}
