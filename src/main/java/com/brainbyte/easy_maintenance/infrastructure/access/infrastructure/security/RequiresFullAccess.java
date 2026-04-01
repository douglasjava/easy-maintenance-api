package com.brainbyte.easy_maintenance.infrastructure.access.infrastructure.security;

import com.brainbyte.easy_maintenance.infrastructure.access.domain.enums.AccessScope;
import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresFullAccess {
    AccessScope scope() default AccessScope.ORGANIZATION;
}
