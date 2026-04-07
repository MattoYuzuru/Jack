#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
KUBECTL_BIN="${KUBECTL_BIN:-sudo k3s kubectl}"
NAMESPACE="jack"
IMAGE_OWNER="${JACK_IMAGE_OWNER:-mattoyuzuru}"
IMAGE_TAG="${JACK_IMAGE_TAG:-edge}"
FRONTEND_IMAGE="ghcr.io/${IMAGE_OWNER}/jack/frontend:${IMAGE_TAG}"
BACKEND_IMAGE="ghcr.io/${IMAGE_OWNER}/jack/backend:${IMAGE_TAG}"

POSTGRES_USER="${JACK_POSTGRES_USER:-jack}"
POSTGRES_DB="${JACK_POSTGRES_DB:-jack}"
POSTGRES_PASSWORD="${JACK_POSTGRES_PASSWORD:-}"
APP_HOST="${JACK_APP_HOST:-https://jack.keykomi.com}"
WEB_ALLOWED_ORIGINS="${JACK_WEB_ALLOWED_ORIGINS:-${APP_HOST}}"

if ! ${KUBECTL_BIN} get namespace "${NAMESPACE}" >/dev/null 2>&1; then
  ${KUBECTL_BIN} apply -f "${ROOT_DIR}/k8s/jack/namespace.yaml"
fi

if ${KUBECTL_BIN} -n "${NAMESPACE}" get secret jack-secrets >/dev/null 2>&1; then
  echo "Secret jack-secrets already exists in namespace ${NAMESPACE}, reusing it."
else
  if [[ -z "${POSTGRES_PASSWORD}" ]]; then
    POSTGRES_PASSWORD="$(openssl rand -hex 24)"
  fi

  ${KUBECTL_BIN} -n "${NAMESPACE}" create secret generic jack-secrets \
    --from-literal=postgres-user="${POSTGRES_USER}" \
    --from-literal=postgres-password="${POSTGRES_PASSWORD}" \
    --from-literal=postgres-db="${POSTGRES_DB}" \
    --from-literal=web-allowed-origins="${WEB_ALLOWED_ORIGINS}" \
    --dry-run=client \
    -o yaml | ${KUBECTL_BIN} apply -f -
fi

${KUBECTL_BIN} apply -k "${ROOT_DIR}/k8s/jack"
${KUBECTL_BIN} -n "${NAMESPACE}" set image deployment/backend backend="${BACKEND_IMAGE}"
${KUBECTL_BIN} -n "${NAMESPACE}" set image deployment/frontend frontend="${FRONTEND_IMAGE}"

${KUBECTL_BIN} -n "${NAMESPACE}" rollout status statefulset/postgres --timeout=180s
${KUBECTL_BIN} -n "${NAMESPACE}" rollout status deployment/backend --timeout=240s
${KUBECTL_BIN} -n "${NAMESPACE}" rollout status deployment/frontend --timeout=180s
${KUBECTL_BIN} -n "${NAMESPACE}" get ingress,svc,pods,pvc
