package org.trustify.operator.cdrs.v2alpha1.keycloak;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.keycloak.utils.KeycloakUtils;

import java.util.Optional;

@ApplicationScoped
public class KeycloakDBSecretActivationCondition implements Condition<Secret, Trustify> {

    @Override
    public boolean isMet(DependentResource<Secret, Trustify> resource, Trustify cr, Context<Trustify> context) {
        boolean databaseRequired = KeycloakUtils.isKeycloakRequired(cr);

        boolean manualSecretIsNotSet = Optional.ofNullable(cr.getSpec().oidcSpec())
                .flatMap(oidcSpec -> Optional.ofNullable(oidcSpec.databaseSpec()))
                .map(databaseSpec -> databaseSpec.usernameSecret() == null || databaseSpec.passwordSecret() == null)
                .orElse(true);

        return databaseRequired && manualSecretIsNotSet;
    }

}
