package org.trustify.operator.cdrs.v2alpha1.keycloak.services;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.operatorhub.v1.OperatorGroup;
import io.fabric8.openshift.api.model.operatorhub.v1.OperatorGroupBuilder;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.keycloak.KeycloakDBDeployment;
import org.trustify.operator.cdrs.v2alpha1.keycloak.KeycloakDBSecret;
import org.trustify.operator.cdrs.v2alpha1.keycloak.KeycloakDBService;
import org.trustify.operator.cdrs.v2alpha1.keycloak.KeycloakHttpTlsSecret;
import org.trustify.operator.cdrs.v2alpha1.keycloak.models.Keycloak;
import org.trustify.operator.cdrs.v2alpha1.keycloak.models.KeycloakSpec;
import org.trustify.operator.cdrs.v2alpha1.keycloak.models.spec.DatabaseSpec;
import org.trustify.operator.cdrs.v2alpha1.keycloak.models.spec.HostnameSpec;
import org.trustify.operator.cdrs.v2alpha1.keycloak.models.spec.HttpSpec;
import org.trustify.operator.cdrs.v2alpha1.keycloak.models.spec.IngressSpec;

import java.util.AbstractMap;
import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
public class KeycloakService {

    @Inject
    KubernetesClient k8sClient;

    private Subscription subscription(Trustify cr) {
        return new SubscriptionBuilder()
                .withNewMetadata()
                .withName("my-keycloak-operator")
                .withNamespace(cr.getMetadata().getNamespace())
                .endMetadata()
                .withNewSpec()
                .withChannel("fast")
                .withName("keycloak-operator")
                .withSource("operatorhubio-catalog")
                .withSourceNamespace("olm")
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
        boolean isSubscriptionHealthy = subscription.getStatus()
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

    public static String getKeycloakName(Trustify cr) {
        return cr.getMetadata().getName() + "-keycloak";
    }

    public Keycloak initInstance(Trustify cr) {
        Keycloak keycloak = new Keycloak();

        keycloak.setMetadata(new ObjectMeta());
        keycloak.getMetadata().setName(getKeycloakName(cr));
        keycloak.setSpec(new KeycloakSpec());

        KeycloakSpec spec = keycloak.getSpec();
        spec.setInstances(1);

        // Database
        spec.setDatabaseSpec(new DatabaseSpec());
        DatabaseSpec databaseSpec = spec.getDatabaseSpec();
        databaseSpec.setVendor("postgres");
        databaseSpec.setHost(KeycloakDBService.getServiceName(cr));
        databaseSpec.setPort(5432);
        databaseSpec.setDatabase(KeycloakDBDeployment.getDatabaseName(cr));

        databaseSpec.setUsernameSecret(KeycloakDBSecret.getUsernameKeySelector(cr));
        databaseSpec.setPasswordSecret(KeycloakDBSecret.getPasswordKeySelector(cr));

        // Https
        spec.setHttpSpec(new HttpSpec());
        HttpSpec httpSpec = spec.getHttpSpec();
        httpSpec.setTlsSecret(KeycloakHttpTlsSecret.getSecretName(cr));

        // Ingress
        spec.setIngressSpec(new IngressSpec());
        spec.getIngressSpec().setIngressEnabled(false);

        // Hostname
        spec.setHostnameSpec(new HostnameSpec());
        spec.getHostnameSpec().setStrict(false);

        return k8sClient.resource(keycloak)
                .inNamespace(cr.getMetadata().getNamespace())
                .create();
    }

    public Optional<Keycloak> getCurrentInstance(Trustify cr) {
        Keycloak keycloak = k8sClient.resources(Keycloak.class)
                .inNamespace(cr.getMetadata().getNamespace())
                .withName(getKeycloakName(cr))
                .get();
        return Optional.ofNullable(keycloak);
    }

    public void cleanupDependentResources(Trustify cr) {
        getCurrentInstance(cr).ifPresent(keycloak -> {
            k8sClient.resource(keycloak).delete();
        });
    }
}
