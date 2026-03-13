#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# bump-version.sh — PKassist version bumper
#
# Usage:
#   ./bump-version.sh 1.7 "Added voice shortcuts and battery saver mode"
#
# What it does:
#   1. Updates versionCode (+1) and versionName in app/build.gradle.kts
#   2. Updates artifacts/android-assistant/version.json
#   3. Commits both files
#   4. Creates a git tag  v<NEW_VERSION>
#   5. Pushes commit + tag → GitHub Actions auto-builds & publishes the release
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

NEW_VERSION="${1:-}"
RELEASE_NOTES="${2:-Bug fixes and improvements}"

if [[ -z "$NEW_VERSION" ]]; then
  echo "Usage: ./bump-version.sh <new_version> [\"release notes\"]"
  echo "Example: ./bump-version.sh 1.7 \"Added dark mode\""
  exit 1
fi

TAG="v${NEW_VERSION}"
GRADLE="app/build.gradle.kts"

# ── Validate we are in the android-assistant directory ──────────────────────
if [[ ! -f "$GRADLE" ]]; then
  echo "Error: Run this script from the artifacts/android-assistant/ directory"
  exit 1
fi

# ── Read current versionCode ─────────────────────────────────────────────────
CURRENT_CODE=$(grep 'versionCode' "$GRADLE" | grep -o '[0-9]\+')
NEW_CODE=$(( CURRENT_CODE + 1 ))

echo "Bumping version:  ${CURRENT_CODE} → ${NEW_CODE}  (${TAG})"

# ── 1. Update build.gradle.kts ───────────────────────────────────────────────
sed -i "s/versionCode = ${CURRENT_CODE}/versionCode = ${NEW_CODE}/" "$GRADLE"
sed -i "s/versionName = \"[^\"]*\"/versionName = \"${NEW_VERSION}\"/" "$GRADLE"

echo "✓ Updated $GRADLE"

# ── 2. Update version.json ───────────────────────────────────────────────────
REPO=$(git remote get-url origin | sed 's/.*github.com[:/]//' | sed 's/\.git//')
DOWNLOAD_URL="https://github.com/${REPO}/releases/download/${TAG}/PKassist-${TAG}.apk"

cat > version.json <<EOF
{
  "versionCode": ${NEW_CODE},
  "versionName": "${NEW_VERSION}",
  "releaseNotes": "${RELEASE_NOTES}",
  "downloadUrl": "${DOWNLOAD_URL}"
}
EOF

echo "✓ Updated version.json"

# ── 3. Commit ────────────────────────────────────────────────────────────────
git add "$GRADLE" version.json
git commit -m "chore: bump version to ${TAG}"

echo "✓ Committed"

# ── 4. Tag ───────────────────────────────────────────────────────────────────
git tag -a "${TAG}" -m "PKassist ${TAG}"

echo "✓ Created tag ${TAG}"

# ── 5. Push ───────────────────────────────────────────────────────────────────
echo ""
echo "Ready to push. Run:"
echo "  git push && git push --tags"
echo ""
echo "GitHub Actions will then automatically:"
echo "  • Build the APK"
echo "  • Create a GitHub Release with the APK attached"
echo "  • Users with older versions will see an update notification"
