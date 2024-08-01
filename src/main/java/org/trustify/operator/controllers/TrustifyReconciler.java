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
import org.trustify.operator.cdrs.v2alpha1.api.ApiDeployment;
import org.trustify.operator.cdrs.v2alpha1.api.ApiIngress;
import org.trustify.operator.cdrs.v2alpha1.api.ApiIngressSecure;
import org.trustify.operator.cdrs.v2alpha1.api.ApiService;
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

                @Dependent(name = "api-deployment", type = ApiDeployment.class, dependsOn = {"db-service"}, readyPostcondition = ApiDeployment.class, useEventSourceWithName = TrustifyReconciler.DEPLOYMENT_EVENT_SOURCE),
                @Dependent(name = "api-service", type = ApiService.class, dependsOn = {"db-service"}, useEventSourceWithName = TrustifyReconciler.SERVICE_EVENT_SOURCE),

                @Dependent(name = "ingress", type = ApiIngress.class, dependsOn = {"db-service"}, readyPostcondition = ApiIngress.class, useEventSourceWithName = TrustifyReconciler.INGRESS_EVENT_SOURCE),
                @Dependent(name = "ingress-secure", type = ApiIngressSecure.class, dependsOn = {"db-service"}, readyPostcondition = ApiIngressSecure.class, useEventSourceWithName = TrustifyReconciler.INGRESS_EVENT_SOURCE)
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
                        status.setType(TrustifyStatusCondition.Successful);
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
