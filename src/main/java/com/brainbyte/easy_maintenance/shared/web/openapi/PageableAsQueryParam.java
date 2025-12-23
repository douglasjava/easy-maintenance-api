package com.brainbyte.easy_maintenance.shared.web.openapi;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotação para deixar explícitos os parâmetros de paginação no Swagger (Springdoc).
 * Use em métodos que recebem org.springframework.data.domain.Pageable.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Parameter(
        in = ParameterIn.QUERY,
        name = "page",
        description = "Número da página (0-based)",
        schema = @Schema(type = "integer", defaultValue = "0", minimum = "0")
)
@Parameter(
        in = ParameterIn.QUERY,
        name = "size",
        description = "Quantidade de itens por página",
        schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "200")
)
@Parameter(
        in = ParameterIn.QUERY,
        name = "sort",
        description = "Critério de ordenação. Formato: campo,(asc|desc). Pode repetir o parâmetro para múltiplas ordenações. Ex.: sort=name,asc&sort=createdAt,desc",
        array = @ArraySchema(schema = @Schema(type = "string"))
)
public @interface PageableAsQueryParam {
}
