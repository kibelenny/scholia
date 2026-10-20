#!/bin/zsh
# Builds the release APK and signs it so it installs as an update over every earlier version.
#
# v1.0 was signed with this Mac's debug key. Later versions are signed with the release key plus
# a rotation lineage (old key -> release key): Android 9+ verifies the new key through the
# lineage, and older devices still see the old key in the v1/v2 signatures.
#
# Signing material lives outside the repo in $SCHOLIA_SIGNING (default ~/.config/scholia-signing):
#   release.jks, legacy-debug.keystore, lineage.bin, signing.properties
# Losing release.jks or its password means future builds can't update existing installs.
set -euo pipefail

ROOT=${0:A:h:h}
SIGNING=${SCHOLIA_SIGNING:-$HOME/.config/scholia-signing}
SDK=$(sed -n 's/^sdk.dir=//p' "$ROOT/local.properties")
BUILD_TOOLS=$SDK/build-tools/37.0.0
export JAVA_HOME=${SCHOLIA_JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}

prop() { sed -n "s/^$1=//p" "$SIGNING/signing.properties"; }
for f in release.jks legacy-debug.keystore lineage.bin signing.properties; do
  [[ -f $SIGNING/$f ]] || { echo "Missing $SIGNING/$f" >&2; exit 1; }
done

cd "$ROOT"
./gradlew assembleRelease --console=plain -q

VERSION=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)
UNSIGNED=app/build/outputs/apk/release/app-release-unsigned.apk
OUT=release/scholia-v$VERSION.apk
mkdir -p release

"$BUILD_TOOLS/apksigner" sign \
  --ks "$(prop legacyStoreFile)" --ks-key-alias "$(prop legacyKeyAlias)" --ks-pass "pass:$(prop legacyStorePassword)" \
  --next-signer \
  --ks "$(prop storeFile)" --ks-key-alias "$(prop keyAlias)" --ks-pass "pass:$(prop storePassword)" \
  --lineage "$SIGNING/lineage.bin" \
  --rotation-min-sdk-version 28 \
  --out "$OUT" "$UNSIGNED"

cp "$OUT" release/reader.apk
(cd release && shasum -a 256 "${OUT:t}" > "${OUT:t}.sha256")

"$BUILD_TOOLS/apksigner" verify --print-certs "$OUT" 2>/dev/null | grep -E 'Signer|SHA-256' || true
echo
echo "Built $OUT ($(stat -f %z "$OUT") bytes)"
cat "release/${OUT:t}.sha256"
