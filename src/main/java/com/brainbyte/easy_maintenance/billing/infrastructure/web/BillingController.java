package com.brainbyte.easy_maintenance.billing.infrastructure.web;

import com.brainbyte.easy_maintenance.billing.application.dto.BillingAccountDTO;
import com.brainbyte.easy_maintenance.billing.application.dto.BillingPlanDTO;
import com.brainbyte.easy_maintenance.billing.application.dto.response.InvoiceDetailResponse;
import com.brainbyte.easy_maintenance.billing.application.dto.response.InvoiceHistoryResponse;
import com.brainbyte.easy_maintenance.billing.application.dto.response.PendingPaymentResponse;
import com.brainbyte.easy_maintenance.billing.application.dto.dashboard.DashboardResponseDTO;
import com.brainbyte.easy_maintenance.billing.application.dto.response.BillingSummaryResponse;
import com.brainbyte.easy_maintenance.billing.application.service.BillingAccountService;
import com.brainbyte.easy_maintenance.billing.application.service.BillingDashboardService;
import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanService;
import com.brainbyte.easy_maintenance.billing.application.service.BillingRecoveryService;
import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.application.service.PaymentMethodTransitionService;
import com.brainbyte.easy_maintenance.billing.domain.enums.BillingStatus;
import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/me/billing")
@Tag(name = "Billing Dashboard", description = "Endpoints de faturamento do usuário")
public class BillingController {

    private final BillingDashboardService dashboardService;
    private final InvoiceService invoiceService;
    private final AuthenticationService authenticationService;
    private final BillingAccountService billingAccountService;
    private final BillingPlanService planService;
    private final BillingRecoveryService billingRecoveryService;
    private final PaymentMethodTransitionService paymentMethodTransitionService;

    @GetMapping("/plans")
    @Operation(summary = "Lista planos disponíveis com features para exibição pública")
    public List<BillingPlanDTO.PublicPlanResponse> listPublicPlans() {
        return planService.listPublicPlans();
    }

