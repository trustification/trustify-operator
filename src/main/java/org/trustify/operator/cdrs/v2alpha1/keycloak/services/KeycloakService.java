package org.trustify.operator.cdrs.v2alpha1.keycloak.services;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.operatorhub.v1.OperatorGroup;
import io.fabric8.openshift.api.model.operatorhub.v1.OperatorGroupBuilder;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.keycloak.KeycloakDBDeployment;
import org.trustify.operator.cdrs.v2alpha1.keycloak.KeycloakDBSecret;
import org.trustify.operator.cdrs.v2alpha1.keycloak.KeycloakDBService;
import org.trustify.operator.cdrs.v2alpha1.keycloak.models.Keycloak;
import org.trustify.operator.cdrs.v2alpha1.keycloak.models.KeycloakSpec;
import org.trustify.operator.cdrs.v2alpha1.keycloak.models.spec.DatabaseSpec;
import org.trustify.operator.cdrs.v2alpha1.keycloak.models.spec.HostnameSpec;
import org.trustify.operator.cdrs.v2alpha1.keycloak.models.spec.HttpSpec;

import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
public class KeycloakService {

    private static final Logger logger = Logger.getLogger(KeycloakService.class);

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

    public boolean isSubscriptionReady(Trustify cr) {
        Subscription subscription = k8sClient.resource(subscription(cr))
                .inNamespace(cr.getMetadata().getNamespace())
                .get();
        boolean isSubscriptionHealthy = subscription.getStatus()
                .getCatalogHealth()
                .stream().anyMatch(SubscriptionCatalogHealth::getHealthy);
        if (!isSubscriptionHealthy) {
            logger.warn("Subscription is not healthy");
            return false;
        }

        String currentCSV = subscription.getStatus().getCurrentCSV();
        if (currentCSV == null) {
            logger.warn("Subscription does not have currentCSV");
            return false;
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
            logger.warn("ClusterServiceVersion does not exist");
            return false;
        }

        String phase = clusterServiceVersion.getStatus().getPhase();
        if (!Objects.equals(phase, "Succeeded")) {
            logger.info("CSV has not Succeeded yet. Waiting for it.");
            return false;
        }

        return true;
    }

    public boolean crdExists() {
        CustomResourceDefinition customResourceDefinition = k8sClient.apiextensions().v1()
                .customResourceDefinitions()
                .withName("keycloaks.k8s.keycloak.org")
                .get();

        return customResourceDefinition != null;
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

        // Host
        spec.setHostnameSpec(new HostnameSpec());
        HostnameSpec hostnameSpec = spec.getHostnameSpec();
        hostnameSpec.setHostname("oeffrs.cunningham.aucunnin.lpi0.s1.devshift.org");

        // Https
        spec.setHttpSpec(new HttpSpec());
        HttpSpec httpSpec = spec.getHttpSpec();
        httpSpec.setTlsSecret("example-tls-secret");

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
