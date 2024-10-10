package org.trustify.operator.cdrs.v2alpha1.keycloak;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.cdrs.v2alpha1.Trustify;

@ApplicationScoped
public class KeycloakDBPersistentVolumeClaimActivationCondition extends KeycloakDBActivationCondition implements Condition<PersistentVolumeClaim, Trustify> {

    @Override
    public boolean isMet(DependentResource<PersistentVolumeClaim, Trustify> resource, Trustify cr, Context<Trustify> context) {
        return super.isMet(cr);
    }

}
