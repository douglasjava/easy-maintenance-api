package com.brainbyte.easy_maintenance.catalog_norms.infrastructure.persistence;

import com.brainbyte.easy_maintenance.catalog_norms.domain.Norm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NormRepository extends JpaRepository<Norm, Long> {

  List<Norm> findByItemType(String itemType);

}
