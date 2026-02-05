package com.brainbyte.easy_maintenance.commons.validation;

import com.brainbyte.easy_maintenance.commons.validation.groups.CnpjGroup;
import com.brainbyte.easy_maintenance.commons.validation.groups.CpfGroup;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import org.hibernate.validator.constraints.br.CNPJ;
import org.hibernate.validator.constraints.br.CPF;
import org.hibernate.validator.constraints.CompositionType;
import org.hibernate.validator.constraints.ConstraintComposition;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Validação de documento brasileiro (CPF ou CNPJ) com suporte a grupos.
 *
 * Como usar:
 *  - Anote o campo com {@link Doc}.
 *  - Ative o grupo desejado ao validar (ex.: {@code @Validated(CpfGroup.class)} ou {@code @Validated(CnpjGroup.class)}).
 */
@Target({ FIELD, METHOD, PARAMETER, ANNOTATION_TYPE })
@Retention(RUNTIME)
@Documented
@Constraint(validatedBy = {})
@ConstraintComposition(CompositionType.OR)
@ReportAsSingleViolation
@CPF(groups = CpfGroup.class)
@CNPJ(groups = CnpjGroup.class)
public @interface Doc {

    String message() default "Documento inválido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
