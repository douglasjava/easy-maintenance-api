package com.brainbyte.easy_maintenance.org_users.infrastructure.web;

import com.brainbyte.easy_maintenance.org_users.application.dto.LgpdDTO;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.org_users.application.service.LgpdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/me/lgpd")
@Tag(name = "LGPD", description = "Direitos de portabilidade e exclusão de dados pessoais")
public class LgpdController {

    private final LgpdService lgpdService;
    private final AuthenticationService authenticationService;

    @GetMapping("/data-export")
    @Operation(summary = "Exportar dados pessoais do usuário autenticado")
    public ResponseEntity<LgpdDTO.DataExportResponse> exportData() {
        Long userId = authenticationService.getCurrentUser().getId();
        return ResponseEntity.ok(lgpdService.exportData(userId));
    }

    @DeleteMapping("/account")
    @Operation(summary = "Anonimizar e excluir conta do usuário autenticado (direito ao esquecimento)")
    public ResponseEntity<Void> deleteAccount(@Valid @RequestBody LgpdDTO.AnonymizeAccountRequest req) {
        Long userId = authenticationService.getCurrentUser().getId();
        lgpdService.anonymizeAccount(userId, req.confirmPassword());
        return ResponseEntity.noContent().build();
    }
}
