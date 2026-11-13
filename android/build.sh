#!/usr/bin/env bash
# InkBook APK build - pure aapt2/javac/d8 pipeline, no Gradle needed.
# Usage: bash build.sh
set -euo pipefail

cd "$(dirname "$0")"

SDK="${SDK:-/home/z/android-sdk}"
ECJ="${ECJ:-/home/z/ecj.jar}"
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
OUT=build

echo "java: $(java -version 2>&1 | head -1)"
echo "ecj: $(java -jar "$ECJ" -version 2>&1 | head -1)"

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/obj" "$OUT/dex"

echo "[1/7] aapt2 compile resources"
"$BT/aapt2" compile --dir res -o "$OUT/res.zip"

echo "[2/7] aapt2 link"
"$BT/aapt2" link -o "$OUT/base.apk" \
  -I "$PLATFORM" \
  --manifest AndroidManifest.xml \
  -R "$OUT/res.zip" \
  -A assets \
  --java "$OUT/gen" \
  --min-sdk-version 21 \
  --target-sdk-version 34 \
  --version-code 1 \
  --version-name 1.0.0 \
  --auto-add-overlay

echo "[3/7] ecj compile"
java -jar "$ECJ" -1.8 -nowarn -encoding UTF-8 \
  -bootclasspath "$PLATFORM" \
  -d "$OUT/obj" \
  src/com/inkbook/reader/*.java \
  "$OUT/gen/com/inkbook/reader/R.java"

echo "[4/7] d8 dex"
find "$OUT/obj" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --release --lib "$PLATFORM" --min-api 21 \
  --output "$OUT/dex" \
  @"$OUT/classes.txt"

echo "[5/7] package"
APK_OUT="$(pwd)/$OUT/base.apk"
(cd "$OUT/dex" && zip -q "$APK_OUT" classes.dex)
"$BT/zipalign" -f -p 4 "$OUT/base.apk" "$OUT/aligned.apk"

echo "[6/7] sign"
if [ ! -f debug.keystore ]; then
  keytool -genkeypair -keystore debug.keystore -alias inkbook \
    -storepass inkbook -keypass inkbook \
    -dname "CN=InkBook, O=InkBook, C=US" \
    -keyalg RSA -keysize 2048 -validity 10000
fi
"$BT/apksigner" sign --ks debug.keystore --ks-pass pass:inkbook --key-pass pass:inkbook \
  --out InkBook.apk "$OUT/aligned.apk"

echo "[7/7] verify"
"$BT/apksigner" verify --print-certs InkBook.apk | head -5

SIZE=$(du -h InkBook.apk | cut -f1)
echo "BUILD OK: $(pwd)/InkBook.apk ($SIZE)"
