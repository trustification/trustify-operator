package org.trustify.operator.cdrs.v2alpha1.keycloak;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.keycloak.utils.KeycloakUtils;

@ApplicationScoped
public class KeycloakDBServiceActivationCondition implements Condition<Service, Trustify> {

    @Override
    public boolean isMet(DependentResource<Service, Trustify> resource, Trustify cr, Context<Trustify> context) {
        return KeycloakUtils.isKeycloakRequired(cr);
    }

}
