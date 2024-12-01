package org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.realmimport;

import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.deployment.StatusCondition;

public class KeycloakRealmImportStatusCondition extends StatusCondition {
    public static final String DONE = "Done";
    public static final String STARTED = "Started";
    public static final String HAS_ERRORS = "HasErrors";
}
