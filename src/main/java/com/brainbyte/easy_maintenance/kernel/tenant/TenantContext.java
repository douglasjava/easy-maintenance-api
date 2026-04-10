package com.brainbyte.easy_maintenance.kernel.tenant;

import java.util.Optional;

public final class TenantContext {

  private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
  private static final ThreadLocal<Boolean> SYSTEM_CONTEXT = new ThreadLocal<>();

  private TenantContext() {
  }

  public static void set(String tenantId) {
    CURRENT.set(tenantId);
  }

  public static Optional<String> get() {
    return Optional.ofNullable(CURRENT.get());
  }

  /**
   * Marks the current thread as a system/background operation.
   * System context bypasses tenant filter enforcement, allowing cross-tenant
   * queries from scheduled jobs or internal system processes.
   */
  public static void setSystemContext() {
    SYSTEM_CONTEXT.set(Boolean.TRUE);
  }

  /**
   * Returns true if the current thread is running in system context
   * (background job or internal process that legitimately queries across tenants).
   */
  public static boolean isSystemContext() {
    return Boolean.TRUE.equals(SYSTEM_CONTEXT.get());
  }

  public static void clear() {
    CURRENT.remove();
    SYSTEM_CONTEXT.remove();
  }

}
