package com.brainbyte.easy_maintenance.org_users.infrastructure.persistence;

import com.brainbyte.easy_maintenance.org_users.domain.User;

/**
 * Projeção de {@code UserOrganizationRepository.findRecipientsWithOrganizationName} — carrega o
 * usuário destinatário e o nome de exibição da organização (para templates de notificação, ex.:
 * WhatsApp "vencimento_manutencao_v2") em uma única query, evitando um segundo round-trip só para
 * ler {@code Organization.name} pelo {@code organizationCode}.
 */
public record UserOrganizationRecipient(User user, String organizationName) {
}
