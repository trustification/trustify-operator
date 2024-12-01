package org.trustify.operator.controllers.setup;

public class K3sWithOlmResource extends BaseK3sResource {
    @Override
    protected boolean requiresOlm() {
        return true;
    }
}
