#!/usr/bin/env sh

mkdir -p "$HOME"/key/jwt
cd "$HOME"/key/jwt || exit 1

if [ -f jwt_private.pem ] && [ -f jwt_public.pem ]; then
    echo "JWT keys already exist. Skipping generation."
    exit 0
fi

openssl genrsa -out jwt_private.pem 4096 || exit 1

openssl rsa -in jwt_private.pem -pubout -out jwt_public.pem || exit 1
chmod 600 jwt_private.pem || exit 1
chmod 644 jwt_public.pem || exit 1
