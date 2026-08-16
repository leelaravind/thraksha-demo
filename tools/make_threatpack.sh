#!/usr/bin/env bash
#
# make_threatpack.sh — (re)generate and sign the bundled demo threat pack.
#
# DEVELOPER TOOL. Not application code; never compiled into the APK.
#
# The threat pack carries indicators for the CONTROLLED, BENIGN VillainCaller sample:
#   * PACKAGE_NAME          com.thraksha.demo.villaincaller
#   * SIGNING_CERT_SHA256   digest of villaincaller's committed demo signing cert
#   * BASE_APK_SHA256       digest of the currently built villaincaller-debug.apk
#
# Because BASE_APK_SHA256 changes on EVERY villaincaller rebuild, run this script after
# building villaincaller and before building Guardian:
#
#   ./gradlew :villaincaller:assembleDebug
#   bash tools/make_threatpack.sh
#   ./gradlew :app:assembleDebug
#
# The app fails CLOSED on a bad signature: ThreatPackLoader reports the pillar
# unavailable (visibly) rather than scanning with unverified data.
#
# Requires: openssl + a JDK keytool on PATH (or Android Studio's JBR), and
# keys/threatpack_private.pem present locally (gitignored; never in assets).

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSETS="$REPO_ROOT/app/src/main/assets"
PRIVATE_KEY="$REPO_ROOT/keys/threatpack_private.pem"
VILLAIN_APK="$REPO_ROOT/villaincaller/build/outputs/apk/debug/villaincaller-debug.apk"
JSON="$ASSETS/threatpack.json"
SIG="$ASSETS/threatpack.sig"
PUB="$ASSETS/threatpack_public.key"

[ -f "$PRIVATE_KEY" ] || { echo "ERROR: $PRIVATE_KEY not found (gitignored by design; restore it locally)." >&2; exit 1; }
[ -f "$VILLAIN_APK" ] || { echo "ERROR: $VILLAIN_APK not built. Run: ./gradlew :villaincaller:assembleDebug" >&2; exit 1; }

# apksigner reads the digest of the certificate actually embedded in the APK — the same
# bytes Android's SigningInfo exposes to the on-device fingerprinter.
APKSIGNER="${APKSIGNER:-}"
if [ -z "$APKSIGNER" ]; then
  BT_DIR="$LOCALAPPDATA/Android/Sdk/build-tools"
  APKSIGNER="$BT_DIR/$(ls "$BT_DIR" | sort -V | tail -1)/apksigner.bat"
fi
[ -f "$APKSIGNER" ] || { echo "ERROR: apksigner not found; set APKSIGNER=..." >&2; exit 1; }

# --- fingerprints of the controlled sample ---------------------------------------
APK_SHA256=$(openssl dgst -sha256 -r "$VILLAIN_APK" | cut -d' ' -f1)

# Signing certificate SHA-256 (lowercase hex, no colons) from the APK itself.
CERT_SHA256=$("$APKSIGNER" verify --print-certs "$VILLAIN_APK" 2>/dev/null \
  | grep -m1 "certificate SHA-256 digest:" | sed 's/.*digest: //' | tr -d ' \r' | tr 'A-F' 'a-f')

