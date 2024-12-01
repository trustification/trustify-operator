package org.trustify.operator;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "keycloak-operator.subscription")
public interface KeycloakSubscriptionConfig {

    @WithName("namespace")
    String namespace();

    @WithName("source")
    String source();

    @WithName("channel")
    String channel();
}
