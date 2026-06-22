package com.brainbyte.easy_maintenance.affiliates.infrastructure.web;

import com.brainbyte.easy_maintenance.affiliates.application.dto.AffiliateResponse;
import com.brainbyte.easy_maintenance.affiliates.application.dto.CreateAffiliateRequest;
import com.brainbyte.easy_maintenance.affiliates.application.service.AffiliateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AffiliateControllerTest {

    @Mock AffiliateService affiliateService;
    @InjectMocks AffiliateController controller;

    @Test
    void register_delegatesToService_andReturnsResponse() {
        AffiliateResponse expected = new AffiliateResponse(
                1L, "João", "joao@test.com", "ABC123",
                "https://easymaintenance.com.br/landing?ref=ABC123",
                new BigDecimal("0.20"));
        when(affiliateService.createAffiliate(any())).thenReturn(expected);

        AffiliateResponse result = controller.register(
                new CreateAffiliateRequest("João", "joao@test.com", "31999999999"));

        assertThat(result.code()).isEqualTo("ABC123");
        assertThat(result.link()).contains("?ref=ABC123");
        verify(affiliateService).createAffiliate(any());
    }

    @Test
    void register_propagatesExceptionFromService_whenEmailDuplicate() {
        when(affiliateService.createAffiliate(any()))
                .thenThrow(new IllegalArgumentException("E-mail já cadastrado como afiliado."));

        assertThatThrownBy(() -> controller.register(
                new CreateAffiliateRequest("X", "dup@test.com", "31")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("já cadastrado");
    }
}
