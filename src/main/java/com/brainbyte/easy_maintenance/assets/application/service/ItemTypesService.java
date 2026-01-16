package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.ItemTypesRequest;
import com.brainbyte.easy_maintenance.assets.application.dto.ItemTypesResponse;
import com.brainbyte.easy_maintenance.assets.domain.ItemTypes;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.ItemTypesRepository;
import com.brainbyte.easy_maintenance.assets.mapper.IItemTypesMapper;
import com.brainbyte.easy_maintenance.commons.helper.NormalizerUtil;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemTypesService {

    private final ItemTypesRepository repository;

    public List<ItemTypesResponse> listAll(String name) {
        log.info("Buscando itens parametro {}", name);

        List<ItemTypes> items;

        if (StringUtils.hasText(name)) {
            String normalized = normalize(name);
            items = repository.findByNormalizedNameContaining(normalized);
        } else {
            items = repository.findAllActive();
        }

        return items.stream()
                .map(IItemTypesMapper.INSTANCE::toResponse)
                .toList();
    }

    public ItemTypesResponse create(ItemTypesRequest request) {
        log.info("Criando novo item {}", request);

        String normalized = normalize(request.name());

        return repository.findByNormalizedName(normalized)
                .map(IItemTypesMapper.INSTANCE::toResponse)
                .orElseGet(() -> {
                    ItemTypes item = new ItemTypes();
                    item.setNormalizedName(normalized);
                    item.setName(request.name());
                    item.setStatus(Status.ACTIVE);
                    item.setCreatedAt(Instant.now());
                    return IItemTypesMapper.INSTANCE.toResponse(repository.save(item));
                });
    }


    private String normalize(String value) {
        return NormalizerUtil.normalize(value);
    }

}
