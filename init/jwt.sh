#!/usr/bin/env bash
set -euo pipefail

KEY_DIR="${HOME}/.keys/jwt"

mkdir -p -m 700 "${KEY_DIR}"

(
    umask 077
    openssl genpkey -algorithm RSA -out "${KEY_DIR}/private.pem" -pkeyopt rsa_keygen_bits:4096
)

openssl rsa -pubout -in "${KEY_DIR}/private.pem" -out "${KEY_DIR}/public.pem"

chmod 644 "${KEY_DIR}/public.pem"
