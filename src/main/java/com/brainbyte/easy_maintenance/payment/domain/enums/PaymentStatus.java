package com.brainbyte.easy_maintenance.payment.domain.enums;

public enum PaymentStatus {

    PENDING,
    PAID,
    FAILED,
    CANCELED,
    REFUNDED,
    OVERDUE,
    RECEIVED,
    CHECKOUT_PAID,
    EXPIRED;

    public boolean isFinal() {
        return this == PAID
                || this == EXPIRED
                || this == CANCELED
                || this == FAILED;
    }

}
