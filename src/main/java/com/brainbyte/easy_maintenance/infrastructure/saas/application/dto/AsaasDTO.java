package com.brainbyte.easy_maintenance.infrastructure.saas.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class AsaasDTO {


    public record CreateCustomerRequest(
            String name,
            @JsonProperty("cpfCnpj") String cpfCnpj,
            String email,
            String phone,
            String mobilePhone,
            @JsonProperty("postalCode") String postalCode,
            String address,
            @JsonProperty("addressNumber") String addressNumber,
            String complement,
            @JsonProperty("province") String province,
            String city,
            String state,
            String country
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CustomerResponse(
            String id,
            String name,
            @JsonProperty("cpfCnpj") String cpfCnpj,
            String email
    ) {}


    public enum BillingType { BOLETO, CREDIT_CARD, PIX }
    public enum Cycle { WEEKLY, BIWEEKLY, MONTHLY, BIMONTHLY, QUARTERLY, SEMIANNUALLY, YEARLY }
    public enum ChargeTypes { RECURRENT, INSTALLMENT, DETACHED }

    public record CreateSubscriptionRequest(
            String customer,
            BillingType billingType,
            BigDecimal value,
            LocalDate nextDueDate,
            Cycle cycle,
            String description
    ) {}

    public record UpdateSubscriptionRequest(
            BigDecimal value,
            Cycle cycle,
            BillingType billingType,
            String updateNextBillingPeriod
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubscriptionResponse(
            String id,
            String customer,
            BillingType billingType,
            BigDecimal value,
            LocalDate nextDueDate,
            Cycle cycle,
            String description,
            String status
    ) {}

    public record CreatePaymentRequest(
            String customer,
            BillingType billingType,
            BigDecimal value,
            LocalDate dueDate,
            String description,
            String externalReference
    ) {}

    public record CreateCheckoutRequest(
            List<BillingType> billingTypes,
            List<ChargeTypes> chargeTypes,
            Integer minutesToExpire,
            String externalReference,
            CheckoutCallback callback,
            List<CheckoutItem> items,
            CheckoutSubscription subscription,
            String customer
    ) {}

    public record CheckoutCallback(
            String successUrl,
            String cancelUrl,
            String expiredUrl
    ) {}

    public record CheckoutItem(
            String externalReference,
            String description,
            String name,
            Integer quantity,
            BigDecimal value
    ) {}

    public record CheckoutSubscription(
            Cycle cycle,
            String endDate,
            String nextDueDate
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CheckoutResponse(
            String id,
            @JsonProperty("link") String link,
            String paymentUrl,
            String paymentLink
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentResponse(
            String id,
            String customer,
            BillingType billingType,
            BigDecimal value,
            LocalDate dueDate,
            String status,
            @JsonProperty("invoiceUrl") String invoiceUrl,
            @JsonProperty("bankSlipUrl") String bankSlipUrl
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookEvent(
            String event,
            PaymentObject payment
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentObject(
            String id,
            String customer,
            String subscription,
            String status,
            BigDecimal value,
            LocalDate dueDate,
            LocalDate creditDate,
            String description,
            String checkoutSession,
            @JsonProperty("billingType") String billingType,
            @JsonProperty("externalReference") String externalReference,
            @JsonProperty("invoiceUrl") String invoiceUrl,
            @JsonProperty("transactionReceiptUrl") String transactionReceiptUrl,
            @JsonProperty("nossoNumero") String nossoNumero,
            @JsonProperty("invoiceNumber") String invoiceNumber,
            BigDecimal netValue,
            LocalDate confirmedDate,
            LocalDate paymentDate,
            String installment,
            DiscountObject discount,
            @JsonProperty("pixTransaction") PixTransaction pixTransaction
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PixTransaction(
            String id,
            @JsonProperty("qrCode") PixQrCode qrCode
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PixQrCode(
            @JsonProperty("encodedImage") String encodedImage,
            @JsonProperty("payload") String payload,
            @JsonProperty("expirationDate") LocalDateTime expirationDate
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DiscountObject(
            BigDecimal value,
            String type
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookCheckoutEvent(
            String id,
            String event,
            @JsonProperty("dateCreated") String dateCreated,
            WebhookAccount account,
            WebhookCheckout checkout,
            PaymentObject payment,
            WebhookSubscription subscription
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookSubscription(
            String id,
            String externalReference,
            String checkoutSession,
            LocalDate nextDueDate,
            String dateCreated,
            String status,
            boolean deleted
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookAccount(
            String id,
            String ownerId
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookCheckout(
            String id,
            String link,
            String status,
            Integer minutesToExpire,
            List<String> billingTypes,
            List<String> chargeTypes,
            CheckoutCallback callback,
            List<WebhookCheckoutItem> items,
            WebhookCheckoutSubscription subscription,
            String customer
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookCheckoutItem(
            String name,
            String description,
            Integer quantity,
            BigDecimal value
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookCheckoutSubscription(
            String cycle,
            String nextDueDate,
            String endDate
    ) {}

}
