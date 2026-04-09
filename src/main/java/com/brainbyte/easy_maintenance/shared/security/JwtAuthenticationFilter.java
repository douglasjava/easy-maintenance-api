package com.brainbyte.easy_maintenance.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.http.Cookie;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
          throws ServletException, IOException {

    // 1) Authorization header (API clients, admin area)
    String token = null;
    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
    log.debug("authHeader: {}", authHeader);
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      token = authHeader.substring(7);
    }

    // 2) HttpOnly cookie (browser clients)
    if (token == null && request.getCookies() != null) {
      token = Arrays.stream(request.getCookies())
              .filter(c -> "accessToken".equals(c.getName()))
              .map(Cookie::getValue)
              .findFirst()
              .orElse(null);
    }

    if (token == null) {
      filterChain.doFilter(request, response);
      return;
    }
    try {
      Jws<Claims> jws = jwtService.parse(token);
      Claims claims = jws.getBody();
      String subject = claims.getSubject(); // we used email as subject
      String role = Optional.ofNullable(claims.get("role"))
              .map(Object::toString)
              .orElse(null);

      List<SimpleGrantedAuthority> authorities = role != null
              ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
              : Collections.emptyList();

      @SuppressWarnings("unchecked")
      List<String> orgCodes = Optional.ofNullable(claims.get("orgs"))
              .filter(v -> v instanceof List)
              .map(v -> (List<String>) v)
              .orElse(Collections.emptyList());

      log.debug("JWT filter - subject: {}, role: {}, orgs: {}, authorities: {}",
                subject, role, orgCodes, authorities);

      UsernamePasswordAuthenticationToken authentication =
              new UsernamePasswordAuthenticationToken(subject, null, authorities);
      authentication.setDetails(orgCodes);

      SecurityContextHolder.getContext().setAuthentication(authentication);
    } catch (Exception ex) {
      SecurityContextHolder.clearContext();
      log.debug("Invalid JWT token: {}", ex.getMessage());
    }

    filterChain.doFilter(request, response);
  }
}
