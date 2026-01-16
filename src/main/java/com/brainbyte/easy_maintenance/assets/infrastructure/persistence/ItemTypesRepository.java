package com.brainbyte.easy_maintenance.assets.infrastructure.persistence;

import com.brainbyte.easy_maintenance.assets.domain.ItemTypes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemTypesRepository extends JpaRepository<ItemTypes, Long> {

    Optional<ItemTypes> findByNormalizedName(String normalizedName);

    @Query("""
        select i from ItemTypes i
        where i.status = 'ACTIVE'
        and i.normalizedName like %:normalized%
    """)
    List<ItemTypes> findByNormalizedNameContaining(String normalized);

    @Query("""
        select i from ItemTypes i
        where i.status = 'ACTIVE'
        order by i.name
    """)
    List<ItemTypes> findAllActive();

}
