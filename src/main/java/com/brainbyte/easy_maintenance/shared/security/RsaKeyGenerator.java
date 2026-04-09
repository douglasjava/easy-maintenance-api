package com.brainbyte.easy_maintenance.shared.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

/**
 * Standalone utility to generate a persistent RSA-2048 key pair for JWT signing.
 *
 * <p>Run once per environment:
 * <pre>
 *   mvn exec:java -Dexec.mainClass="com.brainbyte.easy_maintenance.shared.security.RsaKeyGenerator"
 * </pre>
 *
 * <p>Then set the printed values as environment variables:
 * <pre>
 *   JWT_RSA_PRIVATE_KEY=&lt;base64 value&gt;
 *   JWT_RSA_PUBLIC_KEY=&lt;base64 value&gt;
 * </pre>
 *
 * <p><b>Security notes:</b>
 * <ul>
 *   <li>Keep JWT_RSA_PRIVATE_KEY secret — store in Railway Variables, AWS Secrets Manager, or equivalent.</li>
 *   <li>Never commit key values to version control.</li>
 *   <li>Rotating the key pair invalidates all existing tokens (users are logged out).</li>
 *   <li>Generate separate key pairs per environment (dev, staging, production).</li>
 * </ul>
 */
public class RsaKeyGenerator {

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        String privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String keyId = deriveKeyId((RSAPublicKey) keyPair.getPublic());

        System.out.println();
        System.out.println("=================================================================");
        System.out.println("  JWT RSA KEY PAIR GENERATED");
        System.out.println("  Key ID (kid): " + keyId);
        System.out.println("=================================================================");
        System.out.println();
        System.out.println("Set these as environment variables (Railway Variables / .env):");
        System.out.println();
        System.out.println("JWT_RSA_PRIVATE_KEY=" + privateKeyBase64);
        System.out.println();
        System.out.println("JWT_RSA_PUBLIC_KEY=" + publicKeyBase64);
        System.out.println();
        System.out.println("=================================================================");
        System.out.println("  KEEP THESE VALUES SECRET. Never commit to version control.");
        System.out.println("  Rotating keys will invalidate all active user sessions.");
        System.out.println("  Generate separate keys per environment.");
        System.out.println("=================================================================");
        System.out.println();
    }

    private static String deriveKeyId(RSAPublicKey publicKey) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(publicKey.getEncoded());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(String.format("%02x", hash[i]));
        }
        return sb.toString();
    }
}
