package org.trustify.operator.cdrs.v2alpha1.server.utils;

import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.server.utils.ServerUtils;

public class ServerUtils {

<<<<<<<< HEAD:src/main/java/org/trustify/operator/cdrs/v2alpha1/server/utils/ServerUtils.java
    public static boolean isServerDBRequired(Trustify cr) {
        return !Optional.ofNullable(cr.getSpec().databaseSpec())
                .map(TrustifySpec.DatabaseSpec::externalDatabase)
                .orElse(false);
========
    protected boolean isMet(Trustify cr) {
        return ServerUtils.isServerDBRequired(cr);
>>>>>>>> main:src/main/java/org/trustify/operator/cdrs/v2alpha1/db/DBActivationCondition.java
    }
}
