package com.brainbyte.easy_maintenance.shared.ratelimit;

import com.brainbyte.easy_maintenance.commons.helper.HttpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties properties;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String name = rateLimit.value();
        RateLimitProperties.LimitConfig config = properties.getLimits().get(name);

        if (config == null) {
            log.warn("[RateLimit] Nenhuma configuração encontrada para o bucket '{}'. Requisição permitida sem limite.", name);
            return pjp.proceed();
        }

        String bucketKey = resolveKey(name, config.getKey());
        rateLimiterService.tryConsume(bucketKey, config);

        return pjp.proceed();
    }

    private String resolveKey(String bucketName, RateLimitKey keyType) {
        return switch (keyType) {
            case IP -> {
                ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
                HttpServletRequest request = attrs.getRequest();
                yield bucketName + ":ip:" + HttpUtils.getClientIp(request);
            }
            case USER_ID -> {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                String identifier = (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
                yield bucketName + ":user:" + identifier;
            }
        };
    }
}
