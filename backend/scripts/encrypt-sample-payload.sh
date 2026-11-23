#!/usr/bin/env bash
set -euo pipefail

payload='{"name":"Mehul Nagar","country":"IN","verification_level":"sandbox"}'
printf '%s' "$payload" > /tmp/secure-payload-plain.json
openssl pkeyutl -encrypt -pubin -inkey docs/mock-public-key.pem \
  -in /tmp/secure-payload-plain.json \
  -pkeyopt rsa_padding_mode:oaep \
  -pkeyopt rsa_oaep_md:sha256 \
  -pkeyopt rsa_mgf1_md:sha256 \
  -out /tmp/secure-payload-ciphertext.bin
base64 < /tmp/secure-payload-ciphertext.bin | tr -d '\n'
printf '\n'
