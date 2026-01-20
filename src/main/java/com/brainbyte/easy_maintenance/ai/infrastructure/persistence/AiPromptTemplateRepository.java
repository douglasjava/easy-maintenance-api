package com.brainbyte.easy_maintenance.ai.infrastructure.persistence;

import com.brainbyte.easy_maintenance.ai.domain.AiPromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiPromptTemplateRepository extends JpaRepository<AiPromptTemplate, Long> {

    @Query("""
            SELECT t FROM AiPromptTemplate t
            WHERE t.templateKey = :templateKey
              AND t.companyType = :companyType
              AND t.status = 'ACTIVE'
            ORDER BY t.version DESC
            LIMIT 1
            """)
    Optional<AiPromptTemplate> findLatestActive(@Param("templateKey") String templateKey, 
                                               @Param("companyType") String companyType);
}
