package com.brainbyte.easy_maintenance.org_users.application.service;

import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.commons.exceptions.ConflictException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.org_users.application.dto.OrganizationDTO;
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

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationsService {

  private final OrganizationRepository repository;
  private final UserRepository userRepository;

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

  public OrganizationDTO.OrganizationResponse findById(Long id) {
    log.info("Getting organization with id {}", id);
    var organization = repository.findById(id)
            .orElseThrow(() -> new NotFoundException(String.format("Organization with id %s not found", id)));

    var organizationResponse = IOrganizationMapper.INSTANCE.toOrganizationResponse(organization);

    var users = userRepository.findAllByOrganizationCode(organization.getCode(), Pageable.ofSize(1));
    if (!users.isEmpty()) {
      var user = users.getContent().stream().findFirst().orElse(null);
      var responsibleUser = IOrganizationMapper.INSTANCE.toResponsibleUser(user);
      organizationResponse = new OrganizationDTO.OrganizationResponse(
              organizationResponse.id(),
              organizationResponse.code(),
              organizationResponse.name(),
              organizationResponse.plan(),
              organizationResponse.city(),
              organizationResponse.street(),
              organizationResponse.number(),
              organizationResponse.complement(),
              organizationResponse.neighborhood(),
              organizationResponse.state(),
              organizationResponse.zipCode(),
              organizationResponse.country(),
              organizationResponse.doc(),
              responsibleUser
      );
    }

    return organizationResponse;
  }

  public OrganizationDTO.OrganizationResponse update(Long id, OrganizationDTO.UpdateOrganizationRequest request) {
    log.info("Updating organization with id {}", id);

    var organization = repository.findById(id)
            .orElseThrow(() -> new NotFoundException(String.format("Organization with id %s not found", id)));

    organization.setName(request.name());
    organization.setPlan(request.plan());
    organization.setCity(request.city());
    organization.setDoc(request.doc());
    organization.setUpdatedAt(Instant.now());

    var organizationSaved = repository.save(organization);

    return IOrganizationMapper.INSTANCE.toOrganizationResponse(organizationSaved);

  }

  public OrganizationDTO.OrganizationResponse create(OrganizationDTO.CreateOrganizationRequest request) {
    log.info("Creating organization with id {}", request.code());
    try {

      if (existsByCode(request.code())) {
        throw new ConflictException(String.format("Organization with id %s already exists", request.code()));
      }

      var organization = IOrganizationMapper.INSTANCE.toOrganization(request);
      organization = repository.save(organization);

      return IOrganizationMapper.INSTANCE.toOrganizationResponse(organization);

    } catch(ConflictException e) {
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

}
