#!/usr/bin/env bash
# Small, checksum-verifying Gradle bootstrap wrapper. It intentionally avoids a checked-in
# binary wrapper JAR; the official distribution checksum is fetched over HTTPS before execution.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROPS="$ROOT/gradle/wrapper/gradle-wrapper.properties"
URL="$(sed -n 's/^distributionUrl=//p' "$PROPS" | sed 's/\\:/:/g')"
if [[ -z "$URL" ]]; then echo "Missing distributionUrl in $PROPS" >&2; exit 2; fi
ZIP="${URL##*/}"
VERSION="${ZIP#gradle-}"
VERSION="${VERSION%-bin.zip}"
GH="${GRADLE_USER_HOME:-$HOME/.gradle}"
INSTALL="$GH/wrapper/dists/marbledo-$VERSION"
GRADLE="$INSTALL/gradle-$VERSION/bin/gradle"
if [[ ! -x "$GRADLE" ]]; then
  mkdir -p "$INSTALL"
  TMP="$INSTALL/.download-$$"
  mkdir -p "$TMP"
  echo "Downloading Gradle $VERSION (official SHA-256 verification enabled)"
  curl --fail --location --retry 3 --output "$TMP/$ZIP" "$URL"
  curl --fail --location --retry 3 --output "$TMP/$ZIP.sha256" "$URL.sha256"
  EXPECTED="$(tr -d '[:space:]' < "$TMP/$ZIP.sha256")"
  ACTUAL="$(sha256sum "$TMP/$ZIP" | awk '{print $1}')"
  if [[ "$EXPECTED" != "$ACTUAL" ]]; then
    echo "Gradle distribution checksum mismatch; refusing to run." >&2
    rm -rf "$TMP"
    exit 3
  fi
  unzip -q "$TMP/$ZIP" -d "$TMP"
  mv "$TMP/gradle-$VERSION" "$INSTALL/gradle-$VERSION"
  rm -rf "$TMP"
fi
exec "$GRADLE" -p "$ROOT" "$@"
