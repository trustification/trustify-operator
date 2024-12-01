package org.trustify.operator.cdrs.v2alpha1.keycloak.services;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.operatorhub.v1.OperatorGroup;
import io.fabric8.openshift.api.model.operatorhub.v1.OperatorGroupBuilder;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.trustify.operator.KeycloakSubscriptionConfig;
import org.trustify.operator.cdrs.v2alpha1.Trustify;

import java.util.AbstractMap;
import java.util.Objects;

@ApplicationScoped
public class KeycloakOperatorService {

    @Inject
    KubernetesClient k8sClient;

    @Inject
    KeycloakSubscriptionConfig keycloakSubscriptionConfig;

    private Subscription subscription(Trustify cr) {
        return new SubscriptionBuilder()
                .withNewMetadata()
                    .withName("my-keycloak-operator")
                    .withNamespace(cr.getMetadata().getNamespace())
                .endMetadata()
                .withNewSpec()
                    .withChannel(keycloakSubscriptionConfig.channel())
                    .withName("keycloak-operator")
                    .withSource(keycloakSubscriptionConfig.source())
                    .withSourceNamespace(keycloakSubscriptionConfig.namespace())
                .endSpec()
                .build();
    }

    public boolean subscriptionExists(Trustify cr) {
        return k8sClient.resource(subscription(cr))
                .inNamespace(cr.getMetadata().getNamespace())
                .get() != null;
    }

    public void createSubscription(Trustify cr) {
        OperatorGroup operatorGroup = new OperatorGroupBuilder()
                .withNewMetadata()
                .withName("operatorgroup")
                .withNamespace(cr.getMetadata().getNamespace())
                .endMetadata()
                .withNewSpec()
                .addToTargetNamespaces(cr.getMetadata().getNamespace())
                .endSpec()
                .build();
        k8sClient.resource(operatorGroup)
                .inNamespace(cr.getMetadata().getNamespace())
                .create();

        Subscription subscription = subscription(cr);
        k8sClient.resource(subscription)
                .inNamespace(cr.getMetadata().getNamespace())
                .create();
    }

    public AbstractMap.SimpleEntry<Boolean, String> isSubscriptionReady(Trustify cr) {
        Subscription subscription = k8sClient.resource(subscription(cr))
                .inNamespace(cr.getMetadata().getNamespace())
                .get();
        boolean isSubscriptionHealthy = subscription != null && subscription.getStatus() != null && subscription.getStatus()
                .getCatalogHealth()
                .stream().anyMatch(SubscriptionCatalogHealth::getHealthy);
        if (!isSubscriptionHealthy) {
            return new AbstractMap.SimpleEntry<>(false, "Subscription is not healthy");
        }

        String currentCSV = subscription.getStatus().getCurrentCSV();
        if (currentCSV == null) {
            return new AbstractMap.SimpleEntry<>(false, "Subscription does not have currentCSV");
        }

        ClusterServiceVersion clusterServiceVersion = new ClusterServiceVersionBuilder()
                .withNewMetadata()
                .withName(currentCSV)
                .endMetadata()
                .build();
        clusterServiceVersion = k8sClient.resource(clusterServiceVersion)
                .inNamespace(cr.getMetadata().getNamespace())
                .get();
        if (clusterServiceVersion == null) {
            return new AbstractMap.SimpleEntry<>(false, "ClusterServiceVersion does not exist");
        }

        String phase = clusterServiceVersion.getStatus().getPhase();
        if (!Objects.equals(phase, "Succeeded")) {
            return new AbstractMap.SimpleEntry<>(false, "CSV has not Succeeded yet. Waiting for it.");
        }

        return new AbstractMap.SimpleEntry<>(true, "Subscription is ready.");
    }

}
