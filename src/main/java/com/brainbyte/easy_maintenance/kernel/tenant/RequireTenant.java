package com.brainbyte.easy_maintenance.kernel.tenant;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireTenant {
}
