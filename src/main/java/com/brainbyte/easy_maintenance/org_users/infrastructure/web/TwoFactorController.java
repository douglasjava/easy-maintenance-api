package com.brainbyte.easy_maintenance.org_users.infrastructure.web;

import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.kernel.tenant.RequireTenant;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.org_users.application.service.TwoFactorService;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/me/2fa")
@Tag(name = "Autenticação em Dois Fatores", description = "Gerenciamento de 2FA do usuário")
public class TwoFactorController {

    private final TwoFactorService twoFactorService;
    private final AuthenticationService authenticationService;
    private final OrganizationRepository organizationRepository;

    @GetMapping("/status")
    @Operation(summary = "Retorna o status do 2FA do usuário autenticado")
    public UserDTO.TwoFactorStatusResponse getStatus() {
        Long userId = authenticationService.getCurrentUser().getId();
        return twoFactorService.getStatus(userId);
    }

    @PostMapping("/setup")
    @Operation(summary = "Inicia o setup do 2FA: retorna secret e QR code")
    public UserDTO.TwoFactorSetupResponse initiateSetup() {
        Long userId = authenticationService.getCurrentUser().getId();
        return twoFactorService.initiateSetup(userId);
    }

    @PostMapping("/confirm")
    @Operation(summary = "Confirma o código TOTP e habilita o 2FA, retornando os backup codes")
    public UserDTO.TwoFactorConfirmResponse confirmSetup(
            @Valid @RequestBody UserDTO.TwoFactorConfirmRequest request) {
        Long userId = authenticationService.getCurrentUser().getId();
        return twoFactorService.confirmSetup(userId, request.code());
    }

    @PostMapping("/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desabilita o 2FA usando código TOTP ou backup code")
    public void disable(@Valid @RequestBody UserDTO.TwoFactorDisableRequest request) {
        Long userId = authenticationService.getCurrentUser().getId();
        twoFactorService.disable(userId, request.code());
    }

    @PostMapping("/backup-codes/regenerate")
    @Operation(summary = "Regenera os backup codes (requer código TOTP atual)")
    public Map<String, List<String>> regenerateBackupCodes(
            @Valid @RequestBody UserDTO.TwoFactorRegenerateCodesRequest request) {
        Long userId = authenticationService.getCurrentUser().getId();
        List<String> codes = twoFactorService.regenerateBackupCodes(userId, request.code());
        return Map.of("backupCodes", codes);
    }

    // ─── Org admin: require 2FA for all members ──────────────────────────────

    @PatchMapping("/org/{orgCode}/require")
    @RequireTenant
    @Operation(summary = "Administrador: define se o 2FA é obrigatório para todos da organização")
    public Map<String, Boolean> setOrgRequire2fa(
            @PathVariable String orgCode,
            @RequestBody Map<String, Boolean> body) {
        boolean required = Boolean.TRUE.equals(body.get("required"));
        Organization org = organizationRepository.findByCode(orgCode)
                .orElseThrow(() -> new NotFoundException("Organização não encontrada: " + orgCode));
        org.setRequire2fa(required);
        organizationRepository.save(org);
        return Map.of("require2fa", required);
    }
}
