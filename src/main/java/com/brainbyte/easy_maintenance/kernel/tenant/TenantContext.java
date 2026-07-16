package com.brainbyte.easy_maintenance.kernel.tenant;

import java.util.Optional;
import java.util.function.Supplier;

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

  /**
   * Clears only the system-context flag, leaving the tenant org code intact.
   * Use in a finally block after setSystemContext() to restore per-request scoping.
   */
  public static void clearSystemContext() {
    SYSTEM_CONTEXT.remove();
  }

  public static void clear() {
    CURRENT.remove();
    SYSTEM_CONTEXT.remove();
  }

  /**
   * Runs a legitimately cross-organization read (e.g. summing a value across every organization
   * of an already-authorized account) with the Hibernate tenant filter bypassed, then restores the
   * previous system-context state. The caller is responsible for ensuring any org codes involved
   * were already resolved from data the current user/account is authorized to see — this does not
   * perform any authorization check itself, it only lifts the single-tenant SQL filter.
   *
   * <p>Established pattern for the class of bug where {@code TenantFilterAspect} silently ANDs the
   * active request's org code onto every {@code MaintenanceItemRepository} query, making
   * cross-org aggregates (pool totals, per-org breakdowns for a multi-org account) collapse to only
   * the currently active organization.
   */
  public static <T> T runCrossOrg(Supplier<T> action) {
    boolean alreadySystem = isSystemContext();
    if (!alreadySystem) setSystemContext();
    try {
      return action.get();
    } finally {
      if (!alreadySystem) clearSystemContext();
    }
  }

}
