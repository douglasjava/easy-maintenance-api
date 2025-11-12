package com.brainbyte.easy_maintenance.kernel.tenant;

import java.util.Optional;

public final class TenantContext {

  private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

  private TenantContext() {
  }

  public static void set(String tenantId) {
    CURRENT.set(tenantId);
  }

  public static Optional<String> get() {
    return Optional.ofNullable(CURRENT.get());
  }

  public static void clear() {
    CURRENT.remove();
  }

}
