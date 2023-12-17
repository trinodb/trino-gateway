#!/usr/bin/env bash

function cleanup {
    docker compose -f "${COMPOSE_PATH}" down
}

function test_gateway_starts {
    CONTAINER_ID=
    POSTGRES_CONTAINER_ID=
    trap cleanup EXIT

    # Ensure compose state is starting fresh before the test
    cleanup

    local CONTAINER_NAME=$1
    local PLATFORM=$2
    local COMPOSE_PATH=$3
    local COMPOSE_GATEWAY_NAME=$4
    local COMPOSE_POSTGRES_NAME=$5

    set +e

    # Ensure the platform specific image has been pulled locally for Postgres
    DOCKER_DEFAULT_PLATFORM=${PLATFORM} \
		docker compose -f "${COMPOSE_PATH}" \
		pull ${COMPOSE_POSTGRES_NAME}

    # We need to spin up dependencies for the container
    # Timeout is built into the compose file in the form of healthcheck retry limits
    TRINO_GATEWAY_IMAGE=${CONTAINER_NAME} \
		DOCKER_DEFAULT_PLATFORM=${PLATFORM} \
		docker compose -f "${COMPOSE_PATH}" \
        up --wait \
        ${COMPOSE_POSTGRES_NAME} ${COMPOSE_GATEWAY_NAME}
    local COMPOSE_UP_RES=$?
    POSTGRES_CONTAINER_ID=$(docker compose -f "${COMPOSE_PATH}" ps -q ${COMPOSE_POSTGRES_NAME})
    CONTAINER_ID=$(docker compose -f "${COMPOSE_PATH}" ps -q ${COMPOSE_GATEWAY_NAME})
    if [ ${COMPOSE_UP_RES} -ne 0 ]; then
        echo "🚨 Took too long waiting for Trino Gateway container to become healthy" >&2
        echo "Logs from ${CONTAINER_ID} follow..."
        docker logs "${CONTAINER_ID}"

        set -e
        cleanup
        trap - EXIT
        return 1
    fi

    if ! RESULT=$(curl --fail localhost:8080/api/public/backends 2>/dev/null); then
        echo "🚨 Failed to execute a query after Trino Gateway container started" >&2
    fi

    set -e

    cleanup
    trap - EXIT

    if ! [[ ${RESULT} == '[]' ]]; then
        echo "🚨 Test query didn't return expected result of 0 backends ([]): ${RESULT}" >&2
        return 1
    fi

    return 0
}

function test_javahome {
    local CONTAINER_NAME=$1
    local PLATFORM=$2
    # Check if JAVA_HOME works
    docker run --rm --platform "${PLATFORM}" "${CONTAINER_NAME}" \
        /bin/bash -c '$JAVA_HOME/bin/java -version' &>/dev/null

    [[ $? == "0" ]]
}

function test_container {
    local CONTAINER_NAME=$1
    local PLATFORM=$2
    local COMPOSE_PATH=$3
    local COMPOSE_GATEWAY_NAME=$4
    local COMPOSE_POSTGRES_NAME=$5
    echo "🐢 Validating ${CONTAINER_NAME} on platform ${PLATFORM}..."
    test_javahome "${CONTAINER_NAME}" "${PLATFORM}"
    test_gateway_starts "${CONTAINER_NAME}" "${PLATFORM}" "${COMPOSE_PATH}" "${COMPOSE_GATEWAY_NAME}" "${COMPOSE_POSTGRES_NAME}"
    echo "🎉 Validated ${CONTAINER_NAME} on platform ${PLATFORM}"
}
