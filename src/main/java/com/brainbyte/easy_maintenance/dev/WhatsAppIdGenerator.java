package com.brainbyte.easy_maintenance.dev;

import java.security.SecureRandom;
import java.util.Base64;

public final class WhatsAppIdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private WhatsAppIdGenerator() {
    }

    public static String generateWamid() {
        byte[] bytes = new byte[40]; // tamanho semelhante aos retornados pela API
        RANDOM.nextBytes(bytes);

        return "wamid." + Base64.getEncoder().encodeToString(bytes);
    }

}
