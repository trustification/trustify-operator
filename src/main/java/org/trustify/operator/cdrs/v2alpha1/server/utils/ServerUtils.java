package org.trustify.operator.cdrs.v2alpha1.server.utils;

import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;

import java.util.Optional;

public class ServerUtils {

    public static boolean isServerDBRequired(Trustify cr) {
        return !Optional.ofNullable(cr.getSpec().databaseSpec())
                .map(TrustifySpec.DatabaseSpec::externalDatabase)
                .orElse(false);
    }
}
