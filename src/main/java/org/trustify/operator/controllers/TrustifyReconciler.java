package org.trustify.operator.controllers;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import org.jboss.logging.Logger;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifyStatusCondition;
import org.trustify.operator.cdrs.v2alpha1.db.*;
import org.trustify.operator.cdrs.v2alpha1.keycloak.*;
import org.trustify.operator.cdrs.v2alpha1.server.ServerDeployment;
import org.trustify.operator.cdrs.v2alpha1.server.ServerIngress;
import org.trustify.operator.cdrs.v2alpha1.server.ServerService;

import java.time.Duration;
import java.util.Map;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

@ControllerConfiguration(
        namespaces = WATCH_CURRENT_NAMESPACE,
        name = "trustify",
        dependents = {
                // OIDC
                @Dependent(
                        name = "oidc-db-pvc",
                        type = KeycloakDBPersistentVolumeClaim.class,
                        activationCondition = KeycloakDBPersistentVolumeClaimActivationCondition.class,
                        useEventSourceWithName = TrustifyReconciler.PVC_EVENT_SOURCE
                ),
//                @Dependent(
//                        name = "oidc-db-secret",
//                        type = KeycloakDBSecret.class,
//                        activationCondition = KeycloakDBSecretActivationCondition.class
//                ),
//                @Dependent(
//                        name = "oidc-db-deployment",
//                        type = KeycloakDBDeployment.class,
//                        dependsOn = {"db-pvc", "db-secret"},
//                        readyPostcondition = KeycloakDBDeployment.class,
//                        activationCondition = KeycloakDBDeploymentActivationCondition.class
//                ),
//                @Dependent(
//                        name = "oidc-db-service",
//                        type = KeycloakDBService.class,
//                        dependsOn = {"oidc-db-deployment"},
//                        activationCondition = KeycloakDBServiceActivationCondition.class
//                ),

                // Trustify
                @Dependent(
                        name = "db-pvc",
                        type = DBPersistentVolumeClaim.class,
                        activationCondition = DBPersistentVolumeClaimActivationCondition.class,
                        useEventSourceWithName = TrustifyReconciler.PVC_EVENT_SOURCE
                ),
//                @Dependent(
//                        name = "db-secret",
//                        type = DBSecret.class,
//                        activationCondition = DBSecretActivationCondition.class
//                ),
//                @Dependent(
//                        name = "db-deployment",
//                        type = DBDeployment.class,
//                        dependsOn = {"db-pvc", "db-secret"},
//                        readyPostcondition = DBDeployment.class,
//                        activationCondition = DBDeploymentActivationCondition.class
//                ),
//                @Dependent(
//                        name = "db-service",
//                        type = DBService.class,
//                        dependsOn = {"db-deployment"},
//                        activationCondition = DBServiceActivationCondition.class
//                ),
//
//                @Dependent(
//                        name = "server-deployment",
//                        type = ServerDeployment.class,
////                        dependsOn = {"db-service"},
//                        readyPostcondition = ServerDeployment.class,
//                        useEventSourceWithName = "server-deployment"
//                ),
//                @Dependent(
//                        name = "server-service",
//                        type = ServerService.class,
//                        dependsOn = {"server-deployment"},
//                        useEventSourceWithName = "server-service"
//                ),
//
//                @Dependent(
//                        name = "ingress",
//                        type = ServerIngress.class,
//                        dependsOn = {"server-service"},
//                        readyPostcondition = ServerIngress.class
//                )
        }
)
public class TrustifyReconciler implements Reconciler<Trustify>, ContextInitializer<Trustify>, EventSourceInitializer<Trustify> {

    private static final Logger logger = Logger.getLogger(TrustifyReconciler.class);

    public static final String PVC_EVENT_SOURCE = "pvcSource";
    public static final String DEPLOYMENT_EVENT_SOURCE = "deploymentSource";
    public static final String SERVICE_EVENT_SOURCE = "serviceSource";

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
    public UpdateControl<Trustify> reconcile(Trustify cr, Context context) {
        return context.managedDependentResourceContext()
                .getWorkflowReconcileResult()
                .map(wrs -> {
                    if (wrs.allDependentResourcesReady()) {
                        if (cr.getStatus().isAvailable()) {
                            logger.infof("Trustify {} is ready to be used", cr.getMetadata().getName());
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
        var pvcInformerConfiguration = InformerConfiguration.from(PersistentVolumeClaim.class, context).build();
        var deploymentInformerConfiguration = InformerConfiguration.from(Deployment.class, context).build();
        var serviceInformerConfiguration = InformerConfiguration.from(Service.class, context).build();

        var pvcInformerEventSource = new InformerEventSource<>(pvcInformerConfiguration, context);
        var deploymentInformerEventSource = new InformerEventSource<>(deploymentInformerConfiguration, context);
        var serviceInformerEventSource = new InformerEventSource<>(serviceInformerConfiguration, context);

        return Map.of(
                PVC_EVENT_SOURCE, pvcInformerEventSource,
                DEPLOYMENT_EVENT_SOURCE, deploymentInformerEventSource,
                SERVICE_EVENT_SOURCE, serviceInformerEventSource
        );
    }
}
