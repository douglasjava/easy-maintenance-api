package com.brainbyte.easy_maintenance.catalog_norms.application.service;

import com.brainbyte.easy_maintenance.catalog_norms.application.dto.NormDTO;
import com.brainbyte.easy_maintenance.catalog_norms.infrastructure.persistence.NormRepository;
import com.brainbyte.easy_maintenance.catalog_norms.mapper.INormMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NormService {

  private final NormRepository repo;

  public List<NormDTO.NormResponse> findByItemType(String itemType) {
    log.info("Finding norms for itemType: {}", itemType);
    return repo.findByItemType(itemType)
            .stream().map(INormMapper.INSTANCE::toResponse).toList();
  }

  public NormDTO.NormResponse findById(Long id) {
    log.info("Finding norm by id: {}", id);
    return INormMapper.INSTANCE.toResponse(repo.findById(id).orElseThrow());
  }

  public List<NormDTO.NormResponse> findAll() {
    log.info("Finding all norms");
    return repo.findAll().stream().map(INormMapper.INSTANCE::toResponse).toList();
  }

}
