#!/data/data/com.termux/files/usr/bin/bash
# Commit + push to GitHub, wait for the Actions build, download and open the APK.
# Usage: bash publish.sh ["commit message"]
set -e
cd "$(dirname "$0")"
MSG="${1:-Update app}"

if [ ! -d .git ]; then
  git init -b main
fi

if ! git remote get-url origin >/dev/null 2>&1; then
  read -r -p "New GitHub repo name [datatracker]: " REPO
  REPO="${REPO:-datatracker}"
  git add -A
  git commit -m "$MSG" || true
  gh repo create "$REPO" --private --source=. --remote=origin --push
else
  git add -A
  git commit -m "$MSG" || echo "(nothing new to commit)"
  git push -u origin main
fi

echo "==> Waiting for the build to start..."
sleep 10
RUN_ID="$(gh run list --workflow build-apk.yml --limit 1 --json databaseId -q '.[0].databaseId')"
if [ -z "$RUN_ID" ]; then
  echo "No workflow run found. Check the Actions tab on GitHub."
  exit 1
fi

echo "==> Building (run $RUN_ID). First build takes ~5-8 min."
if ! gh run watch "$RUN_ID" --exit-status; then
  echo
  echo "Build failed. Last log lines:"
  gh run view "$RUN_ID" --log-failed | grep -E "e: |error:|FAILED"
  exit 1
fi

if [ -d "$HOME/storage/downloads" ]; then
  OUT="$HOME/storage/downloads/datatracker-apk"
else
  OUT="$HOME/apk-out"
fi
rm -rf "$OUT"; mkdir -p "$OUT"
gh run download "$RUN_ID" -n app-release-apk -D "$OUT"

APK="$(find "$OUT" -name '*.apk' | head -1)"
echo
echo "APK saved: $APK"
termux-open "$APK" 2>/dev/null || echo "Open it from your Files app > Downloads > datatracker-apk"
