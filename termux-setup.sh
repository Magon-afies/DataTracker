#!/data/data/com.termux/files/usr/bin/bash
# One-time Termux setup: tools, git identity, GitHub login.
set -e
cd "$(dirname "$0")"

echo "==> Installing packages (git, GitHub CLI, Java for keytool)"
pkg update -y
pkg install -y git gh openjdk-17 unzip

if [ ! -d "$HOME/storage" ]; then
  echo "==> Granting Termux access to shared storage (accept the popup)"
  termux-setup-storage || true
fi

if [ -z "$(git config --global user.name || true)" ]; then
  read -r -p "Git name: " GN && git config --global user.name "$GN"
fi
if [ -z "$(git config --global user.email || true)" ]; then
  read -r -p "Git email: " GE && git config --global user.email "$GE"
fi
git config --global init.defaultBranch main

if ! gh auth status >/dev/null 2>&1; then
  echo "==> Log in to GitHub (choose GitHub.com, HTTPS, 'Login with a web browser')"
  gh auth login
fi
gh auth setup-git

chmod +x make-keystore.sh publish.sh
echo
echo "Done. Next:"
echo "  1) bash publish.sh          # creates the repo, pushes, builds the APK"
echo "  2) bash make-keystore.sh    # optional: your own signing key (recommended)"
