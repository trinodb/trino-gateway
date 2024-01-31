#!/usr/bin/env bash

set -xeuo pipefail

usage() {
    cat <<EOF 1>&2
Usage: $0 [-h] [-a <ARCHITECTURES>] [-r <VERSION>]
Builds the Trino Gateway Docker image

-h       Display help
-a       Build the specified comma-separated architectures, defaults to amd64,arm64,ppc64le
-r       Build the specified Trino Gateway release version, downloads all required artifacts
-j       Build the Trino Gateway release with specified Temurin JDK release
EOF
}

# Retrieve the script directory.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "${SCRIPT_DIR}" || exit 2

SOURCE_DIR="${SCRIPT_DIR}/.."

ARCHITECTURES=(amd64 arm64 ppc64le)
TRINO_GATEWAY_VERSION=
JDK_VERSION=$(cat "${SOURCE_DIR}/.java-version")

while getopts ":a:h:r:j:" o; do
    case "${o}" in
        a)
            IFS=, read -ra ARCHITECTURES <<< "$OPTARG"
            ;;
        r)
            TRINO_GATEWAY_VERSION=${OPTARG}
            ;;
        h)
            usage
            exit 0
            ;;
        j)
            JDK_VERSION="${OPTARG}"
            ;;
        *)
            usage
            exit 1
            ;;
    esac
done
shift $((OPTIND - 1))

function check_environment() {
    if ! command -v jq &> /dev/null; then
        echo >&2 "Please install jq"
        exit 1
    fi
    if ! $(docker compose version &> /dev/null); then
        echo >&2 "Please install Docker Compose V2"
        exit 1
    fi
}

function temurin_jdk_link() {
  JDK_VERSION="${1}"
  ARCH="${2}"

  versionsUrl="https://api.adoptium.net/v3/info/release_names?heap_size=normal&image_type=jdk&os=linux&page=0&page_size=20&project=jdk&release_type=ga&semver=false&sort_method=DEFAULT&sort_order=ASC&vendor=eclipse&version=%28${JDK_VERSION}%2C%5D"
  if ! result=$(curl -fLs "$versionsUrl" -H 'accept: application/json'); then
    echo >&2 "Failed to fetch release names for JDK version [${JDK_VERSION}, ) from Temurin API : $result"
    exit 1
  fi

  if ! RELEASE_NAME=$(echo "$result" | jq -er '.releases[]' | grep "${JDK_VERSION}" | head -n 1); then
    echo >&2 "Failed to determine release name: ${RELEASE_NAME}"
    exit 1
  fi

  case "${ARCH}" in
    arm64)
      echo "https://api.adoptium.net/v3/binary/version/${RELEASE_NAME}/linux/aarch64/jdk/hotspot/normal/eclipse?project=jdk"
    ;;
    amd64)
      echo "https://api.adoptium.net/v3/binary/version/${RELEASE_NAME}/linux/x64/jdk/hotspot/normal/eclipse?project=jdk"
    ;;
    ppc64le)
      echo "https://api.adoptium.net/v3/binary/version/${RELEASE_NAME}/linux/ppc64le/jdk/hotspot/normal/eclipse?project=jdk"
    ;;
  *)
    echo "${ARCH} is not supported for Docker image"
    exit 1
    ;;
  esac
}

check_environment

if [ -n "$TRINO_GATEWAY_VERSION" ]; then
    echo "🎣 Downloading gateway server artifact for release version ${TRINO_GATEWAY_VERSION}"
    "${SOURCE_DIR}/mvnw" -C dependency:get -Dtransitive=false -Dartifact="io.trino.gateway:gateway-ha:${TRINO_GATEWAY_VERSION}:jar:jar-with-dependencies"
    local_repo=$("${SOURCE_DIR}/mvnw" -B help:evaluate -Dexpression=settings.localRepository -q -DforceStdout)
    trino_gateway_ha="$local_repo/io/trino/gateway/gateway-ha/${TRINO_GATEWAY_VERSION}/gateway-ha-${TRINO_GATEWAY_VERSION}-jar-with-dependencies.jar"
    chmod +x "$trino_gateway_ha"
else
    TRINO_GATEWAY_VERSION=$("${SOURCE_DIR}/mvnw" -f "${SOURCE_DIR}/pom.xml" --quiet help:evaluate -Dexpression=project.version -DforceStdout)
    echo "🎯 Using currently built artifacts from the gateway-ha module and version ${TRINO_GATEWAY_VERSION}"
    trino_gateway_ha="${SOURCE_DIR}/gateway-ha/target/gateway-ha-${TRINO_GATEWAY_VERSION}-jar-with-dependencies.jar"
fi

echo "🧱 Preparing the image build context directory"
WORK_DIR="$(mktemp -d)"
GATEWAY_WORK_DIR="${WORK_DIR}/gateway-ha"
mkdir "${GATEWAY_WORK_DIR}"
cp "$trino_gateway_ha" "${GATEWAY_WORK_DIR}"
cp -R bin "${GATEWAY_WORK_DIR}"
cp "${SCRIPT_DIR}/Dockerfile" "${WORK_DIR}"

TAG_PREFIX="trino-gateway:${TRINO_GATEWAY_VERSION}"

TRINO_GATEWAY_BASE_IMAGE=${TRINO_GATEWAY_BASE_IMAGE:-'registry.access.redhat.com/ubi9/ubi-minimal:latest'}

for arch in "${ARCHITECTURES[@]}"; do
    echo "🫙  Building the image for $arch with JDK ${JDK_VERSION}"
    DOCKER_BUILDKIT=1 \
    docker build \
        "${WORK_DIR}" \
        --pull \
        --build-arg JDK_VERSION="${JDK_VERSION}" \
        --build-arg JDK_DOWNLOAD_LINK="$(temurin_jdk_link "${JDK_VERSION}" "${arch}")" \
        --build-arg TRINO_GATEWAY_VERSION="${TRINO_GATEWAY_VERSION}" \
        --build-arg TRINO_GATEWAY_BASE_IMAGE="${TRINO_GATEWAY_BASE_IMAGE}" \
        --platform "linux/$arch" \
        -f Dockerfile \
        -t "${TAG_PREFIX}-$arch"
done

echo "🧹 Cleaning up the build context directory"
rm -r "${WORK_DIR}"

echo "🏃 Testing built images"
source container-test.sh

for arch in "${ARCHITECTURES[@]}"; do
    # TODO: remove when https://github.com/multiarch/qemu-user-static/issues/128 is fixed
    if [[ "$arch" != "ppc64le" ]]; then
        test_container "${TAG_PREFIX}-$arch" "linux/$arch" "${SCRIPT_DIR}/minimal-compose.yml" "gateway" "postgres"
    fi
    docker image inspect -f '🚀 Built {{.RepoTags}} {{.Id}}' "${TAG_PREFIX}-$arch"
done
