#!/usr/bin/env bash
# Regenerates evidence/receipt_fixture/receipt_signed.json — build plan §7.2, §7.4.
#
# WHY THIS EXISTS
#
# §7.2's signature is produced by the Android Keystore on the phone, so the
# CLI's signature check would otherwise go untested until a phone, a keystore
# and a real session all existed at once — which is late to discover that the
# two sides disagree about what "sign the head" means (the 64 hex characters,
# or the 32 bytes they spell). A JDK-generated EC P-256 key signs exactly the
# same way, so the path is exercised on the laptop today.
#
# The private key is thrown away at the end. What is committed is the signed
# receipt and the certificate inside it, which together let anybody re-run the
# check: the public key is in the file, and the signature either matches the
# head or it does not.
#
# The fixture it signs is receipt.json, which :domain:receiptFixture writes
# deterministically. The signature itself is NOT deterministic — ECDSA uses a
# random nonce, so every run produces different bytes for the same head. That is
# correct and expected; it just means re-running this makes a real diff.
set -euo pipefail

cd "$(dirname "$0")/.."

KEYSTORE="$(mktemp -t vaakku-receipt-XXXXXX).p12"
PASSWORD="fixture-only-not-a-secret"

cleanup() {
    # The key is scratch by construction, but a private key sitting in a temp
    # directory afterwards is the kind of thing that survives for months.
    rm -f "$KEYSTORE"
}
trap cleanup EXIT

echo "Generating a throwaway EC P-256 key…"
keytool -genkeypair \
    -alias vaakku_receipt_fixture \
    -keyalg EC \
    -groupname secp256r1 \
    -sigalg SHA256withECDSA \
    -keystore "$KEYSTORE" \
    -storetype PKCS12 \
    -storepass "$PASSWORD" \
    -keypass "$PASSWORD" \
    -validity 3650 \
    -dname "CN=VAAKKU receipt fixture, OU=not a device key, O=VAAKKU" \
    2>&1 | grep -v "^$" || true

echo "Signing the fixture receipt…"
./gradlew :domain:receiptFixture -q \
    -Pkeystore="$KEYSTORE" \
    -PkeystorePassword="$PASSWORD"

echo
echo "Checking it with the CLI's own signature path…"
node tools/packet-cli/index.js --in evidence/receipt_fixture/receipt_signed.json
