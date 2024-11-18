package org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.realmimport;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.fabric8.kubernetes.api.model.SecretKeySelector;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Placeholder {
    private SecretKeySelector secret;

    public Placeholder() {
    }

    public Placeholder(SecretKeySelector secret) {
        this.secret = secret;
    }

    public SecretKeySelector getSecret() {
        return secret;
    }

    public void setSecret(SecretKeySelector secret) {
        this.secret = secret;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Placeholder that = (Placeholder) o;
        return getSecret().equals(that.getSecret());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSecret());
    }
}
