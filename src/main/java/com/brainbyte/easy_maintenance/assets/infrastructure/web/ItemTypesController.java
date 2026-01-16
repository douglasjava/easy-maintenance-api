package com.brainbyte.easy_maintenance.assets.infrastructure.web;

import com.brainbyte.easy_maintenance.assets.application.dto.ItemTypesRequest;
import com.brainbyte.easy_maintenance.assets.application.dto.ItemTypesResponse;
import com.brainbyte.easy_maintenance.assets.application.service.ItemTypesService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/item-types")
@Tag(name = "Ativos", description = "Opções de itens pre-definidas para manutenções")
public class ItemTypesController {

    private final ItemTypesService service;

    @GetMapping
    public List<ItemTypesResponse> listAll(@RequestParam(required = false) String name) {
        return service.listAll(name);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemTypesResponse create(@RequestBody ItemTypesRequest request) {
        return service.create(request);
    }


}
