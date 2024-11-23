package org.trustify.operator.cdrs.v2alpha1.server;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;

import java.util.Optional;

@ApplicationScoped
public class ServerConfigurationConfigMapActivationCondition implements Condition<ConfigMap, Trustify> {

    @Override
    public boolean isMet(DependentResource<ConfigMap, Trustify> resource, Trustify cr, Context<Trustify> context) {
        return Optional.ofNullable(cr.getSpec().oidcSpec())
                .map(TrustifySpec.OidcSpec::enabled)
                .orElse(false);
    }

}
