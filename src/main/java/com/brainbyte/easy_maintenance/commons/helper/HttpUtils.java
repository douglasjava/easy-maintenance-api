package com.brainbyte.easy_maintenance.commons.helper;

import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;

@UtilityClass
public class HttpUtils {

    public static String getClientIp(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For",
                "X-Real-IP",
                "CF-Connecting-IP", // Cloudflare
                "True-Client-IP"
        };

        for (String header : headers) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                // X-Forwarded-For pode vir como "ip1, ip2"
                return value.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }

    public static  String getUserAgent(HttpServletRequest request) {
        return request.getHeader("X-User-Agent");
    }

}
