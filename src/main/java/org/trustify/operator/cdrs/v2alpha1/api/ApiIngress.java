package org.trustify.operator.cdrs.v2alpha1.api;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLS;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.utils.CRDUtils;

import java.util.Map;

@KubernetesDependent(labelSelector = ApiIngress.LABEL_SELECTOR, resourceDiscriminator = ApiIngressDiscriminator.class)
@ApplicationScoped
public class ApiIngress extends ApiIngressBase {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=server,component-variant=http";

    @Override
    @SuppressWarnings("unchecked")
    protected Ingress desired(Trustify cr, Context<Trustify> context) {
        return newIngress(
                cr,
                context,
                getIngressName(cr),
                Map.of(
                        "component", "server",
                        "component-variant", "http"
                ),
                Map.of(
                        "console.alpha.openshift.io/overview-app-route", "true"
                )
        );
    }

    @Override
    public boolean isMet(DependentResource<Ingress, Trustify> dependentResource, Trustify cr, Context<Trustify> context) {
        return context.getSecondaryResource(Ingress.class, new ApiIngressDiscriminator())
                .map(in -> {
                    final var status = in.getStatus();
                    if (status != null) {
                        final var ingresses = status.getLoadBalancer().getIngress();
                        // only set the status if the ingress is ready to provide the info we need
                        return ingresses != null && !ingresses.isEmpty();
                    }
                    return false;
                })
                .orElse(false);
    }

    @Override
    protected String getHostname(Trustify cr) {
        return CRDUtils
                .getValueFromSubSpec(cr.getSpec().hostnameSpec(), TrustifySpec.HostnameSpec::hostname)
                .orElseGet(() -> getClusterDomainOnOpenshift()
                        // Openshift
                        .map(domain -> CRDUtils
                                .getValueFromSubSpec(cr.getSpec().hostnameSpec(), TrustifySpec.HostnameSpec::hostname)
                                .orElseGet(() -> getOpenshiftHostname(cr, k8sClient.getConfiguration().getNamespace(), domain))
                        )
                        // Kubernetes vanilla
                        .orElse(null)
                );
    }

    @Override
    protected IngressTLS getIngressTLS(Trustify cr) {
        return null;
    }

    public static String getIngressName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.INGRESS_SUFFIX;
    }

    public static String getOpenshiftHostname(Trustify cr, String namespace, String domain) {
        return namespace + "-" + cr.getMetadata().getName() + "." + domain;
    }
}
