#!/data/data/com.termux/files/usr/bin/bash
# Creates a release keystore and stores it as GitHub Actions secrets.
# Run AFTER publish.sh has created the GitHub repo.
set -e
cd "$(dirname "$0")"

command -v keytool >/dev/null || { echo "keytool missing - run termux-setup.sh first"; exit 1; }
git remote get-url origin >/dev/null 2>&1 || { echo "No GitHub repo yet - run publish.sh first"; exit 1; }

KS="$HOME/datatracker-release.keystore"
if [ -f "$KS" ]; then
  echo "Keystore already exists at $KS - reusing it."
  read -r -s -p "Keystore password: " PASS; echo
  KEYPASS="$PASS"
else
  PASS="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 20)"
  KEYPASS="$PASS"
  keytool -genkeypair -v -keystore "$KS" -alias datatracker \
    -keyalg RSA -keysize 2048 -validity 36500 \
    -storepass "$PASS" -keypass "$KEYPASS" \
    -dname "CN=DataTracker,O=Personal,C=US"
  {
    echo "keystore: $KS"
    echo "alias: datatracker"
    echo "store/key password: $PASS"
  } > "$HOME/keystore-secrets.txt"
  chmod 600 "$HOME/keystore-secrets.txt" "$KS"
fi

echo "==> Uploading GitHub secrets"
base64 -w0 "$KS" | gh secret set KEYSTORE_BASE64
printf '%s' "$PASS"    | gh secret set KEYSTORE_PASSWORD
printf '%s' "datatracker" | gh secret set KEY_ALIAS
printf '%s' "$KEYPASS" | gh secret set KEY_PASSWORD

echo
echo "Done. BACK UP these two files somewhere safe (cloud drive, password manager):"
echo "  $KS"
echo "  $HOME/keystore-secrets.txt"
echo "If you lose the keystore you can never update the installed app - only reinstall."
echo "Next: bash publish.sh \"signed build\""
