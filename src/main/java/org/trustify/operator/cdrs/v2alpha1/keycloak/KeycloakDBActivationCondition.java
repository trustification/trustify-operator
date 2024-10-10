package org.trustify.operator.cdrs.v2alpha1.keycloak;

import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;

import java.util.Optional;

public abstract class KeycloakDBActivationCondition {

    protected boolean isMet(Trustify cr) {
        return !Optional.ofNullable(cr.getSpec().oidcSpec())
                .flatMap(oidcSpec -> Optional.ofNullable(oidcSpec.databaseSpec()))
                .map(TrustifySpec.DatabaseSpec::externalDatabase)
                .orElse(false);
    }

}
