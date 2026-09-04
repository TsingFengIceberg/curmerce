package cn.iocoder.yudao.curmerce.agent;

/** Carries the authenticated caller and tenant across internal Agent boundaries. */
final class AgentRequestContext {
    private static final String DEFAULT_TENANT = "default";
    private static final ThreadLocal<State> STATE = new ThreadLocal<>();

    private AgentRequestContext() { }

    static void bind(String principal) { bind(principal, tenantId()); }

    static void bind(String principal, String tenantId) {
        STATE.set(new State(normalizePrincipal(principal), normalizeTenant(tenantId)));
    }

    /** Opens a nested context and restores the caller context on close. */
    static Scope open(String principal, String tenantId) {
        State previous = STATE.get();
        bind(principal, tenantId);
        return () -> {
            if (previous == null) STATE.remove();
            else STATE.set(previous);
        };
    }

    static String principal() {
        State value = STATE.get();
        return value == null ? "anonymous" : value.principal();
    }

    static String tenantId() {
        State value = STATE.get();
        return value == null ? DEFAULT_TENANT : value.tenantId();
    }

    /** Returns a non-reversible tenant namespace suitable for Redis keys. */
    static String tenantScope() { return tenantScope(tenantId()); }

    /** Returns the namespace for a persisted/background tenant context. */
    static String tenantScope(String tenant) {
        return AgentPrincipalHasher.hash(normalizeTenant(tenant));
    }

    /** Adds an isolated tenant component to a backend key. */
    static String key(String base) {
        if (base == null || base.isBlank()) throw new IllegalArgumentException("Agent backend key cannot be blank");
        return base + ":tenant:" + tenantScope();
    }

    /** Builds a tenant-scoped key without relying on the current thread. */
    static String key(String base, String tenant) {
        if (base == null || base.isBlank()) throw new IllegalArgumentException("Agent backend key cannot be blank");
        return base + ":tenant:" + tenantScope(tenant);
    }

    /** Elasticsearch index names cannot contain arbitrary user-provided bytes. */
    static String indexName(String configured) {
        String base = configured == null || configured.isBlank() ? "curmerce-agent-knowledge" : configured.trim();
        base = base.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        while (base.startsWith("-") || base.startsWith("_") || base.startsWith("+")) base = base.substring(1);
        if (base.isBlank()) base = "curmerce-agent-knowledge";
        return base + "-t-" + tenantScope();
    }

    static String normalizeTenant(String value) {
        String normalized = value == null || value.isBlank() ? DEFAULT_TENANT : value.trim();
        if (!normalized.matches("[A-Za-z0-9._:-]{1,64}")) throw new IllegalArgumentException("租户编号格式无效");
        return normalized;
    }

    private static String normalizePrincipal(String value) {
        return value == null || value.isBlank() ? "anonymous" : value;
    }

    static void clear() { STATE.remove(); }

    @FunctionalInterface
    interface Scope extends AutoCloseable {
        @Override void close();
    }

    private record State(String principal, String tenantId) { }
}
