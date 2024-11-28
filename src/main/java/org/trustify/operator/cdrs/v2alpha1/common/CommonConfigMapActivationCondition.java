package org.trustify.operator.cdrs.v2alpha1.common;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;

import java.util.Optional;

@ApplicationScoped
public class CommonConfigMapActivationCondition implements Condition<ConfigMap, Trustify> {

    @Override
    public boolean isMet(DependentResource<ConfigMap, Trustify> resource, Trustify cr, Context<Trustify> context) {
        var cert = context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_CERTS_DEFAULT_KEY, Optional.class);
        return cert.isPresent();
    }

}
