package org.trustify.operator.cdrs.v2alpha1.ingress;

import io.fabric8.kubernetes.api.model.networking.v1.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.cdrs.v2alpha1.ui.service.UIService;
import org.trustify.operator.services.ClusterService;

import java.util.*;

@KubernetesDependent(labelSelector = AppIngress.LABEL_SELECTOR, resourceDiscriminator = AppIngressDiscriminator.class)
@ApplicationScoped
public class AppIngress extends CRUDKubernetesDependentResource<Ingress, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=ui,component-variant=https";

    public AppIngress() {
        super(Ingress.class);
    }

    @Override
    protected Ingress desired(Trustify cr, Context<Trustify> context) {
        return newIngress(cr, context);
    }

    @Override
    public Result<Ingress> match(Ingress ingress, Trustify cr, Context<Trustify> context) {
        Optional<String> actualHostname = Optional.ofNullable(ingress.getSpec())
                .flatMap(ingressSpec -> ingressSpec
                        .getRules()
                        .stream().findFirst()
                        .map(IngressRule::getHost)
                );
        boolean hostnameMatch = Objects.equals(getHostname(cr, context), actualHostname.orElse(null));
        if (!hostnameMatch) {
            return Result.nonComputed(false);
        }

        List<IngressTLS> ingressTLS = Optional.ofNullable(ingress.getSpec())
                .map(IngressSpec::getTls)
                .orElse(Collections.emptyList());
        boolean tlsMatch = Objects.equals(new HashSet<>(getIngressTLS(cr)), new HashSet<>(ingressTLS));
        if (!tlsMatch) {
            return Result.nonComputed(false);
        }

        return Result.nonComputed(true);
    }

    private String getHostname(Trustify cr, Context<Trustify> context) {
        return Optional.ofNullable(cr.getSpec().hostnameSpec())
                .flatMap(hostnameSpec -> Optional.ofNullable(hostnameSpec.hostname()))
                .or(() -> {
                    final var clusterService = context.managedDependentResourceContext().getMandatory(Constants.CLUSTER_SERVICE, ClusterService.class);
                    return clusterService.getCluster().getAutoGeneratedIngressHost(cr);
                })
                .orElse(null);
    }

    protected Ingress newIngress(Trustify cr, Context<Trustify> context) {
        var port = UIService.getServicePort(cr);

        return new IngressBuilder()
                .withMetadata(Constants.metadataBuilder
                        .apply(new Constants.Resource(getIngressName(cr), LABEL_SELECTOR, cr))
                        .build()
                )
                .withSpec(new IngressSpecBuilder()
                        .withTls(getIngressTLS(cr))
                        .withRules(List.of(new IngressRuleBuilder()
                                .withHost(getHostname(cr, context))
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

    protected List<IngressTLS> getIngressTLS(Trustify cr) {
        String tlsSecretName = Optional.ofNullable(cr.getSpec().httpSpec())
                .map(TrustifySpec.HttpSpec::tlsSecret)
                .orElse(null);

        IngressTLS ingressTLS = new IngressTLSBuilder()
                .withSecretName(tlsSecretName)
                .build();
        return Collections.singletonList(ingressTLS);
    }

    public static String getIngressName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.INGRESS_SUFFIX;
    }

}
