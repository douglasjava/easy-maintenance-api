package com.brainbyte.easy_maintenance.push.mapper;

import com.brainbyte.easy_maintenance.push.application.dto.PushTokenResponse;
import com.brainbyte.easy_maintenance.push.domain.UserPushToken;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

@Mapper(componentModel = "spring")
public interface IPushTokenMapper {

    IPushTokenMapper INSTANCE = Mappers.getMapper(IPushTokenMapper.class);

    default UserPushToken updateExistingToken(UserPushToken existing, Instant now, String platform, String endpoint, String deviceInfo) {
        existing.setActive(true);
        existing.setLastSeenAt(now);
        existing.setDeviceInfo(deviceInfo);
        existing.setEndpoint(endpoint);
        existing.setPlatform(platform);
        existing.setUser(null);
        return existing;
    }

    default UserPushToken createNewToken(String token, Instant now, String platform, String endpoint, String deviceInfo) {
        return UserPushToken.builder()
                .token(token)
                .platform(platform)
                .endpoint(endpoint)
                .deviceInfo(deviceInfo)
                .active(true)
                .lastSeenAt(now)
                .build();
    }

    default PushTokenResponse toResponse(UserPushToken token) {
        return new PushTokenResponse(
                token.getId(),
                token.getToken(),
                token.getPlatform(),
                token.isActive()
        );
    }

}
