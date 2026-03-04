#!/usr/bin/env bash
#
# Usage: ./release.sh <version>
#   e.g. ./release.sh 1.1
#
# Bumps versionCode, sets versionName, builds the debug APK,
# commits, tags, and creates a GitHub release with the APK attached.

set -euo pipefail

if [ $# -ne 1 ]; then
    echo "Usage: $0 <version>  (e.g. 1.1)"
    exit 1
fi

VERSION="$1"
GRADLE="app/build.gradle"
APK="app/build/outputs/apk/debug/app-debug.apk"

# Extract current versionCode
OLD_CODE=$(grep 'versionCode' "$GRADLE" | head -1 | sed 's/[^0-9]//g')
NEW_CODE=$((OLD_CODE + 1))

echo "==> Bumping versionCode $OLD_CODE -> $NEW_CODE, versionName -> $VERSION"

# Update build.gradle
sed -i "s/versionCode $OLD_CODE/versionCode $NEW_CODE/" "$GRADLE"
sed -i "s/versionName \"[^\"]*\"/versionName \"$VERSION\"/" "$GRADLE"

# Build
echo "==> Building APK..."
./gradlew assembleDebug

if [ ! -f "$APK" ]; then
    echo "ERROR: APK not found at $APK"
    exit 1
fi

# Commit and tag
echo "==> Committing version bump..."
git add "$GRADLE"
git commit -m "Release v${VERSION} (build ${NEW_CODE})"
git tag "v${VERSION}"

# Create GitHub release
echo "==> Creating GitHub release v${VERSION}..."
gh release create "v${VERSION}" "$APK" \
    --title "v${VERSION}" \
    --notes "Build ${NEW_CODE}" \
    --latest

echo "==> Done! Release v${VERSION} published."
