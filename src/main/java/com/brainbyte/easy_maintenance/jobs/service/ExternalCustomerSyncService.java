package com.brainbyte.easy_maintenance.jobs.service;

import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.onboarding.mapper.IOnboardingMapper;
import com.brainbyte.easy_maintenance.payment.application.factory.PaymentProviderFactory;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalCustomerSyncService {

    private final BillingAccountRepository billingAccountRepository;
    private final PaymentProviderFactory paymentProviderFactory;

    public void syncMissingExternalCustomerIds() {
        List<BillingAccount> accounts = billingAccountRepository.findByExternalCustomerIdIsNull();

        if (accounts.isEmpty()) {
            log.info("[ExternalCustomerSync] Nenhuma conta sem externalCustomerId. Nada a sincronizar.");
            return;
        }

        log.info("[ExternalCustomerSync] {} conta(s) sem externalCustomerId encontradas. Iniciando sincronização.", accounts.size());

        int success = 0;
        int failure = 0;

        for (BillingAccount account : accounts) {
            try {
                var customer = IOnboardingMapper.INSTANCE.toCustomerDTO(account);
                var externalCustomerId = paymentProviderFactory.get(PaymentProvider.ASAAS).createExternalCustomer(customer);
                account.setExternalCustomerId(externalCustomerId);
                billingAccountRepository.save(account);
                log.info("[ExternalCustomerSync] externalCustomerId sincronizado para billingAccountId={}: {}", account.getId(), externalCustomerId);
                success++;
            } catch (Exception e) {
                log.warn("[ExternalCustomerSync] Falha ao sincronizar billingAccountId={}. Será reprocessado na próxima execução. Erro: {}",
                        account.getId(), e.getMessage());
                failure++;
            }
        }

        log.info("[ExternalCustomerSync] Sincronização concluída. Sucesso: {}, Falhas: {}.", success, failure);

        if (failure > 0) {
            log.error("[ExternalCustomerSync] {} conta(s) não puderam ser sincronizadas com o Asaas. Verificar logs acima.", failure);
        }
    }
}
