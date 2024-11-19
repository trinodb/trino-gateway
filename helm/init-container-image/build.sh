#!/usr/bin/env bash

set -euo pipefail

usage() {
    cat <<EOF 1>&2
Usage: $0 [-h] [-a <ARCHITECTURES>] [-r <VERSION>]
Builds the Trino Gateway Init Docker image

-h       Display help
-a       Build the specified comma-separated architectures, defaults to amd64,arm64,ppc64le
EOF
}

# Retrieve the script directory.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "${SCRIPT_DIR}" || exit 2

SOURCE_DIR="${SCRIPT_DIR}/../.."
MVNW_VERBOSE=false

ARCHITECTURES=(amd64 arm64 ppc64le)
TRINO_GATEWAY_VERSION=$("${SOURCE_DIR}/mvnw" -f "${SOURCE_DIR}/pom.xml" --quiet help:evaluate -Dexpression=project.version -DforceStdout)

while getopts ":a:h:r:j:" o; do
    case "${o}" in
        a)
            IFS=, read -ra ARCHITECTURES <<< "$OPTARG"
            ;;
        h)
            usage
            exit 0
            ;;
        *)
            usage
            exit 1
            ;;
    esac
done
shift $((OPTIND - 1))

function test_image() {
    IMAGE=$1
    PLATFORM=$2

    echo "Testing ${IMAGE}"
    USERNAME=usr
    PASSWORD=secret
    cat > env_test.yaml << EOF
dataStore:
  #This stores the URLs of backend Trino servers and query history
  jdbcUrl: jdbc:postgresql://env:5432/gateway
  user: \${ENV:USERNAME}
  password: "\${ENV:PASSWORD}"
EOF
    cat > env_test_expected.yaml << EOF
dataStore:
  #This stores the URLs of backend Trino servers and query history
  jdbcUrl: jdbc:postgresql://env:5432/gateway
  user: ${USERNAME}
  password: "${PASSWORD}"
EOF

    touch output.yaml
    docker run --platform "${PLATFORM}" --rm -e USERNAME="${USERNAME}" -e PASSWORD="${PASSWORD}" \
    --user $(id -u):$(id -g) \
    -v "${PWD}"/env_test.yaml:/tmp/env_test.yaml \
    -v "${PWD}"/output.yaml:/tmp/output.yaml:rw \
    "${IMAGE}" /tmp/env_test.yaml /tmp/output.yaml
   if [[ -n "$(diff output.yaml env_test_expected.yaml)" ]]; then
       echo "Interpolated output does not match expected:"
       cat  env_test.yaml
       exit 100
   fi
   rm  output.yaml env_test_expected.yaml env_test.yaml
   echo "Test successful"
}

echo "🧱 Preparing the image build context directory"
WORK_DIR="$(mktemp -d)"
cp -r bin "${WORK_DIR}"

TAG_PREFIX="trino-gateway-init:${TRINO_GATEWAY_VERSION}"

for arch in "${ARCHITECTURES[@]}"; do
    echo "🫙  Building the image for $arch"
    DOCKER_BUILDKIT=1 \
    docker build \
        "${WORK_DIR}" \
        --pull \
        --platform "linux/$arch" \
        -f Dockerfile \
        -t "${TAG_PREFIX}-$arch"
done

echo "🧹 Cleaning up the build context directory"
rm -r "${WORK_DIR}"

echo "🏃 Testing built images"
for arch in "${ARCHITECTURES[@]}"; do
    docker image inspect -f '🚀 Built {{.RepoTags}} {{.Id}}' "${TAG_PREFIX}-$arch"
    # TODO: remove when https://github.com/multiarch/qemu-user-static/issues/128 is fixed
    if [[ "${arch}" != "ppc64le" ]]; then
        test_image "${TAG_PREFIX}-${arch}" "linux/${arch}"
    fi
done
