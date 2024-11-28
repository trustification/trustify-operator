package org.trustify.operator.cdrs.v2alpha1.ui.services;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressLoadBalancerIngress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.ui.UIIngress;

import java.util.Optional;

@ApplicationScoped
public class UIIngressService {

    @Inject
    KubernetesClient k8sClient;

    public Optional<String> getCurrentIngressIP(Trustify cr) {
        Ingress nullableIngress = k8sClient.resources(Ingress.class)
                .inNamespace(cr.getMetadata().getNamespace())
                .withName(UIIngress.getIngressName(cr))
                .get();

        // Openshift: Find Host if exists
        Optional<String> ingressHost = Optional.ofNullable(nullableIngress)
                .flatMap(ingress -> Optional.ofNullable(ingress.getSpec()))
                .flatMap(ingressSpec -> Optional.ofNullable(ingressSpec.getRules()))
                .flatMap(ingressRules -> ingressRules.stream().findFirst())
                .map(IngressRule::getHost);
        if (ingressHost.isPresent()) {
            return ingressHost;
        }

        // Minikube: Find IP if exists
        return Optional.ofNullable(nullableIngress)
                .flatMap(ingress -> Optional.ofNullable(ingress.getStatus()))
                .flatMap(status -> Optional.ofNullable(status.getLoadBalancer()))
                .flatMap(loadBalancerStatus -> Optional.ofNullable(loadBalancerStatus.getIngress()))
                .flatMap(loadBalancerIngresses -> loadBalancerIngresses.stream()
                        .filter(item -> item.getIp() != null)
                        .findAny()
                        .map(IngressLoadBalancerIngress::getIp)
                );
    }

    public Optional<String> getCurrentIngressURL(Trustify cr) {
        return getCurrentIngressIP(cr)
                .map(ip -> String.format("%s://%s", "http", ip));
    }
}
