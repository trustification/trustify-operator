package org.trustify.operator.cdrs.v2alpha1.keycloak.models;

public class KeycloakStatusCondition extends StatusCondition {
    public static final String READY = "Ready";
    public static final String HAS_ERRORS = "HasErrors";
    public static final String ROLLING_UPDATE = "RollingUpdate";
}
