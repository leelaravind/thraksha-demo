#!/usr/bin/env bash
#
# sign_rulepack.sh — re-sign the bundled rulepack after editing it.
#
# DEVELOPER TOOL. This is not application code and is never compiled into the APK.
#
# The app refuses to load a rulepack whose signature does not verify (RulepackLoader
# throws, and SecurityAuditEngine surfaces the failure instead of scanning). So editing
# rulepack.json WITHOUT running this script does not degrade detection quietly — it
# stops it, loudly. Run this every time rulepack.json changes.
#
# Usage:
#   bash tools/sign_rulepack.sh
#
# Requires: openssl on PATH, and keys/rulepack_private.pem present locally.
#
# SECURITY NOTES
#   * keys/ and *.pem are gitignored. The private key MUST NOT be committed and MUST NOT
#     be placed in app/src/main/assets/. Only these three files ship in the APK:
#         rulepack.json, rulepack.sig, rulepack_public.key
#   * .gitattributes marks all three assets `-text`. Never remove that: CRLF conversion
#     on a Windows checkout rewrites the signed bytes and breaks verification.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSETS="$REPO_ROOT/app/src/main/assets"
PRIVATE_KEY="$REPO_ROOT/keys/rulepack_private.pem"
JSON="$ASSETS/rulepack.json"
SIG="$ASSETS/rulepack.sig"
PUB="$ASSETS/rulepack_public.key"

if [ ! -f "$PRIVATE_KEY" ]; then
  echo "ERROR: $PRIVATE_KEY not found." >&2
  echo "The signing key is intentionally not in version control. Restore it locally." >&2
  exit 1
fi

if [ ! -f "$JSON" ]; then
  echo "ERROR: $JSON not found." >&2
  exit 1
fi

echo "Signing:  $JSON"

# RSA-2048 / SHA-256 over the EXACT asset bytes, base64-encoded on a single line.
# This must match RulepackVerifier: Signature.getInstance("SHA256withRSA") over the same
# bytes, with the signature read through java.util.Base64.
openssl dgst -sha256 -sign "$PRIVATE_KEY" -out "$SIG.tmp" "$JSON"
openssl base64 -A -in "$SIG.tmp" -out "$SIG"
rm -f "$SIG.tmp"

# Re-derive the public key from the private key so the two can never drift apart.
openssl rsa -in "$PRIVATE_KEY" -pubout -outform DER 2>/dev/null \
  | openssl base64 -A -out "$PUB"

echo "Wrote:    $SIG"
echo "Wrote:    $PUB"

# Verify immediately, exactly the way the app will, so a bad signing run fails here
# rather than on the device. Temp files rather than process substitution — this script
# also runs under Git Bash on Windows, where <(...) is unreliable.
TMP_PEM="$(mktemp)"
TMP_BIN="$(mktemp)"
trap 'rm -f "$TMP_PEM" "$TMP_BIN"' EXIT

openssl rsa -in "$PRIVATE_KEY" -pubout -out "$TMP_PEM" 2>/dev/null
openssl base64 -d -A -in "$SIG" -out "$TMP_BIN"

if openssl dgst -sha256 -verify "$TMP_PEM" -signature "$TMP_BIN" "$JSON" > /dev/null 2>&1; then
  echo "Verified: signature is valid over the current rulepack.json"
else
  echo "ERROR: verification failed immediately after signing." >&2
  exit 1
fi

RULE_COUNT=$(grep -c '"id"[[:space:]]*:' "$JSON" || true)
echo "Rules:    $RULE_COUNT"
echo
echo "Done. Rebuild the app so the new assets are packaged:"
echo "  ./gradlew :app:assembleDebug"
