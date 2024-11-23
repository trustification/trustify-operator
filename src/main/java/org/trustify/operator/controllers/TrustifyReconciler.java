package org.trustify.operator.controllers;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifyStatusCondition;
import org.trustify.operator.cdrs.v2alpha1.db.*;
import org.trustify.operator.cdrs.v2alpha1.keycloak.*;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.deployment.Keycloak;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.realmimport.KeycloakRealmImport;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.realmimport.KeycloakRealmImportStatusCondition;
import org.trustify.operator.cdrs.v2alpha1.keycloak.services.KeycloakOperator;
import org.trustify.operator.cdrs.v2alpha1.keycloak.services.KeycloakRealm;
import org.trustify.operator.cdrs.v2alpha1.keycloak.services.KeycloakServer;
import org.trustify.operator.cdrs.v2alpha1.keycloak.utils.KeycloakUtils;
import org.trustify.operator.cdrs.v2alpha1.server.*;
import org.trustify.operator.cdrs.v2alpha1.ui.UIDeployment;
import org.trustify.operator.cdrs.v2alpha1.ui.UIIngress;
import org.trustify.operator.cdrs.v2alpha1.ui.UIService;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

@ControllerConfiguration(
        namespaces = WATCH_CURRENT_NAMESPACE,
        name = "trustify",
        dependents = {
                @Dependent(
                        name = "keycloak-tls-secret",
                        type = KeycloakHttpTlsSecret.class,
                        activationCondition = KeycloakHttpTlsSecretActivationCondition.class
                ),
                @Dependent(
                        name = "keycloak-db-pvc",
                        type = KeycloakDBPersistentVolumeClaim.class,
                        activationCondition = KeycloakDBPersistentVolumeClaimActivationCondition.class
                ),
                @Dependent(
                        name = "keycloak-db-secret",
                        type = KeycloakDBSecret.class,
                        activationCondition = KeycloakDBSecretActivationCondition.class
                ),
                @Dependent(
                        name = "keycloak-db-deployment",
                        type = KeycloakDBDeployment.class,
                        dependsOn = {"db-pvc", "db-secret"},
                        readyPostcondition = KeycloakDBDeployment.class,
                        activationCondition = KeycloakDBDeploymentActivationCondition.class
                ),
                @Dependent(
                        name = "keycloak-db-service",
                        type = KeycloakDBService.class,
                        activationCondition = KeycloakDBServiceActivationCondition.class
                ),

                @Dependent(
                        name = "db-pvc",
                        type = DBPersistentVolumeClaim.class,
                        activationCondition = DBPersistentVolumeClaimActivationCondition.class
                ),
                @Dependent(
                        name = "db-secret",
                        type = DBSecret.class,
                        activationCondition = DBSecretActivationCondition.class
                ),
                @Dependent(
                        name = "db-deployment",
                        type = DBDeployment.class,
                        dependsOn = {"db-pvc", "db-secret"},
                        readyPostcondition = DBDeployment.class,
                        activationCondition = DBDeploymentActivationCondition.class
                ),
                @Dependent(
                        name = "db-service",
                        type = DBService.class,
                        activationCondition = DBServiceActivationCondition.class
                ),

                @Dependent(
                        name = "server-configmap",
                        type = ServerConfigurationConfigMap.class,
                        activationCondition = ServerConfigurationConfigMapActivationCondition.class
                ),
                @Dependent(
                        name = "server-pvc",
                        type = ServerStoragePersistentVolumeClaim.class,
                        activationCondition = ServerStoragePersistentVolumeClaimActivationCondition.class
                ),
                @Dependent(
                        name = "server-deployment",
                        type = ServerDeployment.class,
                        readyPostcondition = ServerDeployment.class
                ),
                @Dependent(
                        name = "server-service",
                        type = ServerService.class
                ),

                @Dependent(
                        name = "ui-deployment",
                        type = UIDeployment.class,
                        readyPostcondition = UIDeployment.class
                ),
                @Dependent(
                        name = "ui-service",
                        type = UIService.class
                ),

                @Dependent(
                        name = "ui-ingress",
                        type = UIIngress.class,
                        readyPostcondition = UIIngress.class
                )
        }
)
public class TrustifyReconciler implements Reconciler<Trustify>, Cleaner<Trustify>, ContextInitializer<Trustify>, EventSourceInitializer<Trustify> {

    private static final Logger logger = Logger.getLogger(TrustifyReconciler.class);

    public static final String CONFIG_MAP_EVENT_SOURCE = "configMapSource";
    public static final String PVC_EVENT_SOURCE = "pcvSource";
    public static final String SECRET_EVENT_SOURCE = "secretSource";
    public static final String DEPLOYMENT_EVENT_SOURCE = "deploymentSource";
    public static final String SERVICE_EVENT_SOURCE = "serviceSource";

    @Inject
    KubernetesClient k8sClient;

    @Inject
    KeycloakOperator keycloakOperator;

    @Inject
    KeycloakServer keycloakServer;

    @Inject
    KeycloakRealm keycloakRealm;

    @Override
    public void initContext(Trustify cr, Context<Trustify> context) {
        final var labels = Map.of(
                "app.kubernetes.io/managed-by", "trustify-operator",
                "app.kubernetes.io/name", cr.getMetadata().getName(),
                "app.kubernetes.io/part-of", cr.getMetadata().getName(),
                "trustify-operator/cluster", org.trustify.operator.Constants.TRUSTI_NAME
        );
        context.managedDependentResourceContext().put(org.trustify.operator.Constants.CONTEXT_LABELS_KEY, labels);
    }

