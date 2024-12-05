package org.trustify.operator.cdrs.v2alpha1.ingress;

import io.fabric8.kubernetes.api.model.networking.v1.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.cdrs.v2alpha1.ui.service.UIService;
import org.trustify.operator.services.ClusterService;
import org.trustify.operator.utils.CRDUtils;

import java.util.Collections;
import java.util.List;

@KubernetesDependent(labelSelector = AppIngress.LABEL_SELECTOR, resourceDiscriminator = AppIngressDiscriminator.class)
@ApplicationScoped
public class AppIngress extends CRUDKubernetesDependentResource<Ingress, Trustify>
        implements Condition<Ingress, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=ui,component-variant=https";

    public AppIngress() {
        super(Ingress.class);
    }

    @Override
    protected Ingress desired(Trustify cr, Context<Trustify> context) {
        return newIngress(cr, context);
    }

    protected Ingress newIngress(Trustify cr, Context<Trustify> context) {
        var port = UIService.getServicePort(cr);
        IngressTLS ingressTLS = getIngressTLS(cr);

        String hostname = CRDUtils.getValueFromSubSpec(cr.getSpec().hostnameSpec(), TrustifySpec.HostnameSpec::hostname)
                .or(() -> {
                    final var clusterService = context.managedDependentResourceContext().getMandatory(Constants.CLUSTER_SERVICE, ClusterService.class);
                    return clusterService.getCluster().getClusterHostname();
                })
                .orElse(null);

        return new IngressBuilder()
                .withMetadata(Constants.metadataBuilder
                        .apply(new Constants.Resource(getIngressName(cr), LABEL_SELECTOR, cr))
                        .build()
                )
                .withSpec(new IngressSpecBuilder()
                        .withTls(ingressTLS != null ? Collections.singletonList(ingressTLS) : Collections.emptyList())
                        .withRules(List.of(new IngressRuleBuilder()
                                .withHost(hostname)
                                .withHttp(new HTTPIngressRuleValueBuilder()
                                        .withPaths(List.of(new HTTPIngressPathBuilder()
                                                .withPath("/")
                                                .withPathType("Prefix")
                                                .withBackend(new IngressBackendBuilder()
                                                        .withService(new IngressServiceBackendBuilder()
                                                                .withName(UIService.getServiceName(cr))
                                                                .withPort(new ServiceBackendPortBuilder()
                                                                        .withNumber(port)
                                                                        .build()
                                                                )
                                                                .build()
                                                        )
                                                        .build()
                                                )
                                                .build()
                                        ))
                                        .build()
                                )
                                .build()
                        ))
                        .build()
                )
                .build();
    }

    @Override
    public boolean isMet(DependentResource<Ingress, Trustify> dependentResource, Trustify primary, Context<Trustify> context) {
        return context.getSecondaryResource(Ingress.class, new AppIngressDiscriminator())
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

    protected IngressTLS getIngressTLS(Trustify cr) {
        String tlsSecretName = CRDUtils.getValueFromSubSpec(cr.getSpec().httpSpec(), TrustifySpec.HttpSpec::tlsSecret)
                .orElse(null);

        return new IngressTLSBuilder()
                .withSecretName(tlsSecretName)
                .build();
    }

    public static String getIngressName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.INGRESS_SUFFIX;
    }

}
