package com.brainbyte.easy_maintenance.push.infrastructure.web;

import com.brainbyte.easy_maintenance.push.application.dto.*;
import com.brainbyte.easy_maintenance.push.application.service.PushTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/easy-maintenance/api/v1/push")
@RequiredArgsConstructor
@Tag(name = "Push Notifications", description = "Gerenciamento de tokens FCM para notificações")
public class PushTokensController {

    private final PushTokenService service;

    @PostMapping("/tokens")
    @Operation(
            summary = "Registrar ou atualizar token (Público)",
            description = "Registra um novo token FCM ou atualiza um existente de forma anônima. " +
                          "Garante que o token esteja ativo e atualiza informações do dispositivo.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Token registrado/atualizado com sucesso")
            }
    )
    public PushTokenResponse registerOrUpdatePublic(@Valid @RequestBody RegisterOrUpdateTokenRequest request) {
        return service.registerOrUpdatePublic(request);
    }


    @PostMapping("/tokens/link")
    @Operation(
            summary = "Vincular token ao usuário (Autenticado)",
            description = "Associa um token FCM ao usuário logado no contexto da requisição (JWT).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Token vinculado com sucesso"),
                    @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
            }
    )
    public PushTokenResponse linkToUser(@Valid @RequestBody LinkTokenRequest request, Authentication authentication) {
        return service.linkToAuthenticatedUser(authentication, request.token());
    }


    @PatchMapping("/tokens/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Desativar token",
            description = "Marca um token como inativo (ex: no logout ou erro do FCM). " +
                          "O registro permanece no banco para histórico.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Token desativado com sucesso")
            }
    )
    public void disable(@Valid @RequestBody DisableTokenRequest request) {
        service.disableToken(request.token());
    }

}
