FROM quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-21 AS build

ARG QUARKUS_OPTS
ARG CHANNELS=alpha

COPY --chown=quarkus:quarkus mvnw /code/mvnw
COPY --chown=quarkus:quarkus .mvn /code/.mvn
COPY --chown=quarkus:quarkus pom.xml /code/
USER quarkus
WORKDIR /code
RUN ./mvnw -B org.apache.maven.plugins:maven-dependency-plugin:3.1.2:go-offline
COPY src/main /code/src/main
RUN ./mvnw package -DskipTests ${QUARKUS_OPTS} -Dquarkus.operator-sdk.bundle.channels=${CHANNELS}

FROM registry.access.redhat.com/ubi9/ubi:latest AS bundle
COPY scripts /scripts
COPY --from=build /code/target/bundle/trustify-operator/ /code/target/bundle/trustify-operator/
RUN dnf install curl zip unzip --allowerasing -y && \
    curl -s "https://get.sdkman.io?rcupdate=false" | bash && \
    source "$HOME/.sdkman/bin/sdkman-init.sh" && \
    sdk install java && \
    sdk install groovy && \
    groovy scripts/enrichCSV.groovy /code/target/bundle/trustify-operator/manifests/trustify-operator.clusterserviceversion.yaml
RUN curl --output /usr/bin/yq https://github.com/mikefarah/yq/releases/latest/download/yq_linux_amd64 && \
    chmod +x /usr/bin/yq && \
    yq e -P -i '.annotations."com.redhat.openshift.versions"="v4.10"'

FROM scratch
ARG CHANNELS=alpha

# Core bundle labels.
LABEL operators.operatorframework.io.bundle.channel.default.v1=${CHANNELS}
LABEL operators.operatorframework.io.bundle.channels.v1=${CHANNELS}
LABEL operators.operatorframework.io.bundle.manifests.v1=manifests/
LABEL operators.operatorframework.io.bundle.mediatype.v1=registry+v1
LABEL operators.operatorframework.io.bundle.metadata.v1=metadata/
LABEL operators.operatorframework.io.bundle.package.v1=trustify-operator
LABEL operators.operatorframework.io.metrics.builder=qosdk-bundle-generator/6.8.2+5def15d
LABEL operators.operatorframework.io.metrics.mediatype.v1=metrics+v1
LABEL operators.operatorframework.io.metrics.project_layout=quarkus.javaoperatorsdk.io/v1-alpha

# Copy files to locations specified by labels.
COPY --from=bundle /code/target/bundle/trustify-operator/manifests /manifests/
COPY --from=bundle /code/target/bundle/trustify-operator/metadata /metadata/
