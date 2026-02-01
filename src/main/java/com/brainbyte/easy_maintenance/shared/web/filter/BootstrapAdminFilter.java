package com.brainbyte.easy_maintenance.shared.web.filter;

import com.brainbyte.easy_maintenance.shared.web.ProblemDetails;
import com.brainbyte.easy_maintenance.shared.web.ProblemType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
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
        return !request.getRequestURI().startsWith("/easy-maintenance/api/v1/private/admin");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        String token = req.getHeader("X-Admin-Token");

        if (token == null || !token.equals(adminToken)) {

            var problem = ProblemDetails.of(
                    HttpStatus.UNAUTHORIZED,
                    ProblemType.WITHOUT_PERMISSIONS,
                    "Usuário sem permissão de administrador",
                    req
            );

            res.setStatus(HttpStatus.UNAUTHORIZED.value());
            res.setContentType("application/problem+json");
            res.setCharacterEncoding("UTF-8");

            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(res.getWriter(), problem);

            return; // IMPORTANTE: não chama o chain
        }

        chain.doFilter(req, res);
    }

}

