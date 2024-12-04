package org.trustify.operator.cdrs.v2alpha1.db;

import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.server.utils.ServerUtils;

public abstract class DBActivationCondition {

    protected boolean isMet(Trustify cr) {
        return ServerUtils.isServerDBRequired(cr);
    }

}
