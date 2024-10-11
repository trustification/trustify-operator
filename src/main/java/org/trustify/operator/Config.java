package org.trustify.operator;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "related.image")
public interface Config {

    @WithName("ui")
    String uiImage();

    @WithName("server")
    String serverImage();

    @WithName("db")
    String dbImage();

    @WithName("pull-policy")
    String imagePullPolicy();
}
