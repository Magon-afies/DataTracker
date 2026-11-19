#!/data/data/com.termux/files/usr/bin/bash
# FeaturePack updater
#   fp                 apply the newest files-suite*.zip from Downloads, verify, then build
#   fp --doctor        check your setup and explain what is wrong (nothing is changed)
#   fp --undo          undo the last FeaturePack (if the last build broke something)
#   fp --no-publish    apply + verify but don't build
#   fp --dir <folder>  apply from an unzipped folder
# Everything printed is also saved to ~/fp-last.log
PROJ="${FP_PROJECT:-$HOME/DataTracker}"
DL="${FP_DOWNLOADS:-$HOME/storage/downloads}"
LOG="$HOME/fp-last.log"

main() {
  DOCTOR=0; PUBLISH=1; ZIP=""; DIR=""; UNDO=0
  while [ $# -gt 0 ]; do
    case "$1" in
      --doctor) DOCTOR=1;;
      --undo) UNDO=1;;
      --no-publish) PUBLISH=0;;
      --force) ;;
      --dir) DIR="$2"; shift;;
      *.zip) ZIP="$1";;
    esac
    shift
  done

  echo "== FeaturePack =="
  [ -f "$PROJ/app/build.gradle.kts" ] || { echo "[X] Project not found at $PROJ"; echo "    Find it with:  ls ~   then run:  FP_PROJECT=~/yourfolder fp"; return 1; }
  echo "[ok] Project: $PROJ   (installed pack version: $(cat "$PROJ/.featurepack-version" 2>/dev/null || echo none))"
  if [ "$UNDO" = 1 ]; then
    cd "$PROJ" || return 1
    MSG="$(git log -1 --pretty=%s)"
    case "$MSG" in
      FeaturePack*) echo "Undoing: $MSG"; git revert --no-edit HEAD && bash publish.sh "Undo $MSG";;
      *) echo "The last saved change is not a FeaturePack ($MSG), so nothing was undone."; return 1;;
    esac
    return $?
  fi
  command -v python3 >/dev/null || { echo "[..] Installing python"; pkg install -y python >/dev/null 2>&1; }
  command -v unzip >/dev/null || pkg install -y unzip >/dev/null 2>&1
  command -v python3 >/dev/null && echo "[ok] python3 present" || { echo "[X] python3 missing: run  pkg install python"; return 1; }

  TMP=""
  if [ -n "$DIR" ]; then
    SRC="$DIR"
  else
    [ -n "$ZIP" ] || ZIP="$(ls -t "$DL"/files-suite*.zip 2>/dev/null | head -n1)"
    if [ -z "$ZIP" ] || [ ! -f "$ZIP" ]; then
      echo "[X] No files-suite*.zip in $DL"
      echo "    Download the zip to your phone's Downloads folder first."
      [ -d "$HOME/storage/downloads" ] || echo "    Also run once:  termux-setup-storage   (and allow access)"
      return 1
    fi
    echo "[ok] Using zip: $(basename "$ZIP")  ($(date -r "$ZIP" '+%d %b %H:%M' 2>/dev/null))"
    TMP="$(mktemp -d)"
    unzip -oq "$ZIP" -d "$TMP" || { echo "[X] Could not unzip it (incomplete download?). Download it again."; return 1; }
    SRC="$TMP"
    [ -d "$SRC/overlay" ] || SRC="$(dirname "$(find "$TMP" -maxdepth 3 -type d -name overlay | head -n1)")"
  fi
  [ -d "$SRC/overlay/app" ] || { echo "[X] That zip is not a FeaturePack."; return 1; }
  NEW="$(cat "$SRC/VERSION" 2>/dev/null || echo 0)"
  echo "[ok] Pack version in zip: $NEW"

  if grep -q "alias fp=" "$HOME/.bashrc" 2>/dev/null; then echo "[ok] 'fp' shortcut installed"; else
    echo "alias fp='FP_PROJECT=\"$PROJ\" bash \"$PROJ/tools/featurepack.sh\"'" >> "$HOME/.bashrc"; echo "[ok] Added the 'fp' shortcut (new Termux sessions will have it)"; fi

  if [ "$DOCTOR" = 1 ]; then
    echo ""; echo "-- git status --"; (cd "$PROJ" && git status --short | head -15; git log --oneline | head -3)
    echo ""; echo "Doctor finished. Nothing was changed."
    [ -n "$TMP" ] && rm -rf "$TMP"; return 0
  fi

  echo ""; echo "==> Copying files"
  cp -r "$SRC/overlay/app" "$PROJ/" || { echo "[X] Copy failed"; return 1; }
  echo "==> Patching"
  python3 "$SRC/patch.py" "$PROJ"
  mkdir -p "$PROJ/tools"
  [ -f "$SRC/featurepack.sh" ] && cp "$SRC/featurepack.sh" "$PROJ/tools/featurepack.sh"
  echo "==> Verifying"
  python3 "$SRC/verify.py" "$PROJ" "$SRC/overlay"
  VRES=$?
  [ -n "$TMP" ] && rm -rf "$TMP"
  if [ $VRES -ne 0 ]; then
    echo ""
    echo "[X] Not building, because some checks failed (see the [FAIL] lines above)."
    echo "    Send Claude the output of:   tail -60 ~/fp-last.log"
    return 1
  fi
  echo "$NEW" > "$PROJ/.featurepack-version"
  if [ "$PUBLISH" = 1 ]; then
    echo ""; echo "==> Building (this takes a few minutes)"
    cd "$PROJ" && bash publish.sh "FeaturePack v$NEW"
  else
    echo "Applied and verified. Build with:  cd $PROJ && bash publish.sh \"FeaturePack v$NEW\""
  fi
}

set -o pipefail
main "$@" 2>&1 | tee "$LOG"
exit ${PIPESTATUS[0]}
