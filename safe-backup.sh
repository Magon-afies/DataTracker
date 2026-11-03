#!/data/data/com.termux/files/usr/bin/bash
# Zips the project WITHOUT signing keys, passwords, build output or APKs.
set -e
cd "$(dirname "$0")"
command -v zip >/dev/null 2>&1 || pkg install -y zip
if [ -d "$HOME/storage/downloads" ]; then OUT="$HOME/storage/downloads"; else OUT="$HOME"; fi
FILE="$OUT/DataTracker-Backup-$(date +%Y%m%d_%H%M%S).zip"
zip -rq "$FILE" . \
  -x '*.keystore' '*.jks' 'keystore-secrets.txt' \
  -x 'build/*' '*/build/*' '.gradle/*' 'apk-out/*' '*.apk'
echo "Saved: $FILE"
echo "(signing keys and passwords are NOT inside this zip)"