[ ${#APK_SHA256} -eq 64 ] || { echo "ERROR: bad APK digest" >&2; exit 1; }
[ ${#CERT_SHA256} -eq 64 ] || { echo "ERROR: bad cert digest ('$CERT_SHA256')" >&2; exit 1; }

echo "VillainCaller base APK SHA-256:  $APK_SHA256"
echo "VillainCaller signer SHA-256:    $CERT_SHA256"

# --- the pack ---------------------------------------------------------------------
# All records are classified DEMO_TEST_THREAT: the sample is benign and controlled.
# The pack also carries one disabled indicator and one non-matching hash as negative
# controls (proving disabled records never match and the scanner is not flag-everything),
# and one FUTURE-typed record (representable, never evaluated).
GENERATED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
cat > "$JSON" << EOF
{
  "schemaVersion": 1,
  "packVersion": 2,
  "generatedAt": "$GENERATED_AT",
  "indicators": [
    {
      "id": "demo-villain-package",
      "type": "PACKAGE_NAME",
      "value": "com.thraksha.demo.villaincaller",
      "classification": "DEMO_TEST_THREAT",
      "severity": "HIGH",
      "description": "Package name matches a demo test-threat record in the signed offline threat pack. This is the controlled benign VillainCaller sample, not real malware.",
      "source": "thraksha-demo-pack",
      "enabled": true,
      "weight": 85
    },
    {
      "id": "demo-villain-signer",
      "type": "SIGNING_CERT_SHA256",
      "value": "$CERT_SHA256",
      "classification": "DEMO_TEST_THREAT",
      "severity": "HIGH",
      "description": "Signing certificate matches the demo test-threat signer in the signed offline threat pack. The certificate belongs to the controlled VillainCaller demo sample.",
      "source": "thraksha-demo-pack",
      "enabled": true,
      "weight": 90
    },
    {
      "id": "demo-villain-base-apk",
      "type": "BASE_APK_SHA256",
      "value": "$APK_SHA256",
      "classification": "DEMO_TEST_THREAT",
      "severity": "HIGH",
      "description": "Installed base APK digest matches the demo test-threat record byte-for-byte (base APK only; splits are not hashed).",
      "source": "thraksha-demo-pack",
      "enabled": true,
      "weight": 97
    },
    {
      "id": "demo-disabled-control",
      "type": "PACKAGE_NAME",
      "value": "com.thraksha.demo.goodcaller",
      "classification": "DEMO_NEGATIVE_CONTROL",
      "severity": "LOW",
      "description": "DISABLED negative control: proves disabled indicators never match. If GoodCaller is ever flagged by threat intelligence, this record was wrongly evaluated.",
      "source": "thraksha-demo-pack",
      "enabled": false,
      "weight": 10
    },
    {
      "id": "demo-nonmatching-hash-control",
      "type": "BASE_APK_SHA256",
      "value": "0000000000000000000000000000000000000000000000000000000000000000",
      "classification": "DEMO_NEGATIVE_CONTROL",
      "severity": "LOW",
      "description": "Enabled negative control with a digest that matches nothing: proves the scanner matches values, not mere presence in the pack.",
      "source": "thraksha-demo-pack",
      "enabled": true,
      "weight": 10
    },
    {
      "id": "demo-future-type-control",
      "type": "DOMAIN",
      "value": "demo-threat.example.invalid",
      "classification": "DEMO_NEGATIVE_CONTROL",
      "severity": "LOW",
      "description": "FUTURE-typed record: representable in the schema but never evaluated by this build.",
      "source": "thraksha-demo-pack",
      "enabled": true,
      "weight": 10
    },
    {
      "id": "demo-network-endpoint",
      "type": "DESTINATION_IP",
      "value": "203.0.113.113",
      "classification": "DEMO_TEST_NETWORK_INDICATOR",
      "severity": "HIGH",
      "description": "Outbound destination matches a controlled demo network indicator in the signed offline threat pack. 203.0.113.0/24 is RFC 5737 TEST-NET-3 documentation space — this is a demonstration indicator, not real malware infrastructure.",
      "source": "thraksha-demo-pack",
      "enabled": true,
      "weight": 88
    },
    {
      "id": "demo-network-nonmatching-control",
      "type": "DESTINATION_IP",
      "value": "203.0.113.200",
      "classification": "DEMO_NEGATIVE_CONTROL",
      "severity": "LOW",
      "description": "Enabled network negative control: a TEST-NET address the demo probe never targets, proving destination matching is exact.",
      "source": "thraksha-demo-pack",
      "enabled": true,
      "weight": 10
    }
  ]
}
EOF

# --- sign (RSA-2048 / SHA-256 over the exact bytes, same scheme as the rulepack) --
openssl dgst -sha256 -sign "$PRIVATE_KEY" -out "$SIG.tmp" "$JSON"
openssl base64 -A -in "$SIG.tmp" -out "$SIG"
rm -f "$SIG.tmp"
openssl rsa -in "$PRIVATE_KEY" -pubout -outform DER 2>/dev/null | openssl base64 -A -out "$PUB"

echo "Wrote:    $JSON"
echo "Wrote:    $SIG"
echo "Wrote:    $PUB"

# --- verify immediately, the way the app will -------------------------------------
TMP_PEM="$(mktemp)"; TMP_BIN="$(mktemp)"
trap 'rm -f "$TMP_PEM" "$TMP_BIN"' EXIT
openssl rsa -in "$PRIVATE_KEY" -pubout -out "$TMP_PEM" 2>/dev/null
openssl base64 -d -A -in "$SIG" -out "$TMP_BIN"
if openssl dgst -sha256 -verify "$TMP_PEM" -signature "$TMP_BIN" "$JSON" > /dev/null 2>&1; then
  echo "Verified: signature is valid over the current threatpack.json"
else
  echo "ERROR: verification failed immediately after signing." >&2
  exit 1
fi

echo
echo "Done. Rebuild Guardian so the new assets are packaged:"
echo "  ./gradlew :app:assembleDebug"
