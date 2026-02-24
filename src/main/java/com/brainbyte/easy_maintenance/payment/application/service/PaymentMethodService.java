package com.brainbyte.easy_maintenance.payment.application.service;

import com.brainbyte.easy_maintenance.commons.exceptions.ConflictException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import com.brainbyte.easy_maintenance.payment.application.dto.PaymentMethodDTO;
import com.brainbyte.easy_maintenance.payment.domain.PaymentMethod;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentMethodRepository;
import com.brainbyte.easy_maintenance.payment.mapper.PaymentMethodMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentMethodService {

    private final PaymentMethodRepository repository;
    private final UserRepository userRepository;
    private final PaymentMethodMapper mapper;

    @Transactional(readOnly = true)
    public List<PaymentMethodDTO.PaymentMethodResponse> listUserPaymentMethods(Long userId) {
        return repository.findAllByUserId(userId).stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public PaymentMethodDTO.PaymentMethodResponse createPaymentMethod(Long userId, PaymentMethodDTO.CreatePaymentMethodRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado: " + userId));

        if (request.isDefault()) {
            repository.findByUserIdAndIsDefaultTrue(userId)
                    .ifPresent(pm -> {
                        pm.setIsDefault(false);
                        repository.save(pm);
                    });
        }

        PaymentMethod entity = mapper.toEntity(request);
        entity.setUser(user);

        return mapper.toResponse(repository.save(entity));
    }

    @Transactional
    public void deletePaymentMethod(Long userId, Long methodId) {
        PaymentMethod method = repository.findById(methodId)
                .orElseThrow(() -> new NotFoundException("Método de pagamento não encontrado: " + methodId));

        if (!method.getUser().getId().equals(userId)) {
            throw new ConflictException("Método de pagamento não pertence ao usuário");
        }

        repository.delete(method);

        if (method.getIsDefault()) {
            repository.findAllByUserId(userId).stream()
                    .findFirst()
                    .ifPresent(pm -> {
                        pm.setIsDefault(true);
                        repository.save(pm);
                    });
        }
    }

    @Transactional
    public void setDefault(Long userId, Long methodId) {
        PaymentMethod method = repository.findById(methodId)
                .orElseThrow(() -> new NotFoundException("Método de pagamento não encontrado: " + methodId));

        if (!method.getUser().getId().equals(userId)) {
            throw new ConflictException("Método de pagamento não pertence ao usuário");
        }

        repository.findByUserIdAndIsDefaultTrue(userId)
                .ifPresent(pm -> {
                    pm.setIsDefault(false);
                    repository.save(pm);
                });

        method.setIsDefault(true);
        repository.save(method);
    }

}
