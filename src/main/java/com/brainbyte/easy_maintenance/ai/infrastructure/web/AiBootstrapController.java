package com.brainbyte.easy_maintenance.ai.infrastructure.web;

import com.brainbyte.easy_maintenance.ai.application.dto.AiBootstrapApplyRequest;
import com.brainbyte.easy_maintenance.ai.application.dto.AiBootstrapApplyResponse;
import com.brainbyte.easy_maintenance.ai.application.dto.AiBootstrapPreviewRequest;
import com.brainbyte.easy_maintenance.ai.application.dto.AiBootstrapPreviewResponse;
import com.brainbyte.easy_maintenance.ai.application.service.AiBootstrapService;
import com.brainbyte.easy_maintenance.kernel.tenant.RequireTenant;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/ai/bootstrap")
@Tag(name = "AI Bootstrap (Onboarding)", description = "Geração de preview de cadastros iniciais via IA")
public class AiBootstrapController {

    private final AiBootstrapService bootstrapService;

    @PostMapping("/preview")
    @RequireTenant
    @Operation(summary = "Gera um preview de itens de manutenção para onboarding baseado no tipo de empresa")
    public ResponseEntity<AiBootstrapPreviewResponse> preview(@Validated @RequestBody AiBootstrapPreviewRequest request) {
        //return ResponseEntity.ok(bootstrapService.preview(request));
        return ResponseEntity.ok(mock());
    }

    @PostMapping("/apply")
    @RequireTenant
    @Operation(summary = "Aplica os cadastros gerados pela IA no banco de dados")
    public ResponseEntity<AiBootstrapApplyResponse> apply(@Validated @RequestBody AiBootstrapApplyRequest request) {
        return ResponseEntity.ok(bootstrapService.apply(request));
    }

    private AiBootstrapPreviewResponse mock() {
        try {

            var aiBootstrapPreviewResponse = """
                    {
                        "usedAi": true,
                        "companyType": "CONDOMINIO",
                        "items": [
                            {
                                "itemType": "ELEVADOR",
                                "category": "SEGURANCA",
                                "criticality": "ALTA",
                                "maintenance": {
                                    "norm": "ABNT (quando aplicável); IT do Corpo de Bombeiros (quando aplicável)",
                                    "periodUnit": "MESES",
                                    "periodQty": 1,
                                    "toleranceDays": 5,
                                    "notes": "Inspeção preventiva mensal por técnico credenciado: lubrificação, verificação de comandos e segurança, ensaio de dispositivos de frenagem e parada de emergência. Manutenção corretiva conforme necessidade; registro e comunicação ao fabricante quando aplicável."
                                }
                            },
                            {
                                "itemType": "SPDA",
                                "category": "SEGURANCA",
                                "criticality": "ALTA",
                                "maintenance": {
                                    "norm": "ABNT NBR 5419; IT do Corpo de Bombeiros (quando aplicável)",
                                    "periodUnit": "ANUAL",
                                    "periodQty": 1,
                                    "toleranceDays": 30,
                                    "notes": "Inspeção e ensaio anual da malha e condutores de descida, medição de resistência de aterramento, continuidade elétrica e integridade dos componentes; verificação adicional após descargas/raios ou intervenções na edificação."
                                }
                            },
                            {
                                "itemType": "BOMBAS",
                                "category": "SEGURANCA",
                                "criticality": "ALTA",
                                "maintenance": {
                                    "norm": "ABNT (quando aplicável); IT do Corpo de Bombeiros (para bombas de combate a incêndio)",
                                    "periodUnit": "DIAS",
                                    "periodQty": 7,
                                    "toleranceDays": 2,
                                    "notes": "Teste funcional semanal (acionamento) das bombas de incêndio/hidrante e bombas de pressurização: verificação de operação, pressões, válvulas, painéis elétricos e nível/combustível em motores diesel. Manutenção preventiva mensal e corretiva conforme resultado dos testes."
                                }
                            },
                            {
                                "itemType": "PORTAO_AUTOMATICO",
                                "category": "OPERACIONAL",
                                "criticality": "MEDIA",
                                "maintenance": {
                                    "norm": "ABNT (quando aplicável)",
                                    "periodUnit": "MESES",
                                    "periodQty": 6,
                                    "toleranceDays": 15,
                                    "notes": "Inspeção semestral: verificação de guias, rolamentos, correntes/correias, sistema de acionamento, dispositivos de segurança (fotocélulas, botoeiras), lubrificação e testes de parada de emergência. Ajustes e reparos conforme necessidade."
                                }
                            },
                            {
                                "itemType": "SISTEMA_DE_INCENDIO",
                                "category": "SEGURANCA",
                                "criticality": "ALTA",
                                "maintenance": {
                                    "norm": "IT do Corpo de Bombeiros; ABNT (quando aplicável)",
                                    "periodUnit": "MESES",
                                    "periodQty": 1,
                                    "toleranceDays": 7,
                                    "notes": "Inspeção mensal: verificação visual e teste dos detectores, painéis de alarme, sirenes, hidrantes, pressões, mangueiras e extintores. Ensaios e manutenção completos anuais conforme IT do Corpo de Bombeiros e documentação de conformidade."
                                }
                            }
                        ]
                    }""";

            return new ObjectMapper().readValue(aiBootstrapPreviewResponse, AiBootstrapPreviewResponse.class);


        } catch (Exception e) {
            System.out.println("Error");
        }
        return null;
    }
}
