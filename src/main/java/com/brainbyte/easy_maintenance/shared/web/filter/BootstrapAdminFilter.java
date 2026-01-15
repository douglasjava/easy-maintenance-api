package com.brainbyte.easy_maintenance.shared.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Order(0)
@Component
public class BootstrapAdminFilter extends OncePerRequestFilter {

    @Value("${bootstrap.admin.token}")
    private String adminToken;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/private/admin");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        String token = req.getHeader("X-Admin-Token");
        if (token == null || !token.equals(adminToken)) {
            res.setStatus(401);
            return;
        }
        chain.doFilter(req, res);
    }

}

