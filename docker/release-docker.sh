#!/bin/bash

set -eux

VERSION=$1
REPO=trinodb/trino-gateway
IMAGE=trino-gateway:$VERSION
TARGET=$REPO:$VERSION

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"

$SCRIPT_DIR/build.sh -r "$VERSION"

architectures=(amd64 arm64 ppc64le)

for arch in "${architectures[@]}"; do
    docker tag "$IMAGE-$arch" "$TARGET-$arch"
    docker push "$TARGET-$arch"
done

for name in "$TARGET" "$REPO:latest"; do
    docker manifest create "$name" "${architectures[@]/#/$TARGET-}"
    docker manifest push --purge "$name"
done
