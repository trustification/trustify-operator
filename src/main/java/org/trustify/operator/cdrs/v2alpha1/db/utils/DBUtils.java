package org.trustify.operator.cdrs.v2alpha1.db.utils;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.db.DBDeploymentDiscriminator;

public class DBUtils {

    public static boolean isDeploymentReady(DependentResource<Deployment, Trustify> dependentResource, Trustify primary, Context<Trustify> context) {
        return context.getSecondaryResource(Deployment.class, new DBDeploymentDiscriminator())
                .map(deployment -> {
                    final var status = deployment.getStatus();
                    if (status != null) {
                        final var readyReplicas = status.getReadyReplicas();
                        return readyReplicas != null && readyReplicas >= 1;
                    }
                    return false;
                })
                .orElse(false);
    }

}
