package com.brainbyte.easy_maintenance.shared.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtService {

  @Value("${easy.jwt.secret}")
  private String secret;

  @Value("${easy.jwt.expirationSeconds:3600}")
  private long expirationSeconds;

  private SecretKey getKey() {
    // Accept raw string or base64; fall back to raw bytes
    try {
      byte[] decoded = Decoders.BASE64.decode(secret);
      return Keys.hmacShaKeyFor(decoded);
    } catch (Exception ex) {
      return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
  }

  public String generate(String subject, Map<String, Object> claims) {
    Instant now = Instant.now();
    Instant exp = now.plusSeconds(expirationSeconds);

    return Jwts.builder()
            .setSubject(subject)
            .addClaims(claims)
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(exp))
            .signWith(getKey(), SignatureAlgorithm.HS256)
            .compact();
  }

  public Jws<Claims> parse(String token) throws JwtException {
    return Jwts.parserBuilder()
            .setSigningKey(getKey())
            .build()
            .parseClaimsJws(token);
  }
}
