package org.trustify.operator.cdrs.v2alpha1.server.db.pvc;

import io.fabric8.kubernetes.api.model.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.trustify.operator.Constants;
import org.trustify.operator.TrustifyConfig;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.utils.CRDUtils;

import java.util.Map;

@KubernetesDependent(labelSelector = DBPersistentVolumeClaim.LABEL_SELECTOR, resourceDiscriminator = DBPersistentVolumeClaimDiscriminator.class)
@ApplicationScoped
public class DBPersistentVolumeClaim extends CRUDKubernetesDependentResource<PersistentVolumeClaim, Trustify>
        implements Creator<PersistentVolumeClaim, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=db";

    @Inject
    TrustifyConfig trustifyConfig;

    public DBPersistentVolumeClaim() {
        super(PersistentVolumeClaim.class);
    }

    @Override
    protected PersistentVolumeClaim desired(Trustify cr, Context<Trustify> context) {
        return newPersistentVolumeClaim(cr, context);
    }

    @SuppressWarnings("unchecked")
    private PersistentVolumeClaim newPersistentVolumeClaim(Trustify cr, Context<Trustify> context) {
        final var labels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        String pvcStorageSize = CRDUtils.getValueFromSubSpec(cr.getSpec().databaseSpec(), TrustifySpec.DatabaseSpec::pvcSize)
                .orElse(trustifyConfig.defaultPvcSize());

        return new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                .withName(getPersistentVolumeClaimName(cr))
                .withNamespace(cr.getMetadata().getNamespace())
                .withLabels(labels)
                .addToLabels("component", "db")
                .withOwnerReferences(CRDUtils.getOwnerReference(cr))
                .endMetadata()
                .withSpec(new PersistentVolumeClaimSpecBuilder()
                        .withAccessModes("ReadWriteOnce")
                        .withResources(new VolumeResourceRequirementsBuilder()
                                .withRequests(Map.of("storage", new Quantity(pvcStorageSize)))
                                .build()
                        )
                        .build()
                )
                .build();
    }

    @Override
    public Matcher.Result<PersistentVolumeClaim> match(PersistentVolumeClaim actual, Trustify cr, Context<Trustify> context) {
        final var desiredPersistentVolumeClaimName = getPersistentVolumeClaimName(cr);
        return Matcher.Result.nonComputed(actual
                .getMetadata()
                .getName()
                .equals(desiredPersistentVolumeClaimName)
        );
    }

    public static String getPersistentVolumeClaimName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.DB_PVC_SUFFIX;
    }

}
