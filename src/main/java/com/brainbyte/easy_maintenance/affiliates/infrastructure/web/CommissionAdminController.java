package com.brainbyte.easy_maintenance.affiliates.infrastructure.web;

import com.brainbyte.easy_maintenance.affiliates.application.dto.AffiliateResponse;
import com.brainbyte.easy_maintenance.affiliates.application.dto.CommissionAdminResponse;
import com.brainbyte.easy_maintenance.affiliates.application.service.AffiliateService;
import com.brainbyte.easy_maintenance.affiliates.application.service.CommissionService;
import com.brainbyte.easy_maintenance.affiliates.domain.ReferralCommission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/easy-maintenance/api/v1/private/admin/affiliates-commissions")
@RequiredArgsConstructor
@Tag(name = "Admin — Comissões", description = "Gerenciamento de comissões de afiliados (admin)")
public class CommissionAdminController {

    private final CommissionService commissionService;
    private final AffiliateService affiliateService;

    @GetMapping("/commissions")
    @Operation(summary = "Listar todas as comissões")
    public List<CommissionAdminResponse> listCommissions() {
        return commissionService.listAll();
    }

    @PatchMapping("/commissions/{id}/pay")
    @Operation(summary = "Marcar comissão como paga")
    public CommissionAdminResponse markAsPaid(@PathVariable Long id) {
        ReferralCommission updated = commissionService.markAsPaid(id);
        return commissionService.listAll().stream()
                .filter(r -> r.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @GetMapping
    @Operation(summary = "Listar afiliados ativos")
    public List<AffiliateResponse> listAffiliates() {
        return affiliateService.listAllActive();
    }
}
