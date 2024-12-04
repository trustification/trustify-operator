package org.trustify.operator.cdrs.v2alpha1.ui.service;

import io.fabric8.kubernetes.api.model.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.utils.CRDUtils;

import java.util.Map;

@KubernetesDependent(labelSelector = UIService.LABEL_SELECTOR, resourceDiscriminator = UIServiceDiscriminator.class)
@ApplicationScoped
public class UIService extends CRUDKubernetesDependentResource<Service, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=ui";

    public UIService() {
        super(Service.class);
    }

    @Override
    public Service desired(Trustify cr, Context context) {
        return newService(cr, context);
    }

    @SuppressWarnings("unchecked")
    private Service newService(Trustify cr, Context context) {
        final var labels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        return new ServiceBuilder()
                .withNewMetadata()
                .withName(getServiceName(cr))
                .withNamespace(cr.getMetadata().getNamespace())
                .withLabels(labels)
                .addToLabels("component", "ui")
                .withOwnerReferences(CRDUtils.getOwnerReference(cr))
                .endMetadata()
                .withSpec(getServiceSpec(cr))
                .build();
    }

    private ServiceSpec getServiceSpec(Trustify cr) {
        return new ServiceSpecBuilder()
                .withPorts(
                        new ServicePortBuilder()
                                .withName("http")
                                .withPort(getServicePort(cr))
                                .withProtocol(Constants.SERVICE_PROTOCOL)
                                .build()
                )
                .withSelector(Constants.UI_SELECTOR_LABELS)
                .withType("ClusterIP")
                .build();
    }

    public static int getServicePort(Trustify cr) {
        return Constants.HTTP_PORT;
    }

    public static String getServiceName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.UI_SERVICE_SUFFIX;
    }

}
