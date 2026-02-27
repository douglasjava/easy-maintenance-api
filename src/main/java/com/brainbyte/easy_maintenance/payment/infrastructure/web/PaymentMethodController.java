package com.brainbyte.easy_maintenance.payment.infrastructure.web;

import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import com.brainbyte.easy_maintenance.payment.application.dto.PaymentMethodDTO;
import com.brainbyte.easy_maintenance.payment.application.service.PaymentMethodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/easy-maintenance/api/v1/me/payment-methods")
@Tag(name = "Payment Method User", description = "Operações de métodos de pagamento para o usuário autenticado")
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;
    private final AuthenticationService authenticationService;

    @GetMapping
    @Operation(summary = "Lista os métodos de pagamento do usuário logado")
    public List<PaymentMethodDTO.PaymentMethodResponse> listMyPaymentMethods() {
        Long userId = authenticationService.getCurrentUser().getId();
        return paymentMethodService.listUserPaymentMethods(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Adiciona um novo método de pagamento para o usuário logado")
    public PaymentMethodDTO.PaymentMethodResponse createMyPaymentMethod(@RequestBody @Valid PaymentMethodDTO.CreatePaymentMethodRequest request) {
        Long userId = authenticationService.getCurrentUser().getId();
        return paymentMethodService.createPaymentMethod(userId, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove um método de pagamento do usuário logado")
    public void deleteMyPaymentMethod(@PathVariable Long id) {
        Long userId = authenticationService.getCurrentUser().getId();
        paymentMethodService.deletePaymentMethod(userId, id);
    }

    @PatchMapping("/{id}/default")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Define um método de pagamento como padrão para o usuário logado")
    public void setMyDefaultPaymentMethod(@PathVariable Long id) {
        Long userId = authenticationService.getCurrentUser().getId();
        paymentMethodService.setDefault(userId, id);
    }

}
