package org.trustify.operator.cdrs.v2alpha1;

public record TrustifyStatusCondition(String type, Boolean status) {
    public static final String AVAILABLE = "Available";
    public static final String PROCESSING = "Processing";
    public static final String DEGRADED = "Degraded";
}
