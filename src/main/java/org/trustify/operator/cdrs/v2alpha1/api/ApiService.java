package org.trustify.operator.cdrs.v2alpha1.api;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.utils.CRDUtils;

import java.util.Map;

@KubernetesDependent(labelSelector = ApiService.LABEL_SELECTOR, resourceDiscriminator = ApiServiceDiscriminator.class)
@ApplicationScoped
public class ApiService extends CRUDKubernetesDependentResource<Service, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=server";

    public ApiService() {
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
                .addToLabels("component", "server")
                .withOwnerReferences(CRDUtils.getOwnerReference(cr))
                .endMetadata()
                .withSpec(getServiceSpec(cr))
                .build();
    }

    private ServiceSpec getServiceSpec(Trustify cr) {
        return new ServiceSpecBuilder()
                .addNewPort()
                .withPort(getServicePort(cr))
                .withProtocol(Constants.SERVICE_PROTOCOL)
                .endPort()
                .withSelector(Constants.SERVER_SELECTOR_LABELS)
                .withType("ClusterIP")
                .build();
    }

    public static int getServicePort(Trustify cr) {
        return Constants.HTTP_PORT;
    }

    public static String getServiceName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.SERVER_SERVICE_SUFFIX;
    }

    public static boolean isTlsConfigured(Trustify cr) {
        var tlsSecret = CRDUtils.getValueFromSubSpec(cr.getSpec().httpSpec(), TrustifySpec.HttpSpec::tlsSecret);
        return tlsSecret.isPresent() && !tlsSecret.get().trim().isEmpty();
    }
}
