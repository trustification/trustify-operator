package org.trustify.operator.cdrs.v2alpha1.db;

import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;

import java.util.Optional;

public abstract class DBActivationCondition {

    protected boolean isMet(Trustify cr) {
        return !Optional.ofNullable(cr.getSpec().databaseSpec())
                .map(TrustifySpec.DatabaseSpec::externalDatabase)
                .orElse(false);
    }

}
