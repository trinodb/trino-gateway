#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# syntax=docker/dockerfile:1
ARG TRINO_GATEWAY_BASE_IMAGE
ARG TRINO_GATEWAY_BUILD_IMAGE

FROM --platform=$BUILDPLATFORM ${TRINO_GATEWAY_BUILD_IMAGE} AS jdk-download
ARG JDK_DOWNLOAD_LINK
ARG JDK_RELEASE_NAME
ENV JAVA_HOME="/usr/lib/jvm/jdk-${JDK_RELEASE_NAME}"

RUN \
    set -xeuo pipefail && \
    dnf install -y tar gzip && \
    # Install JDK from the provided archive link \
    echo "Downloading JDK from ${JDK_DOWNLOAD_LINK}" && \
    mkdir -p "${JAVA_HOME}" && \
    curl -#LfS --retry 8 "${JDK_DOWNLOAD_LINK}" -o /jdktarfile && \
    tar -zxf /jdktarfile --strip 1 -C "${JAVA_HOME}" && \
    echo "Done"

FROM ${TRINO_GATEWAY_BUILD_IMAGE} AS packages

RUN \
    set -xeuo pipefail && \
    mkdir -p /tmp/overlay/usr/libexec/ && \
    touch /tmp/overlay/usr/libexec/grepconf.sh && \
    chmod +x /tmp/overlay/usr/libexec/grepconf.sh && \
    dnf update -y && \
    dnf install --installroot /tmp/overlay --setopt install_weak_deps=false --nodocs -y \
      less \
      zlib `#required by java` \
      curl-minimal grep `# required by health-check` \
      shadow-utils `# required by useradd` \
      tar `# required to support kubectl cp` && \
      rm -rf /tmp/overlay/var/cache/*

FROM ${TRINO_GATEWAY_BASE_IMAGE}
WORKDIR /usr/lib/trino-gateway

ARG JDK_RELEASE_NAME
ENV JAVA_HOME="/usr/lib/jvm/jdk-${JDK_RELEASE_NAME}"
ENV PATH=$PATH:$JAVA_HOME/bin
COPY --from=jdk-download $JAVA_HOME $JAVA_HOME
COPY --from=packages /tmp/overlay /

RUN \
    set -xeu && \
    groupadd trino --gid 1000 && \
    useradd trino --uid 1000 --gid 1000 --create-home && \
    mkdir -p /usr/lib/trino-gateway /etc/trino-gateway && \
    chown -R "trino:trino" /usr/lib/trino-gateway /etc/trino-gateway

COPY --chown=trino:trino gateway-ha /usr/lib/trino-gateway

EXPOSE 8080
USER trino:trino
CMD java -jar /usr/lib/trino-gateway/gateway-ha-jar-with-dependencies.jar "/etc/trino-gateway/config.yaml"

HEALTHCHECK --interval=10s --timeout=5s --start-period=10s \
  CMD /usr/lib/trino-gateway/bin/health-check
