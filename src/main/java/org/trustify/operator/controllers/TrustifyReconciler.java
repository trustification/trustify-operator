package org.trustify.operator.controllers;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import org.jboss.logging.Logger;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifyStatusCondition;
import org.trustify.operator.cdrs.v2alpha1.server.ServerDeployment;
import org.trustify.operator.cdrs.v2alpha1.server.ServerIngress;
import org.trustify.operator.cdrs.v2alpha1.server.ServerService;
import org.trustify.operator.cdrs.v2alpha1.db.DBDeployment;
import org.trustify.operator.cdrs.v2alpha1.db.DBPersistentVolumeClaim;
import org.trustify.operator.cdrs.v2alpha1.db.DBSecret;
import org.trustify.operator.cdrs.v2alpha1.db.DBService;

import java.time.Duration;
import java.util.Map;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

@ControllerConfiguration(
        namespaces = WATCH_CURRENT_NAMESPACE,
        name = "trustify",
        dependents = {
                @Dependent(name = "db-pvc", type = DBPersistentVolumeClaim.class, useEventSourceWithName = TrustifyReconciler.PVC_EVENT_SOURCE),
                @Dependent(name = "db-secret", type = DBSecret.class),
                @Dependent(name = "db-deployment", type = DBDeployment.class, dependsOn = {"db-pvc", "db-secret"}, readyPostcondition = DBDeployment.class, useEventSourceWithName = TrustifyReconciler.DEPLOYMENT_EVENT_SOURCE),
                @Dependent(name = "db-service", type = DBService.class, dependsOn = {"db-deployment"}, useEventSourceWithName = TrustifyReconciler.SERVICE_EVENT_SOURCE),

                @Dependent(name = "server-deployment", type = ServerDeployment.class, dependsOn = {"db-service"}, readyPostcondition = ServerDeployment.class, useEventSourceWithName = TrustifyReconciler.DEPLOYMENT_EVENT_SOURCE),
                @Dependent(name = "server-service", type = ServerService.class, dependsOn = {"db-service"}, useEventSourceWithName = TrustifyReconciler.SERVICE_EVENT_SOURCE),

                @Dependent(name = "ingress", type = ServerIngress.class, dependsOn = {"db-service"}, readyPostcondition = ServerIngress.class, useEventSourceWithName = TrustifyReconciler.INGRESS_EVENT_SOURCE)
        }
)
public class TrustifyReconciler implements Reconciler<Trustify>, ContextInitializer<Trustify>,
        EventSourceInitializer<Trustify> {

    private static final Logger logger = Logger.getLogger(TrustifyReconciler.class);

    public static final String PVC_EVENT_SOURCE = "PVCEventSource";
    public static final String DEPLOYMENT_EVENT_SOURCE = "DeploymentEventSource";
    public static final String SERVICE_EVENT_SOURCE = "ServiceEventSource";
    public static final String INGRESS_EVENT_SOURCE = "IngressEventSource";

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
        var pcvInformerConfiguration = InformerConfiguration.from(PersistentVolumeClaim.class, context).build();
        var deploymentInformerConfiguration = InformerConfiguration.from(Deployment.class, context).build();
        var serviceInformerConfiguration = InformerConfiguration.from(Service.class, context).build();
        var ingressInformerConfiguration = InformerConfiguration.from(Ingress.class, context).build();

        var pcvInformerEventSource = new InformerEventSource<>(pcvInformerConfiguration, context);
        var deploymentInformerEventSource = new InformerEventSource<>(deploymentInformerConfiguration, context);
        var serviceInformerEventSource = new InformerEventSource<>(serviceInformerConfiguration, context);
        var ingressInformerEventSource = new InformerEventSource<>(ingressInformerConfiguration, context);

        return Map.of(
                PVC_EVENT_SOURCE, pcvInformerEventSource,
                DEPLOYMENT_EVENT_SOURCE, deploymentInformerEventSource,
                SERVICE_EVENT_SOURCE, serviceInformerEventSource,
                INGRESS_EVENT_SOURCE, ingressInformerEventSource
        );
    }
}
