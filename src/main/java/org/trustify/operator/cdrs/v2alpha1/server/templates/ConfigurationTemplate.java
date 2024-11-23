package org.trustify.operator.cdrs.v2alpha1.server.templates;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@CheckedTemplate
public class ConfigurationTemplate {

    public record Data(
            String serverUrl,
            String uiClientId,
            String backendClientId
    ) {
    }

    /**
     * Searches for a file with the name of the method of this function into "resources/templates
     *
     * @param data Should Match the fields in the template file
     * @return a Template instance
     */
    public static native TemplateInstance configuration(Data data);

}
