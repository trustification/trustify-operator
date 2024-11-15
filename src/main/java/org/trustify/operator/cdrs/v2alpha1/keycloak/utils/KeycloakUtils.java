package org.trustify.operator.cdrs.v2alpha1.keycloak.utils;

import org.trustify.operator.cdrs.v2alpha1.Trustify;

import java.util.Optional;

public class KeycloakUtils {

    public static boolean isKeycloakRequired(Trustify cr) {
        return Optional.ofNullable(cr.getSpec().oidcSpec())
                .map(oidcSpec -> oidcSpec.enabled() && (oidcSpec.serverUrl() == null || oidcSpec.serverUrl().isBlank()))
                .orElse(false);
    }

}
