ARG FROM_REPO=<myrepo>/temurin
ARG FROM_TAG=jre-17

FROM ${FROM_REPO}:${FROM_TAG} as builder

ENV GATEWAY_VERSION 5
ENV DOCKERIZE_VERSION v0.7.0

USER root

RUN set -eux && \
    apt-get -qq update && apt-get --no-install-recommends --no-install-suggests -yqq install curl && \
    curl --silent --location --retry 3 -o /tmp/gateway-ha.jar https://repo1.maven.org/maven2/io/trino/gateway/gateway-ha/$GATEWAY_VERSION/gateway-ha-$GATEWAY_VERSION-jar-with-dependencies.jar && \
    curl --silent --location --retry 3 https://github.com/jwilder/dockerize/releases/download/$DOCKERIZE_VERSION/dockerize-linux-amd64-$DOCKERIZE_VERSION.tar.gz | tar xz -C /usr/bin && \
    curl --silent --location --retry 3 -o /tmp/gateway-ha-persistence-postgres.sql https://github.com/trinodb/trino-gateway/blob/main/gateway-ha/src/main/resources/gateway-ha-persistence-postgres.sql

FROM ${FROM_REPO}:${FROM_TAG} as target

ARG GATEWAY_UID=1001
ARG GATEWAY_GID=1001

WORKDIR /opt/gateway

USER root

RUN groupadd gateway -g ${GATEWAY_GID} && \
    useradd -m -g gateway -u ${GATEWAY_UID} -s /bin/bash gateway

COPY --from=builder --chown=gateway:gateway /tmp/gateway-ha.jar  /opt/gateway
COPY --from=builder --chown=gateway:gateway /tmp/gateway-ha-persistence-postgres.sql  /opt/gateway
COPY --from=builder --chown=gateway:gateway /usr/bin  /usr/local/bin
COPY templates/entrypoint.sh /opt/gateway/entrypoint.sh
COPY templates/gateway-ha-config.yml /opt/gateway/gateway-ha-config.yml

RUN chown -R gateway:gateway /opt/gateway && \
    chmod 0755 /opt/gateway/entrypoint.sh && \
    chmod 0644 /opt/gateway/gateway-ha-config.yml

USER gateway

ENTRYPOINT ["./entrypoint.sh"]
