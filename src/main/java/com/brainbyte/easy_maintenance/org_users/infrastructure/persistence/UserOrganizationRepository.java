package com.brainbyte.easy_maintenance.org_users.infrastructure.persistence;

import com.brainbyte.easy_maintenance.org_users.domain.UserOrganization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserOrganizationRepository extends JpaRepository<UserOrganization, Long> {

    Optional<UserOrganization> findByUserIdAndOrganizationCode(Long userId, String organizationCode);

    List<UserOrganization> findAllByOrganizationCode(String organizationCode);

    @Query("SELECT uo FROM UserOrganization uo JOIN FETCH uo.user WHERE uo.organizationCode IN :orgCodes")
    List<UserOrganization> findAllByOrganizationCodeInWithUser(@Param("orgCodes") List<String> orgCodes);

    @Query("SELECT uo FROM UserOrganization uo JOIN FETCH uo.user WHERE uo.organizationCode = :orgCode")
    List<UserOrganization> findAllByOrganizationCodeWithUser(@Param("orgCode") String orgCode);

    // Mesmo join de findAllByOrganizationCodeWithUser, acrescido do nome da organização — usado pelo
    // canal WhatsApp (template "vencimento_manutencao_v2") para preencher a variável de empresa/tenant
    // sem round-trip extra (o join ON o.code = uo.organizationCode segue o mesmo padrão já usado em
    // OrganizationRepository.findAllByUserId, já que UserOrganization não tem @ManyToOne para Organization).
    @Query("SELECT new com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRecipient(uo.user, o.name) " +
            "FROM UserOrganization uo " +
            "JOIN uo.user " +
            "JOIN Organization o ON o.code = uo.organizationCode " +
            "WHERE uo.organizationCode = :orgCode")
    List<UserOrganizationRecipient> findRecipientsWithOrganizationName(@Param("orgCode") String orgCode);

    List<UserOrganization> findAllByUserId(Long userId);

    void deleteByUserIdAndOrganizationCode(Long userId, String organizationCode);

    long countByUserId(Long userId);

    long countByOrganizationCode(String organizationCode);

}
