package org.trustify.operator;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "trustify")
public interface TrustifyConfig {

    @WithName("default-pvc-size")
    String defaultPvcSize();
}
