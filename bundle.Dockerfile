FROM quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-21 AS build

ARG OPERATOR_IMAGE

ARG RELATED_IMAGE_SERVER
ENV RELATED_IMAGE_SERVER=${RELATED_IMAGE_SERVER:+unset}

ARG RELATED_IMAGE_DB
ENV RELATED_IMAGE_DB=${RELATED_IMAGE_DB}

COPY --chown=quarkus:quarkus mvnw /code/mvnw
COPY --chown=quarkus:quarkus .mvn /code/.mvn
COPY --chown=quarkus:quarkus pom.xml /code/
USER quarkus
WORKDIR /code
RUN ./mvnw -B org.apache.maven.plugins:maven-dependency-plugin:3.1.2:go-offline
COPY src/main /code/src/main
RUN [[ -z $RELATED_IMAGE_SERVER ]] && unset RELATED_IMAGE_SERVER; \
    [[ -z $RELATED_IMAGE_DB ]] && unset RELATED_IMAGE_DB; \
    ./mvnw package -DskipTests -Dquarkus.container-image.image=$OPERATOR_IMAGE

FROM registry.access.redhat.com/ubi9/ubi:latest as bundle
COPY scripts /scripts
COPY --from=build /code/target/bundle/trustify-operator/ /code/target/bundle/trustify-operator/
RUN dnf install curl zip unzip --allowerasing -y && \
curl -s "https://get.sdkman.io?rcupdate=false" | bash && \
source "$HOME/.sdkman/bin/sdkman-init.sh" && \
sdk install java && \
sdk install groovy && \
groovy scripts/enrichCSV.groovy /code/target/bundle/trustify-operator/manifests/trustify-operator.clusterserviceversion.yaml && \
echo '  com.redhat.openshift.versions: "v4.10"' >> /code/target/bundle/trustify-operator/metadata/annotations.yaml

FROM scratch
# Core bundle labels.
LABEL operators.operatorframework.io.bundle.channel.default.v1=alpha
LABEL operators.operatorframework.io.bundle.channels.v1=alpha
LABEL operators.operatorframework.io.bundle.manifests.v1=manifests/
LABEL operators.operatorframework.io.bundle.mediatype.v1=registry+v1
LABEL operators.operatorframework.io.bundle.metadata.v1=metadata/
LABEL operators.operatorframework.io.bundle.package.v1=trustify-operator
LABEL operators.operatorframework.io.metrics.builder=qosdk-bundle-generator/6.6.6+6212e1b
LABEL operators.operatorframework.io.metrics.mediatype.v1=metrics+v1
LABEL operators.operatorframework.io.metrics.project_layout=quarkus.javaoperatorsdk.io/v1-alpha

# Copy files to locations specified by labels.
COPY --from=bundle /code/target/bundle/trustify-operator/manifests /manifests/
COPY --from=bundle /code/target/bundle/trustify-operator/metadata /metadata/
