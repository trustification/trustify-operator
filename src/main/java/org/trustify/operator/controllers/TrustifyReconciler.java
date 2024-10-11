package org.trustify.operator.controllers;

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
import org.trustify.operator.cdrs.v2alpha1.server.ServerDeployment;
import org.trustify.operator.cdrs.v2alpha1.ui.UIDeployment;
import org.trustify.operator.cdrs.v2alpha1.ui.UIIngress;
import org.trustify.operator.cdrs.v2alpha1.server.ServerService;
import org.trustify.operator.cdrs.v2alpha1.ui.UIService;

import java.time.Duration;
import java.util.Map;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

@ControllerConfiguration(
        namespaces = WATCH_CURRENT_NAMESPACE,
        name = "trustify",
        dependents = {
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
                        name = "server-deployment",
                        type = ServerDeployment.class,
                        readyPostcondition = ServerDeployment.class,
                        useEventSourceWithName = TrustifyReconciler.DEPLOYMENT_EVENT_SOURCE
                ),
                @Dependent(
                        name = "server-service",
                        type = ServerService.class,
                        useEventSourceWithName = TrustifyReconciler.SERVICE_EVENT_SOURCE
                ),

                @Dependent(
                        name = "ui-deployment",
                        type = UIDeployment.class,
                        readyPostcondition = UIDeployment.class,
                        useEventSourceWithName = TrustifyReconciler.DEPLOYMENT_EVENT_SOURCE
                ),
                @Dependent(
                        name = "ui-service",
                        type = UIService.class,
                        useEventSourceWithName = TrustifyReconciler.SERVICE_EVENT_SOURCE
                ),
                @Dependent(
                        name = "ui-ingress",
                        type = UIIngress.class,
                        readyPostcondition = UIIngress.class
                )
        }
)
public class TrustifyReconciler implements Reconciler<Trustify>, ContextInitializer<Trustify>, EventSourceInitializer<Trustify> {

    private static final Logger logger = Logger.getLogger(TrustifyReconciler.class);

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
        var serverDeploymentInformerConfiguration = InformerConfiguration.from(Deployment.class, context).build();
        var serverServiceInformerConfiguration = InformerConfiguration.from(Service.class, context).build();

        var serverDeploymentInformerEventSource = new InformerEventSource<>(serverDeploymentInformerConfiguration, context);
        var serverServiceInformerEventSource = new InformerEventSource<>(serverServiceInformerConfiguration, context);

        return Map.of(
                DEPLOYMENT_EVENT_SOURCE, serverDeploymentInformerEventSource,
                SERVICE_EVENT_SOURCE, serverServiceInformerEventSource
        );
    }
}
