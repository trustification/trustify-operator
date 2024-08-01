package org.trustify.operator.cdrs.v2alpha1.server;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLS;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.utils.CRDUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class ServerIngressBase extends CRUDKubernetesDependentResource<Ingress, Trustify> implements Condition<Ingress, Trustify> {

    @Inject
    KubernetesClient k8sClient;

    public ServerIngressBase() {
        super(Ingress.class);
    }

    protected abstract String getHostname(Trustify cr);
    protected abstract IngressTLS getIngressTLS(Trustify cr);

    @SuppressWarnings("unchecked")
    protected Ingress newIngress(Trustify cr, Context<Trustify> context, String ingressName, Map<String, String> additionalLabels, Map<String, String> additionalAnnotations) {
        final var labels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        var port = ServerService.getServicePort(cr);

        String hostname = getHostname(cr);
        IngressTLS ingressTLS = getIngressTLS(cr);
        List<IngressTLS> ingressTLSList = ingressTLS != null ? Collections.singletonList(ingressTLS) : Collections.emptyList();

        return new IngressBuilder()
                .withNewMetadata()
                    .withName(ingressName)
                    .withNamespace(cr.getMetadata().getNamespace())
                    .withAnnotations(additionalAnnotations)
                    .withLabels(labels)
                    .addToLabels(additionalLabels)
                    .withOwnerReferences(CRDUtils.getOwnerReference(cr))
                .endMetadata()
                .withNewSpec()
                    .addNewRule()
                        .withHost(hostname)
                        .withNewHttp()
                            .addNewPath()
                                .withPath("/")
                                .withPathType("Prefix")
                                .withNewBackend()
                                    .withNewService()
                                        .withName(ServerService.getServiceName(cr))
                                        .withNewPort()
                                        .withNumber(port)
                                        .endPort()
                                    .endService()
                                .endBackend()
                            .endPath()
                        .endHttp()
                    .endRule()
                    .withTls(ingressTLSList)
                .endSpec()
                .build();
    }

    protected String getBaseHostname(Trustify cr) {
        String hostname = "";
        final var hostnameSpec = cr.getSpec().hostnameSpec();
        if (hostnameSpec != null && hostnameSpec.hostname() != null) {
            hostname = hostnameSpec.hostname();
        } else {
            hostname = k8sClient.getConfiguration().getNamespace() + "-" +
                    cr.getMetadata().getName() + "." +
                    getClusterDomainOnOpenshift().orElse("");
        }

        return hostname;
    }

    protected Optional<String> getClusterDomainOnOpenshift() {
        String clusterDomain = null;
        try {
            CustomResourceDefinitionContext customResourceDefinitionContext = new CustomResourceDefinitionContext.Builder()
                    .withName("Ingress")
                    .withGroup("config.openshift.io")
                    .withVersion("v1")
                    .withPlural("ingresses")
                    .withScope("Cluster")
                    .build();
            GenericKubernetesResource clusterObject = k8sClient.genericKubernetesResources(customResourceDefinitionContext)
                    .withName("cluster")
                    .get();

            Map<String, String> objectSpec = Optional.ofNullable(clusterObject)
                    .map(kubernetesResource -> kubernetesResource.<Map<String, String>>get("spec"))
                    .orElse(Collections.emptyMap());
            clusterDomain = objectSpec.get("domain");
        } catch (KubernetesClientException exception) {
            // Nothing to do
            Log.info("No Openshift host found");
        }

        return Optional.ofNullable(clusterDomain);
    }

}
