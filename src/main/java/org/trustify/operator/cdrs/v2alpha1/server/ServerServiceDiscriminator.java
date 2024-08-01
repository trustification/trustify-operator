package org.trustify.operator.cdrs.v2alpha1.server;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import org.trustify.operator.cdrs.v2alpha1.Trustify;

import java.util.Optional;

public class ServerServiceDiscriminator implements ResourceDiscriminator<Service, Trustify> {
    @Override
    public Optional<Service> distinguish(Class<Service> resource, Trustify cr, Context<Trustify> context) {
        String serviceName = ServerService.getServiceName(cr);
        ResourceID resourceID = new ResourceID(serviceName, cr.getMetadata().getNamespace());
        var informerEventSource = (InformerEventSource<Service, Trustify>) context.eventSourceRetriever().getResourceEventSourceFor(Service.class);
        return informerEventSource.get(resourceID);
    }
}