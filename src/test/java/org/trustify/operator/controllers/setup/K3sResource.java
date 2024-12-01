package org.trustify.operator.controllers.setup;

public class K3sResource extends BaseK3sResource {
    @Override
    protected boolean requiresOlm() {
        return false;
    }
}
