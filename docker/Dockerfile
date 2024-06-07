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
FROM ${TRINO_GATEWAY_BASE_IMAGE} AS jdk-download
ARG JDK_DOWNLOAD_LINK
ARG JDK_VERSION
ENV JAVA_HOME="/usr/lib/jvm/jdk-${JDK_VERSION}"

RUN \
    set -xeuo pipefail && \
    microdnf install -y tar gzip && \
    # Install JDK from the provided archive link \
    echo "Downloading JDK from ${JDK_DOWNLOAD_LINK}" && \
    mkdir -p "${JAVA_HOME}" && \
    curl -#LfS "${JDK_DOWNLOAD_LINK}" | tar -zx --strip 1 -C "${JAVA_HOME}"

FROM ${TRINO_GATEWAY_BASE_IMAGE}
WORKDIR /opt/trino

ARG JDK_VERSION
ENV JAVA_HOME="/usr/lib/jvm/jdk-${JDK_VERSION}"
ENV PATH=$PATH:$JAVA_HOME/bin
COPY --from=jdk-download $JAVA_HOME $JAVA_HOME

RUN \
    set -xeu && \
    microdnf update -y && \
    microdnf install -y tar less shadow-utils && \
    groupadd trino --gid 1000 && \
    useradd trino --uid 1000 --gid 1000 --create-home && \
    mkdir -p /usr/lib/trino && \
    chown -R "trino:trino" /usr/lib/trino /opt/trino

COPY --chown=trino:trino gateway-ha /usr/lib/trino

EXPOSE 8080
USER trino:trino
CMD java -jar /usr/lib/trino/gateway-ha-jar-with-dependencies.jar "/opt/trino/gateway-ha-config.yml"

HEALTHCHECK --interval=10s --timeout=5s --start-period=10s \
  CMD /usr/lib/trino/bin/health-check
