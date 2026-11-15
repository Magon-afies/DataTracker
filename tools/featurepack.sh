#!/data/data/com.termux/files/usr/bin/bash
# FeaturePack updater.  Usage:  fp            (newest files-suite*.zip in Downloads)
#                               fp --force    (re-apply even if same version)
#                               fp --no-publish
# Set FP_PROJECT=/path if your project is not ~/DataTracker.
set -e
PROJ="${FP_PROJECT:-$HOME/DataTracker}"
DL="${FP_DOWNLOADS:-$HOME/storage/downloads}"
FORCE=0; PUBLISH=1; ZIP=""; DIR=""
while [ $# -gt 0 ]; do
  case "$1" in
    --force) FORCE=1;;
    --no-publish) PUBLISH=0;;
    --dir) DIR="$2"; shift;;
    *.zip) ZIP="$1";;
  esac
  shift
done
[ -f "$PROJ/app/build.gradle.kts" ] || { echo "Project not found at $PROJ  (use: FP_PROJECT=/path fp)"; exit 1; }
command -v python3 >/dev/null || { echo "Installing python..."; pkg install -y python; }
command -v unzip >/dev/null || pkg install -y unzip

TMP=""
if [ -n "$DIR" ]; then
  SRC="$DIR"
else
  [ -n "$ZIP" ] || ZIP="$(ls -t "$DL"/files-suite*.zip 2>/dev/null | head -n1)"
  [ -n "$ZIP" ] && [ -f "$ZIP" ] || { echo "No files-suite*.zip found in $DL. Download the new zip first."; exit 1; }
  TMP="$(mktemp -d)"
  unzip -oq "$ZIP" -d "$TMP"
  SRC="$TMP"
  [ -d "$SRC/overlay" ] || SRC="$(dirname "$(find "$TMP" -maxdepth 3 -type d -name overlay | head -n1)")"
fi
[ -d "$SRC/overlay" ] || { echo "That zip doesn't look like a FeaturePack."; exit 1; }

NEW="$(cat "$SRC/VERSION" 2>/dev/null || echo 0)"
OLD="$(cat "$PROJ/.featurepack-version" 2>/dev/null || echo 0)"
echo "FeaturePack version $NEW  (installed: $OLD)"
if [ "$FORCE" = 0 ] && [ "$NEW" -le "$OLD" ]; then
  echo "Already up to date. Download the newer zip, or run: fp --force"
  [ -n "$TMP" ] && rm -rf "$TMP"; exit 0
fi

echo "==> Copying files"
cp -r "$SRC/overlay/app" "$PROJ/"
echo "==> Patching manifest / gradle / menu"
python3 "$SRC/patch.py" "$PROJ"
echo "$NEW" > "$PROJ/.featurepack-version"
[ -n "$TMP" ] && rm -rf "$TMP"

if [ "$PUBLISH" = 1 ]; then
  cd "$PROJ" && bash publish.sh "FeaturePack v$NEW"
else
  echo "Applied. Build with: cd $PROJ && bash publish.sh \"FeaturePack v$NEW\""
fi
