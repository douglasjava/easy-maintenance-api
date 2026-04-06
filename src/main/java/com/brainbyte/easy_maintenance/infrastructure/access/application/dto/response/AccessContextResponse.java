package com.brainbyte.easy_maintenance.infrastructure.access.application.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AccessContextResponse {
    private UserInfo user;
    private AccountAccessResponse accountAccess;
    private List<OrganizationAccessResponse> organizationsAccess;

    @Data
    @Builder
    public static class UserInfo {
        private Long id;
        private String name;
    }
}
