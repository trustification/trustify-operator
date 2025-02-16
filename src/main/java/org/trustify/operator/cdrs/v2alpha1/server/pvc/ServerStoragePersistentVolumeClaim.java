package org.trustify.operator.cdrs.v2alpha1.server.pvc;

import io.fabric8.kubernetes.api.model.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.trustify.operator.Constants;
import org.trustify.operator.TrustifyConfig;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;

import java.util.Map;
import java.util.Optional;

@KubernetesDependent(labelSelector = ServerStoragePersistentVolumeClaim.LABEL_SELECTOR, resourceDiscriminator = ServerStoragePersistentVolumeClaimDiscriminator.class)
@ApplicationScoped
public class ServerStoragePersistentVolumeClaim extends CRUDKubernetesDependentResource<PersistentVolumeClaim, Trustify>
        implements Creator<PersistentVolumeClaim, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=server";

    @Inject
    TrustifyConfig trustifyConfig;

    public ServerStoragePersistentVolumeClaim() {
        super(PersistentVolumeClaim.class);
    }

    @Override
    protected PersistentVolumeClaim desired(Trustify cr, Context<Trustify> context) {
        return newPersistentVolumeClaim(cr, context);
    }

    private PersistentVolumeClaim newPersistentVolumeClaim(Trustify cr, Context<Trustify> context) {
        String pvcStorageSize = Optional.ofNullable(cr.getSpec().storageSpec())
                .flatMap(storageSpec -> Optional.ofNullable(storageSpec.filesystemStorageSpec()))
                .map(TrustifySpec.FilesystemStorageSpec::pvcSize)
                .orElse(trustifyConfig.defaultPvcSize());

        return new PersistentVolumeClaimBuilder()
                .withMetadata(Constants.metadataBuilder
                        .apply(new Constants.Resource(getPersistentVolumeClaimName(cr), LABEL_SELECTOR, cr))
                        .build()
                )
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
    public Result<PersistentVolumeClaim> match(PersistentVolumeClaim actual, Trustify cr, Context<Trustify> context) {
        final var desiredPersistentVolumeClaimName = getPersistentVolumeClaimName(cr);
        return Result.nonComputed(actual
                .getMetadata()
                .getName()
                .equals(desiredPersistentVolumeClaimName)
        );
    }

    public static String getPersistentVolumeClaimName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.SERVER_PVC_SUFFIX;
    }

}
