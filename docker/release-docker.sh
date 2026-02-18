#!/bin/bash

set -eux

# Check crane is available
if ! command -v crane &> /dev/null; then
    echo "Error: crane is required but not installed."
    echo "Install with: brew install crane"
    exit 1
fi

VERSION=$1
REPO=trinodb/trino-gateway
IMAGE=trino-gateway:$VERSION
TARGET=$REPO:$VERSION

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"

$SCRIPT_DIR/build.sh -r "$VERSION"

architectures=(amd64 arm64 ppc64le)

# Create temp directory for digest references
REFS_DIR=$(mktemp -d)
trap "rm -rf $REFS_DIR" EXIT

# Push each architecture image by digest using crane
# --image-refs captures the digest without creating a named tag
for arch in "${architectures[@]}"; do
    docker save "$IMAGE-$arch" -o "$REFS_DIR/$arch.tar"
    crane push "$REFS_DIR/$arch.tar" "$REPO" --image-refs "$REFS_DIR/$arch.ref"
done

# Create multi-arch manifests from the digest references
crane index append -t "$TARGET" \
    -m "$(cat $REFS_DIR/amd64.ref)" \
    -m "$(cat $REFS_DIR/arm64.ref)" \
    -m "$(cat $REFS_DIR/ppc64le.ref)"

crane index append -t "$REPO:latest" \
    -m "$(cat $REFS_DIR/amd64.ref)" \
    -m "$(cat $REFS_DIR/arm64.ref)" \
    -m "$(cat $REFS_DIR/ppc64le.ref)"
