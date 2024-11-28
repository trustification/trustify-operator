package org.trustify.operator.cdrs.v2alpha1.common;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.utils.CRDUtils;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;

@KubernetesDependent(labelSelector = CommonConfigMap.LABEL_SELECTOR, resourceDiscriminator = CommonConfigMapDiscriminator.class)
@ApplicationScoped
public class CommonConfigMap extends CRUDKubernetesDependentResource<ConfigMap, Trustify>
        implements Creator<ConfigMap, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=common";

    public CommonConfigMap() {
        super(ConfigMap.class);
    }

    @Override
    protected ConfigMap desired(Trustify cr, Context<Trustify> context) {
        return newConfigMap(cr, context);
    }

    @SuppressWarnings("unchecked")
    private ConfigMap newConfigMap(Trustify cr, Context<Trustify> context) {
        String cert = (String) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_CERTS_DEFAULT_KEY, Optional.class)
                .orElse("");

        byte[] tlsCert = Base64.getDecoder().decode(cert);

        final var labels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        return new ConfigMapBuilder()
                .withNewMetadata()
                .withName(getConfigMapName(cr))
                .withNamespace(cr.getMetadata().getNamespace())
                .withLabels(labels)
                .addToLabels("component", "common")
                .withOwnerReferences(CRDUtils.getOwnerReference(cr))
                .endMetadata()
                .withData(Map.of(
                        "tls.crt", "\n" + new String(tlsCert)
                ))
                .build();
    }

    @Override
    public Result<ConfigMap> match(ConfigMap actual, Trustify cr, Context<Trustify> context) {
        final var desiredConfigMap = getConfigMapName(cr);
        return Result.nonComputed(actual
                .getMetadata()
                .getName()
                .equals(desiredConfigMap)
        );
    }

    public static String getConfigMapName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.COMMON_CLUSTER_CERT_CONFIG_MAP_SUFFIX;
    }

    public static String getConfigMapClusterTlsKey(Trustify cr) {
        return "tls.crt";
    }

}
