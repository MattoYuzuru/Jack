#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
KUBECTL_BIN="${KUBECTL_BIN:-sudo k3s kubectl}"
NAMESPACE="jack"
IMAGE_OWNER="${JACK_IMAGE_OWNER:-mattoyuzuru}"
IMAGE_TAG="${JACK_IMAGE_TAG:-edge}"
FRONTEND_IMAGE="ghcr.io/${IMAGE_OWNER}/jack/frontend:${IMAGE_TAG}"
BACKEND_IMAGE="ghcr.io/${IMAGE_OWNER}/jack/backend:${IMAGE_TAG}"

POSTGRES_USER="${JACK_POSTGRES_USER:-}"
POSTGRES_DB="${JACK_POSTGRES_DB:-}"
POSTGRES_PASSWORD="${JACK_POSTGRES_PASSWORD:-}"
APP_HOST="${JACK_APP_HOST:-https://jack.keykomi.com}"
WEB_ALLOWED_ORIGINS="${JACK_WEB_ALLOWED_ORIGINS:-}"
PROCESSING_SESSION_SECRET="${JACK_PROCESSING_SESSION_SECRET:-}"

read_secret_key() {
  local key="$1"
  ${KUBECTL_BIN} -n "${NAMESPACE}" get secret jack-secrets \
    -o "jsonpath={.data.${key}}" 2>/dev/null | base64 --decode
}

if ! ${KUBECTL_BIN} get namespace "${NAMESPACE}" >/dev/null 2>&1; then
  ${KUBECTL_BIN} apply -f "${ROOT_DIR}/k8s/jack/namespace.yaml"
fi

if ${KUBECTL_BIN} -n "${NAMESPACE}" get secret jack-secrets >/dev/null 2>&1; then
  # Сохраняем существующие значения при повторном bootstrap: случайная ротация owner-signing
  # key инвалидировала бы все активные processing sessions без явного решения оператора.
  POSTGRES_USER="${POSTGRES_USER:-$(read_secret_key postgres-user)}"
  POSTGRES_DB="${POSTGRES_DB:-$(read_secret_key postgres-db)}"
  POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-$(read_secret_key postgres-password)}"
  WEB_ALLOWED_ORIGINS="${WEB_ALLOWED_ORIGINS:-$(read_secret_key web-allowed-origins)}"
  PROCESSING_SESSION_SECRET="${PROCESSING_SESSION_SECRET:-$(read_secret_key processing-session-secret)}"
fi

POSTGRES_USER="${POSTGRES_USER:-jack}"
POSTGRES_DB="${POSTGRES_DB:-jack}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-$(openssl rand -hex 24)}"
WEB_ALLOWED_ORIGINS="${WEB_ALLOWED_ORIGINS:-${APP_HOST}}"
PROCESSING_SESSION_SECRET="${PROCESSING_SESSION_SECRET:-$(openssl rand -hex 32)}"

${KUBECTL_BIN} -n "${NAMESPACE}" create secret generic jack-secrets \
  --from-literal=postgres-user="${POSTGRES_USER}" \
  --from-literal=postgres-password="${POSTGRES_PASSWORD}" \
  --from-literal=postgres-db="${POSTGRES_DB}" \
  --from-literal=web-allowed-origins="${WEB_ALLOWED_ORIGINS}" \
  --from-literal=processing-session-secret="${PROCESSING_SESSION_SECRET}" \
  --dry-run=client \
  -o yaml | ${KUBECTL_BIN} apply -f -

${KUBECTL_BIN} apply -k "${ROOT_DIR}/k8s/jack"
${KUBECTL_BIN} -n "${NAMESPACE}" set image deployment/backend backend="${BACKEND_IMAGE}"
${KUBECTL_BIN} -n "${NAMESPACE}" set image deployment/frontend frontend="${FRONTEND_IMAGE}"

${KUBECTL_BIN} -n "${NAMESPACE}" rollout status statefulset/postgres --timeout=180s
${KUBECTL_BIN} -n "${NAMESPACE}" rollout status deployment/backend --timeout=240s
${KUBECTL_BIN} -n "${NAMESPACE}" rollout status deployment/frontend --timeout=180s
${KUBECTL_BIN} -n "${NAMESPACE}" get ingress,svc,pods,pvc