    @Override
    public UpdateControl<Trustify> reconcile(Trustify cr, Context<Trustify> context) {
        Optional<UpdateControl<Trustify>> kcUpdateControl = createOrUpdateKeycloakResources(cr, context);
        return kcUpdateControl.orElseGet(() -> createOrUpdateDependantResources(cr, context));
    }

    @Override
    public DeleteControl cleanup(Trustify cr, Context<Trustify> context) {
        keycloakRealm.cleanupDependentResources(cr);
        keycloakServer.cleanupDependentResources(cr);

        return DeleteControl.defaultDelete();
    }

    private Optional<UpdateControl<Trustify>> createOrUpdateKeycloakResources(Trustify cr, Context<Trustify> context) {
        boolean isKcRequired = KeycloakUtils.isKeycloakRequired(cr);
        if (isKcRequired) {
            // Keycloak Operator
            boolean kcSubscriptionExists = keycloakOperator.subscriptionExists(cr);
            if (!kcSubscriptionExists) {
                logger.info("Installing Keycloak Operator");
                keycloakOperator.createSubscription(cr);
            }

            AbstractMap.SimpleEntry<Boolean, String> subscriptionReady = keycloakOperator.isSubscriptionReady(cr);
            if (!subscriptionReady.getKey()) {
                logger.infof("Waiting for the Keycloak Operator to be ready: %s", subscriptionReady.getValue());
                return Optional.of(UpdateControl.<Trustify>noUpdate().rescheduleAfter(5, TimeUnit.SECONDS));
            }

            // Keycloak Server
            Keycloak kcInstance = keycloakServer.getCurrentInstance(cr)
                    .orElseGet(() -> {
                        logger.info("Creating a Keycloak Server");
                        return keycloakServer.initInstance(cr);
                    });
            boolean isKcInstanceReady = kcInstance.getStatus() != null && kcInstance.getStatus()
                    .getConditions().stream()
                    .anyMatch(condition -> Objects.equals(condition.getType(), "Ready") && Objects.equals(condition.getStatus(), true));
            if (!isKcInstanceReady) {
                logger.info("Waiting for the Keycloak Server to be ready");
                return Optional.of(UpdateControl.<Trustify>noUpdate().rescheduleAfter(5, TimeUnit.SECONDS));
            }

            // Keycloak Realm
            KeycloakRealmImport realmImportInstance = keycloakRealm.getCurrentInstance(cr)
                    .orElseGet(() -> {
                        logger.info("Creating a KeycloakRealmImport");
                        return keycloakRealm.initInstance(cr);
                    });
            boolean isRealmImportInstanceReady = realmImportInstance.getStatus() != null && realmImportInstance.getStatus()
                    .getConditions().stream()
                    .anyMatch(condition -> Objects.equals(condition.getType(), KeycloakRealmImportStatusCondition.DONE) && Objects.equals(condition.getStatus(), true));
            if (!isRealmImportInstanceReady) {
                logger.info("Waiting for the KeycloakRealmImport to be ready");
                return Optional.of(UpdateControl.<Trustify>noUpdate().rescheduleAfter(5, TimeUnit.SECONDS));
            }
        }

        return Optional.empty();
    }

    private UpdateControl<Trustify> createOrUpdateDependantResources(Trustify cr, Context<Trustify> context) {
        return context.managedDependentResourceContext()
                .getWorkflowReconcileResult()
                .map(wrs -> {
                    if (wrs.allDependentResourcesReady()) {
                        if (cr.getStatus().isAvailable()) {
                            logger.infof("Trustify %s is ready to be used", cr.getMetadata().getName());
                        }

                        TrustifyStatusCondition status = new TrustifyStatusCondition();
                        status.setType(TrustifyStatusCondition.SUCCESSFUL);
                        status.setStatus(true);

                        cr.getStatus().setCondition(status);

                        return UpdateControl.updateStatus(cr);
                    } else {
                        TrustifyStatusCondition status = new TrustifyStatusCondition();
                        status.setType(TrustifyStatusCondition.PROCESSING);
                        status.setStatus(true);

                        cr.getStatus().setCondition(status);

                        final var duration = Duration.ofSeconds(5);
                        return UpdateControl.updateStatus(cr).rescheduleAfter(duration);
                    }
                })
                .orElseThrow();
    }

    @Override
    public Map<String, EventSource> prepareEventSources(EventSourceContext<Trustify> context) {
        var configMapInformerConfiguration = InformerConfiguration.from(ConfigMap.class, context).build();
        var pcvInformerConfiguration = InformerConfiguration.from(PersistentVolumeClaim.class, context).build();
        var secretInformerConfiguration = InformerConfiguration.from(Secret.class, context).build();
        var deploymentInformerConfiguration = InformerConfiguration.from(Deployment.class, context).build();
        var serviceInformerConfiguration = InformerConfiguration.from(Service.class, context).build();

        var configMapInformerConfigurationInformerEventSource = new InformerEventSource<>(configMapInformerConfiguration, context);
        var pcvInformerEventSource = new InformerEventSource<>(pcvInformerConfiguration, context);
        var secretInformerEventSource = new InformerEventSource<>(secretInformerConfiguration, context);
        var deploymentInformerEventSource = new InformerEventSource<>(deploymentInformerConfiguration, context);
        var serviceInformerEventSource = new InformerEventSource<>(serviceInformerConfiguration, context);

        return Map.of(
                CONFIG_MAP_EVENT_SOURCE, configMapInformerConfigurationInformerEventSource,
                PVC_EVENT_SOURCE, pcvInformerEventSource,
                SECRET_EVENT_SOURCE, secretInformerEventSource,
                DEPLOYMENT_EVENT_SOURCE, deploymentInformerEventSource,
                SERVICE_EVENT_SOURCE, serviceInformerEventSource
        );
    }
}
