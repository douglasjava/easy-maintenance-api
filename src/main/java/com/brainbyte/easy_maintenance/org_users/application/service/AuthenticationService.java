package com.brainbyte.easy_maintenance.org_users.application.service;

import com.brainbyte.easy_maintenance.commons.exceptions.NotAuthorizedException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;

    public User getCurrentUser() {

        try {

            String email = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            return userRepository.findByEmail(email)
                    .orElseThrow(() -> new NotFoundException("Usuário logado não encontrado no banco de dados"));

        } catch (Exception e){
            throw new NotAuthorizedException("Usuário não autenticado");
        }

    }


}
