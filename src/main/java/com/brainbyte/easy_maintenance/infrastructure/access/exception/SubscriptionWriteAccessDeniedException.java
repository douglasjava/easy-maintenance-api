package com.brainbyte.easy_maintenance.infrastructure.access.exception;

import com.brainbyte.easy_maintenance.infrastructure.access.domain.enums.AccessScope;
import lombok.Getter;

@Getter
public class SubscriptionWriteAccessDeniedException extends RuntimeException {
    private final AccessScope scope;

    public SubscriptionWriteAccessDeniedException(AccessScope scope) {
        super(scope == AccessScope.USER_ACCOUNT 
            ? "Sua assinatura de usuário está inativa. Você ainda pode acessar os dados existentes, mas esta operação não está permitida."
            : "A assinatura desta organização está inativa. Você ainda pode visualizar os dados existentes, mas as operações de gravação não são permitidas.");
        this.scope = scope;
    }
}
