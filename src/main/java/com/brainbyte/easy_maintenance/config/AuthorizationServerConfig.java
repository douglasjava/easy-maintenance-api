package com.brainbyte.easy_maintenance.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;

import javax.sql.DataSource;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Configuration
public class AuthorizationServerConfig {

    @Value("${jwt.rsa.private-key:}")
    private String rsaPrivateKeyBase64;

    @Value("${jwt.rsa.public-key:}")
    private String rsaPublicKeyBase64;

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();
        // Enable OpenID Connect 1.0
        authorizationServerConfigurer.oidc(Customizer.withDefaults());

        // Match only the Authorization Server endpoints
        var endpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();
        http.securityMatcher(endpointsMatcher);

        http.with(authorizationServerConfigurer, Customizer.withDefaults());
        http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        http.formLogin(Customizer.withDefaults());

        return http.build();

    }


    // JDBC repositories/services
    @Bean
    public RegisteredClientRepository registeredClientRepository(DataSource dataSource) {
        return new JdbcRegisteredClientRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    public OAuth2AuthorizationService authorizationService(DataSource dataSource,
                                                           RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationService(new JdbcTemplate(dataSource), registeredClientRepository);
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(DataSource dataSource,
                                                                        RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(new JdbcTemplate(dataSource), registeredClientRepository);
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = loadOrGenerateRsaKey();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        String keyId = deriveKeyId(publicKey);
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(keyId)
                .build();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return (jwkSelector, securityContext) -> jwkSelector.select(jwkSet);
    }

    /**
     * Loads RSA key pair from environment variables (JWT_RSA_PRIVATE_KEY / JWT_RSA_PUBLIC_KEY).
     * Falls back to generating an ephemeral key in non-production environments with a loud warning.
     * Run RsaKeyGenerator once to generate and print the values to configure as env vars.
     */
    private KeyPair loadOrGenerateRsaKey() {
        boolean hasPrivate = rsaPrivateKeyBase64 != null && !rsaPrivateKeyBase64.isBlank();
        boolean hasPublic = rsaPublicKeyBase64 != null && !rsaPublicKeyBase64.isBlank();

        if (hasPrivate && hasPublic) {
            try {
                byte[] privateKeyBytes = Base64.getDecoder().decode(rsaPrivateKeyBase64.trim());
                byte[] publicKeyBytes = Base64.getDecoder().decode(rsaPublicKeyBase64.trim());

                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
                RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));

                log.info("JWT RSA key pair loaded from environment variables (kid: {})", deriveKeyId(publicKey));
                return new KeyPair(publicKey, privateKey);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to load RSA key pair from environment variables. " +
                        "Ensure JWT_RSA_PRIVATE_KEY and JWT_RSA_PUBLIC_KEY contain valid Base64-encoded DER keys. " +
                        "Run com.brainbyte.easy_maintenance.shared.security.RsaKeyGenerator to generate new keys.", e);
            }
        }

        log.warn("=================================================================");
        log.warn("JWT_RSA_PRIVATE_KEY / JWT_RSA_PUBLIC_KEY not configured.");
        log.warn("Generating EPHEMERAL RSA key pair — tokens will be INVALIDATED on restart.");
        log.warn("Run RsaKeyGenerator and set env vars before deploying to production.");
        log.warn("=================================================================");
        return generateRsaKey();
    }

    /**
     * Derives a stable, deterministic key ID from the public key bytes (first 8 bytes of SHA-256).
     * This ensures the kid header in JWTs remains the same across restarts for the same key.
     */
    private static String deriveKeyId(RSAPublicKey publicKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(publicKey.getEncoded());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().build();
    }

    // Initialize a public client (no secret) with Authorization Code + PKCE, OIDC and Refresh Token
    @Bean
    public ApplicationRunner oauthClientInitializer(RegisteredClientRepository repository) {
        return args -> {
            // You may externalize these values to application properties if needed
            String clientId = "easy-maintenance-web";
            RegisteredClient existing = null;
            if (repository instanceof JdbcRegisteredClientRepository jdbcRepo) {
                existing = jdbcRepo.findByClientId(clientId);
            }

            if (existing == null) {
                TokenSettings tokenSettings = TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(30))
                        .refreshTokenTimeToLive(Duration.ofDays(30))
                        .reuseRefreshTokens(false)
                        .build();

                ClientSettings clientSettings = ClientSettings.builder()
                        .requireProofKey(true) // PKCE required
                        .requireAuthorizationConsent(true)
                        .build();

                RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                        .clientId(clientId)
                        // Public client: no secret and `none` authentication method
                        .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                        .redirectUri("http://localhost:3000/callback")
                        .postLogoutRedirectUri("http://localhost:3000")
                        .scope(OidcScopes.OPENID)
                        .scope(OidcScopes.PROFILE)
                        .scope("offline_access")
                        .tokenSettings(tokenSettings)
                        .clientSettings(clientSettings)
                        .build();

                repository.save(client);
            }
        };
    }

    private static KeyPair generateRsaKey() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
