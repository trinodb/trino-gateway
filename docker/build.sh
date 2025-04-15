#!/usr/bin/env bash

set -euo pipefail

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
JDK_RELEASE_NAME=$(cat "${SOURCE_DIR}/.java-version")
# necessary to allow version parsing from the pom file
MVNW_VERBOSE=false

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
            JDK_RELEASE_NAME="${OPTARG}"
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
  JDK_RELEASE_NAME="${1}"
  ARCH="${2}"

  case "${ARCH}" in
    arm64)
      echo "https://api.adoptium.net/v3/binary/version/${JDK_RELEASE_NAME}/linux/aarch64/jdk/hotspot/normal/eclipse?project=jdk"
    ;;
    amd64)
      echo "https://api.adoptium.net/v3/binary/version/${JDK_RELEASE_NAME}/linux/x64/jdk/hotspot/normal/eclipse?project=jdk"
    ;;
    ppc64le)
      echo "https://api.adoptium.net/v3/binary/version/${JDK_RELEASE_NAME}/linux/ppc64le/jdk/hotspot/normal/eclipse?project=jdk"
    ;;
  *)
    echo "${ARCH} is not supported for Docker image"
    exit 1
    ;;
  esac
}

check_environment

if [ -n "$TRINO_GATEWAY_VERSION" ]; then
    echo "🎣 Downloading Trino Gateway server artifact for release version ${TRINO_GATEWAY_VERSION}"
    "${SOURCE_DIR}/mvnw" -C dependency:get -Dtransitive=false -Dartifact="io.trino.gateway:trino-gateway-server:${TRINO_GATEWAY_VERSION}:tar.gz"
    local_repo=$("${SOURCE_DIR}/mvnw" -B help:evaluate -Dexpression=settings.localRepository -q -DforceStdout)
    trino_gateway_ha="$local_repo/io/trino/gateway/trino-gateway-server/${TRINO_GATEWAY_VERSION}/trino-gateway-server-${TRINO_GATEWAY_VERSION}.tar.gz"
    chmod +x "$trino_gateway_ha"
else
    TRINO_GATEWAY_VERSION=$("${SOURCE_DIR}/mvnw" -f "${SOURCE_DIR}/pom.xml" --quiet help:evaluate -Dexpression=project.version -DforceStdout)
    echo "🎯 Using currently built artifacts with version ${TRINO_GATEWAY_VERSION}"
    trino_gateway_ha="${SOURCE_DIR}/trino-gateway-server/target/trino-gateway-server-${TRINO_GATEWAY_VERSION}.tar.gz"
fi

echo "🧱 Preparing the image build context directory"
WORK_DIR="$(mktemp -d)"
cp "$trino_gateway_ha" "${WORK_DIR}/"
tar -C "${WORK_DIR}" -xzf "${WORK_DIR}/trino-gateway-server-${TRINO_GATEWAY_VERSION}.tar.gz"
rm "${WORK_DIR}/trino-gateway-server-${TRINO_GATEWAY_VERSION}.tar.gz"
cp -R bin "${WORK_DIR}/trino-gateway-server-${TRINO_GATEWAY_VERSION}"
cp -R default "${WORK_DIR}/"

TAG_PREFIX="trino-gateway:${TRINO_GATEWAY_VERSION}"
#version file is used by the Helm chart test
echo "${TRINO_GATEWAY_VERSION}" > "${SOURCE_DIR}"/trino-gateway-version.txt

TRINO_GATEWAY_BASE_IMAGE=${TRINO_GATEWAY_BASE_IMAGE:-'redhat/ubi10-micro:latest'}
TRINO_GATEWAY_BUILD_IMAGE=${TRINO_GATEWAY_BUILD_IMAGE:-'redhat/ubi10:latest'}

for arch in "${ARCHITECTURES[@]}"; do
    echo "🫙  Building the image for $arch with Temurin JDK release ${JDK_RELEASE_NAME}"
    DOCKER_BUILDKIT=1 \
    docker build \
        "${WORK_DIR}" \
        --pull \
        --build-arg JDK_RELEASE_NAME="${JDK_RELEASE_NAME}" \
        --build-arg JDK_DOWNLOAD_LINK="$(temurin_jdk_link "jdk-${JDK_RELEASE_NAME}" "${arch}")" \
        --build-arg TRINO_GATEWAY_BASE_IMAGE="${TRINO_GATEWAY_BASE_IMAGE}" \
        --build-arg TRINO_GATEWAY_BUILD_IMAGE="${TRINO_GATEWAY_BUILD_IMAGE}" \
        --platform "linux/$arch" \
        -f Dockerfile \
        -t "${TAG_PREFIX}-$arch" \
        --build-arg "TRINO_GATEWAY_VERSION=${TRINO_GATEWAY_VERSION}"
done

echo "🧹 Cleaning up the build context directory"
rm -r "${WORK_DIR}"

echo "🏃 Testing built images"
source container-test.sh

for arch in "${ARCHITECTURES[@]}"; do
    # TODO: remove when https://github.com/multiarch/qemu-user-static/issues/128 is fixed
    if [[ "$arch" != "ppc64le" ]]; then
        test_container "${TAG_PREFIX}-$arch" "linux/$arch" "${SCRIPT_DIR}/docker-compose.yml" "trino-gateway" "postgres"
    fi
    docker image inspect -f '🚀 Built {{.RepoTags}} {{.Id}}' "${TAG_PREFIX}-$arch"
done