    @GetMapping("accounts")
    @Operation(summary = "Lista as contas de faturamento com filtros")
    public PageResponse<BillingAccountDTO.BillingAccountResponse> listBillingAccounts(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String doc,
            @RequestParam(required = false) BillingStatus status,
            @PageableDefault(size = 10) Pageable pageable) {
        return billingAccountService.findAll(email, name, doc, status, pageable);
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Retorna as informações consolidadas do dashboard de faturamento")
    public DashboardResponseDTO getDashboard() {
        var user = authenticationService.getCurrentUser();
        return dashboardService.getDashboard(user.getId());
    }

    @GetMapping("/summary")
    @Operation(summary = "Retorna o resumo completo de faturamento do usuário")
    public BillingSummaryResponse getSummary() {
        var user = authenticationService.getCurrentUser();
        return dashboardService.getBillingSummary(user.getId());
    }

    @GetMapping("/invoices")
    @Operation(summary = "Retorna o histórico de faturas do usuário autenticado")
    public PageResponse<InvoiceHistoryResponse> getInvoiceHistory(
            @PageableDefault(size = 10) Pageable pageable) {
        var user = authenticationService.getCurrentUser();
        return invoiceService.getInvoiceHistory(user.getId(), pageable);
    }

    @GetMapping("/invoices/{id}")
    @Operation(summary = "Retorna os detalhes de uma fatura específica")
    public InvoiceDetailResponse getInvoiceDetail(@PathVariable Long id) {
        var user = authenticationService.getCurrentUser();
        return invoiceService.getInvoiceDetail(user.getId(), id);
    }

    @PatchMapping("/payment-method")
    @Operation(summary = "Atualiza o método de pagamento da assinatura do usuário",
            description = "Permitido apenas em status TRIAL ou PAST_DUE.")
    public ResponseEntity<Void> updatePaymentMethod(
            @Valid @RequestBody BillingAccountDTO.UpdatePaymentMethodRequest request) {
        var user = authenticationService.getCurrentUser();
        billingAccountService.updatePaymentMethod(user.getId(), request.method());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/payment-failure")
    @Operation(summary = "Retorna o último motivo de falha de pagamento da assinatura")
    public ResponseEntity<BillingAccountDTO.PaymentFailureResponse> getPaymentFailure() {
        var user = authenticationService.getCurrentUser();
        return ResponseEntity.ok(billingAccountService.getLastPaymentFailure(user.getId()));
    }

    @PostMapping("/recover/pix")
    @Operation(summary = "Gera cobrança PIX de recuperação para assinatura em PAST_DUE",
            description = "Cria uma cobrança PIX avulsa para o valor em atraso. "
                    + "Retorna QR code e link de pagamento. "
                    + "Retorna 409 se já existe pagamento pendente para a assinatura. "
                    + "Retorna 422 se a assinatura não está em PAST_DUE.")
    public ResponseEntity<BillingAccountDTO.RecoveryPixResponse> recoverWithPix() {
        var user = authenticationService.getCurrentUser();
        return ResponseEntity.ok(billingRecoveryService.recoverWithPix(user.getId()));
    }

    @PostMapping("/recover/checkout")
    @Operation(summary = "Gera checkout CC de recuperação para assinatura em PAST_DUE",
            description = "Cancela a subscription antiga no Asaas e cria um novo checkout CC recorrente. "
                    + "Retorna o link do checkout para o usuário inserir os dados do cartão. "
                    + "Retorna 422 se a assinatura não está em PAST_DUE.")
    public ResponseEntity<BillingAccountDTO.RecoveryCheckoutResponse> recoverWithCheckout() {
        var user = authenticationService.getCurrentUser();
        return ResponseEntity.ok(billingRecoveryService.recoverWithCheckout(user.getId()));
    }

    @PostMapping("/transition/pix")
    @Operation(summary = "Transição CC → PIX para assinatura ACTIVE",
            description = "Cancela a subscription CC no Asaas e atualiza o método para PIX. "
                    + "O próximo ciclo usará PIX avulso criado pelo PixRenewalJob. "
                    + "Retorna 422 se a assinatura não está ACTIVE ou o método atual não é CC.")
    public ResponseEntity<Void> transitionToPix() {
        var user = authenticationService.getCurrentUser();
        paymentMethodTransitionService.transitionToPixFromCard(user.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/update-card")
    @Operation(summary = "Atualização de cartão CC → CC para assinatura ACTIVE",
            description = "Cria um novo checkout CC para atualização do cartão. "
                    + "Após pagamento, o webhook cancela a subscription antiga e vincula a nova. "
                    + "Retorna 422 se a assinatura não está ACTIVE ou o método atual não é CC.")
    public ResponseEntity<BillingAccountDTO.CardUpdateResponse> updateCard() {
        var user = authenticationService.getCurrentUser();
        return ResponseEntity.ok(paymentMethodTransitionService.initiateCardUpdate(user.getId()));
    }

    @PostMapping("/transition/card")
    @Operation(summary = "Transição PIX → CC para assinatura ACTIVE",
            description = "Atualiza o método de pagamento para CC. "
                    + "Se houver PIX pendente, retorna warning indicando o ciclo efetivo. "
                    + "Retorna 422 se a assinatura não está ACTIVE ou o método atual não é PIX.")
    public ResponseEntity<BillingAccountDTO.PaymentMethodTransitionResponse> transitionToCard() {
        var user = authenticationService.getCurrentUser();
        return ResponseEntity.ok(paymentMethodTransitionService.transitionToCardFromPix(user.getId()));
    }

    @GetMapping("/pending-payment")
    @Operation(summary = "Retorna o pagamento pendente da assinatura ativa do usuário, se houver",
            description = "Para usuários PIX, inclui pixQrCode, pixQrCodeBase64 e pixExpiresAt. "
                    + "Retorna 204 No Content quando não há pagamento pendente.")
    public ResponseEntity<PendingPaymentResponse> getPendingPayment() {
        var user = authenticationService.getCurrentUser();
        var response = dashboardService.getPendingPayment(user.getId());
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.noContent().build();
    }

}
