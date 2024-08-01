package org.trustify.operator.cdrs.v2alpha1.server;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import org.trustify.operator.cdrs.v2alpha1.Trustify;

import java.util.Optional;

public class ServerIngressSecureDiscriminator implements ResourceDiscriminator<Ingress, Trustify> {
    @Override
    public Optional<Ingress> distinguish(Class<Ingress> resource, Trustify cr, Context<Trustify> context) {
        String ingressName = ServerIngressSecure.getIngressName(cr);
        ResourceID resourceID = new ResourceID(ingressName, cr.getMetadata().getNamespace());
        var informerEventSource = (InformerEventSource<Ingress, Trustify>) context.eventSourceRetriever().getResourceEventSourceFor(Ingress.class);
        return informerEventSource.get(resourceID);
    }
}
