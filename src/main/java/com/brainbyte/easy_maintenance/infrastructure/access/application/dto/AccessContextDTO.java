package com.brainbyte.easy_maintenance.infrastructure.access.application.dto;

import com.brainbyte.easy_maintenance.infrastructure.access.domain.enums.AccessMode;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AccessContextDTO {
    private UserInfo user;
    private AccessDetail accountAccess;
    private List<AccessDetail> organizationsAccess;
    private Map<String, Boolean> permissions;

    @Data
    @Builder
    public static class UserInfo {
        private Long id;
        private String name;
    }

    @Data
    @Builder
    public static class AccessDetail {
        private String organizationCode;
        private String organizationName;
        private String subscriptionStatus;
        private AccessMode accessMode;
        private String message;
    }
}
