#!/system/bin/sh
# Exact-firmware v2 repair: owned files only, system-service namespace, no reboot.
set -eu
umask 077
BASE=$(CDPATH= cd "$(dirname "$0")" && pwd -P)
say() { printf '%s\n' "$*"; }
fail() { say "STOP: $*" >&2; exit 1; }
present() { [ -e "$1" ] || [ -L "$1" ]; }
real_dir() { [ -d "$1" ] && [ ! -L "$1" ] && [ "$(readlink -f "$1")" = "$1" ]; }
digest() { sha256sum "$1" | { read -r hash rest; printf '%s\n' "$hash"; }; }
matches() { [ -f "$1" ] && [ ! -L "$1" ] && [ "$(digest "$1")" = "$2" ]; }
need_hash() { matches "$1" "$2" || fail "Unexpected file/hash: $1"; }
SIGNATURE=SamsungPlusAppleBoot-v2
OLD_SIGNATURE=SamsungPlusAppleBoot-v1
OLD_SHA=b0c57bc0281f6aaca90f049fff21c8f23d58e6a1ed27ca0f07ef9cc8971124c7
ASSET_SHA=__ASSET_SHA__
GZIP_SHA=__GZIP_SHA__
PLAYER_SHA=76afbd61a515ba4c29221531984f3684be3caf6c6a0d1602942aced3928ddf9b
RC_SHA=e7a8c6e4fa1b3176d2571f8f52b6fb24f555a14a0867a5a1d0ed129fda83d504
DATA=/data/system/b2b
EFS=/efs/knoxcustom
MAIN=$DATA/bootanimation.qmg
LOOP=$DATA/bootloop.qmg
CONFIG=$DATA/BootFileInfo.txt
MARKER=$EFS/KnoxCustomBootEnable.txt
PENDING=$EFS/.SamsungPlusApple-v2.pending
STATE=/data/adb/samsung-plus-apple-boot-v2
OLD_STATE=/data/adb/samsung-plus-apple-boot-v1
RECEIPT=$BASE/Samsung-Plus-Apple-Boot-v2-receipt.txt

identity() {
 [ "$(getprop ro.product.model)" = SM-S926U ] || fail 'Wrong phone model.'
 [ "$(getprop ro.build.fingerprint)" = 'samsung/e2qsqw/e2q:16/BP4A.251205.006/S926USQU5DZDR:user/release-keys' ] || fail 'Firmware changed.'
}
expected_config() { printf '%s\r\n%s\r\n' "$MAIN" "$LOOP"; }
expected_marker() { printf 'true\r\n'; }
config_matches() { [ -f "$CONFIG" ] && [ ! -L "$CONFIG" ] && expected_config | cmp -s - "$CONFIG"; }
marker_matches() { [ -f "$MARKER" ] && [ ! -L "$MARKER" ] && expected_marker | cmp -s - "$MARKER"; }
label() {
 value=$(ls -Zd "$1")
 case "$value" in *"u:object_r:$2:s0"*) ;; *) fail "Unexpected label: $1" ;; esac
}
namespace_ok() {
 [ "$(readlink "/proc/$RUNTIME_PID/exe")" = /system/bin/surfaceflinger ] || fail 'The system-service anchor changed.'
 [ "$(readlink /proc/self/ns/mnt)" = "$(readlink "/proc/$RUNTIME_PID/ns/mnt")" ] || fail 'Not in the system-service mount namespace.'
}
baseline() {
 [ "$(id -u)" = 0 ] || fail 'Root must be active; run this through su.'
 identity
 namespace_ok
 [ "$(getenforce)" = Enforcing ] || fail 'Expected SELinux Enforcing.'
 [ "$(getprop ro.boot.verifiedbootstate)" = green ] || fail 'Expected verified boot green.'
 [ "$(getprop ro.boot.flash.locked)" = 1 ] || fail 'Expected locked bootloader.'
 [ "$(getprop ro.boot.warranty_bit)" = 0 ] || fail 'Cached warranty property missing/nonzero.'
 [ "$(getprop sys.boot_completed)" = 1 ] || fail 'Wait for Android startup to finish.'
 [ "$(getprop init.svc.bootanim)" = stopped ] || fail 'The real boot-animation service is active.'
 for tool in sha256sum readlink ls cp cmp mv mkdir rmdir rm chown chmod restorecon sync cat date grep base64 gzip; do
  command -v "$tool" >/dev/null 2>&1 || fail "Missing command: $tool"
 done
 for path in /data /data/system /data/adb /efs; do real_dir "$path" || fail "Unexpected directory: $path"; done
 need_hash /system/bin/bootanimation "$PLAYER_SHA"
 need_hash /system/etc/init/bootanim.rc "$RC_SHA"
}
owned_targets() {
 known=0
 for entry in "$OLD_STATE:$OLD_SIGNATURE" "$STATE:$SIGNATURE"; do
  dir=${entry%:*}; sig=${entry##*:}
  if present "$dir"; then
   real_dir "$dir" && [ -f "$dir/signature" ] && [ ! -L "$dir/signature" ] || fail "Unknown record: $dir"
   [ "$(cat "$dir/signature")" = "$sig" ] || fail "Unknown record signature: $dir"
   known=1
  fi
 done
 for dir in "$DATA" "$EFS"; do
  if present "$dir"; then real_dir "$dir" || fail "Unexpected customization directory: $dir"; fi
 done
 for path in "$MAIN" "$LOOP"; do
  if present "$path"; then
   if matches "$path" "$OLD_SHA" || matches "$path" "$ASSET_SHA"; then known=1
   else fail "Image changed or belongs to another customization: $path"; fi
  fi
 done
 if present "$CONFIG"; then config_matches || fail 'Unrecognized path configuration; kept unchanged.'; fi
 if present "$MARKER"; then
  [ "$known" = 1 ] && marker_matches || fail 'Cannot establish ownership of the activation marker.'
 fi
 if present "$PENDING"; then fail 'A pending activation already exists; left unchanged.'; fi
 for dir in "$DATA" "$EFS"; do
  if [ -d "$dir" ]; then
   for path in "$dir"/* "$dir"/.[!.]* "$dir"/..?*; do
    present "$path" || continue
    case "$path" in "$MAIN"|"$LOOP"|"$CONFIG"|"$MARKER") ;; *) fail "Unexpected customization file was kept: $path" ;; esac
   done
  fi
 done
}
restore_artwork() {
 baseline
 owned_targets
 if present "$MARKER"; then rm "$MARKER"; sync; fi
 for path in "$MAIN" "$LOOP" "$CONFIG"; do if present "$path"; then rm "$path"; fi; done
 rmdir "$DATA" 2>/dev/null || :
 rmdir "$EFS" 2>/dev/null || :
 if real_dir "$STATE"; then say restored > "$STATE/status"; fi
 sync
 say 'RESTORED: matching v1/v2 custom activation removed. No reboot requested.'
}
changing=0
committed=0
payload_owned=0
made_data=0
made_efs=0
rollback() {
 # The previous attempt already failed. On repair failure, prefer stock fallback
 # over reactivating an incompletely replaced custom animation.
 if real_dir "$EFS"; then
  if present "$MARKER" && marker_matches; then rm -f "$MARKER" || :; fi
  rm -f "$PENDING" || :
 fi
 for name in bootanimation.qmg bootloop.qmg BootFileInfo.txt; do
  rm -f "$DATA/.$name.v2-pending" || :
  if [ -f "$STATE/backup-$name" ]; then
   cp "$STATE/backup-$name" "$DATA/$name" || :
   chown 1000:1000 "$DATA/$name" || :
   chmod 0644 "$DATA/$name" || :
   restorecon "$DATA/$name" || :
  else
   rm -f "$DATA/$name" || :
  fi
 done
 if [ "$made_data" = 1 ]; then rmdir "$DATA" 2>/dev/null || :; fi
 if [ "$made_efs" = 1 ]; then rmdir "$EFS" 2>/dev/null || :; fi
 rm -f "$STATE/payload.qmg" "$STATE/payload.qmg.gz" || :
 say failed > "$STATE/status" || :
 sync || :
 say 'Repair failed; its activation was removed where possible. No reboot requested.' >&2
}
worker_exit() {
 code=$?
 trap - EXIT HUP INT TERM
 if [ "$changing" = 1 ] && [ "$committed" = 0 ]; then rollback; fi
 if [ "$payload_owned" = 1 ]; then rm -f "$STATE/payload.qmg" "$STATE/payload.qmg.gz" || :; fi
 exit "$code"
}
repair() {
 baseline
 owned_targets
 if ! present "$STATE"; then mkdir "$STATE"; chmod 0700 "$STATE"; say "$SIGNATURE" > "$STATE/signature"; fi
 for path in "$STATE/payload.qmg" "$STATE/payload.qmg.gz"; do
  present "$path" && fail "Prior payload was kept: $path"
 done
 trap worker_exit EXIT
 trap 'exit 129' HUP
 trap 'exit 130' INT
 trap 'exit 143' TERM
 payload_owned=1
 # No EFS/B2B writes until both payload integrity checks pass.
 base64 -d > "$STATE/payload.qmg.gz" <<'SAMSUNG_APPLE_V2_GZIP'
__ASSET_BASE64__
SAMSUNG_APPLE_V2_GZIP
 need_hash "$STATE/payload.qmg.gz" "$GZIP_SHA"
 gzip -dc "$STATE/payload.qmg.gz" > "$STATE/payload.qmg"
 need_hash "$STATE/payload.qmg" "$ASSET_SHA"
 rm "$STATE/payload.qmg.gz"
 for name in bootanimation.qmg bootloop.qmg BootFileInfo.txt; do
  # Refresh known-file backups for this attempt; protected directory inspection
  # above has already refused every unknown filename/hash/symlink.
  if present "$DATA/$name"; then cp "$DATA/$name" "$STATE/backup-$name"
  else rm -f "$STATE/backup-$name"; fi
 done
 changing=1
 say preparing > "$STATE/status"
 if present "$MARKER"; then rm "$MARKER"; sync; fi
 if ! present "$DATA"; then mkdir "$DATA"; made_data=1; fi
 if ! present "$EFS"; then mkdir "$EFS"; made_efs=1; fi
 chown 1000:1000 "$DATA" "$EFS"
 chmod 0755 "$DATA" "$EFS"
 for name in bootanimation.qmg bootloop.qmg; do cp "$STATE/payload.qmg" "$DATA/.$name.v2-pending"; done
 expected_config > "$DATA/.BootFileInfo.txt.v2-pending"
 for name in bootanimation.qmg bootloop.qmg BootFileInfo.txt; do
  chown 1000:1000 "$DATA/.$name.v2-pending"
  chmod 0644 "$DATA/.$name.v2-pending"
  restorecon "$DATA/.$name.v2-pending"
  label "$DATA/.$name.v2-pending" system_data_file
 done
 need_hash "$DATA/.bootanimation.qmg.v2-pending" "$ASSET_SHA"
 need_hash "$DATA/.bootloop.qmg.v2-pending" "$ASSET_SHA"
 for name in bootanimation.qmg bootloop.qmg BootFileInfo.txt; do mv "$DATA/.$name.v2-pending" "$DATA/$name"; done
 restorecon "$DATA" "$EFS" "$MAIN" "$LOOP" "$CONFIG"
 label "$DATA" system_data_file
 label "$EFS" efs_file
 label "$MAIN" system_data_file
 label "$LOOP" system_data_file
 label "$CONFIG" system_data_file
 config_matches || fail 'Configuration readback failed.'
 need_hash "$MAIN" "$ASSET_SHA"
 need_hash "$LOOP" "$ASSET_SHA"
 expected_marker > "$PENDING"
 chown 1000:1000 "$PENDING"
 chmod 0644 "$PENDING"
 restorecon "$PENDING"
 label "$PENDING" efs_file
 need_hash /system/bin/bootanimation "$PLAYER_SHA"
 [ "$(getenforce)" = Enforcing ] || fail 'Security mode changed.'
 sync
 mv "$PENDING" "$MARKER"
 sync
 marker_matches || fail 'Activation readback failed.'
 say staged > "$STATE/status"
 {
  say "signature=$SIGNATURE"
  say "install_boot_id=$(cat /proc/sys/kernel/random/boot_id)"
  say "asset_sha256=$ASSET_SHA"
  say "runtime_mount_namespace=$(readlink /proc/self/ns/mnt)"
 } > "$BASE/receipt.txt"
 rm "$STATE/payload.qmg"
 committed=1
 say 'V2 STAGED: native-size, 24-frame SAMSUNG + Apple artwork in the system-service mount namespace.'
 say 'Readback, labels and hashes passed. First startup with v2 is not confirmed.'
 say 'No reboot was requested. Keep the script for restore.'
}
verify() {
 identity
 [ -f "$RECEIPT" ] && [ ! -L "$RECEIPT" ] || fail 'Keep the v2 receipt beside this script.'
 grep -Fx "signature=$SIGNATURE" "$RECEIPT" >/dev/null || fail 'Receipt does not match v2.'
 old=$(sed -n 's/^install_boot_id=//p' "$RECEIPT")
 [ "$old" != "$(cat /proc/sys/kernel/random/boot_id)" ] || fail 'Still the staging boot.'
 log=$BASE/Samsung-Plus-Apple-v2-check-$(date +%Y%m%d-%H%M%S)-$$.txt
 logcat -b all -d -v threadtime -s 'BootAnimationResource:V' 'BootAnimationLoader:V' 'BootAnimation:V' '*:S' > "$log" 2>&1 || :
 if grep -F "<<knox MAIN>> resource = $MAIN" "$log" | grep -F 'Load [' >/dev/null && grep -F 'draw Start' "$log" >/dev/null && ! grep -Ei 'decode.*fail|outbuffer.*null|parse from buffer fail|unsupported format' "$log" >/dev/null; then
  say 'Custom resource/drawing evidence found. This does not prove every frame rendered.'
 else say 'Startup playback remains unconfirmed. No root or reboot was requested.'; fi
}

# Use SurfaceFlinger's namespace, rather than an app's private root namespace
# or PID 1's potentially different APEX bootstrap namespace. /sdcard may not
# exist there, so use a private /data/local/tmp staging directory.
if [ "${1:-repair}" = __global ]; then
 mode=${2:-check}
 BASE=$3
 RUNTIME_PID=$4
 namespace_ok
 case "$mode" in
  repair|install) repair ;;
  restore) restore_artwork ;;
  check) baseline; owned_targets; say 'Boot-namespace checks passed; nothing changed.' ;;
  *) fail 'Unknown worker mode.' ;;
 esac
 exit
fi
mode=${1:-repair}
if [ "$mode" = verify ]; then verify; exit; fi
case "$mode" in repair|install|restore|check) ;; *) fail 'Use repair, restore, check, or verify.' ;; esac
[ "$(id -u)" = 0 ] || fail 'Root must be active; run through su.'
identity
command -v nsenter >/dev/null 2>&1 || fail 'System-service namespace entry is unavailable; nothing changed.'
command -v pidof >/dev/null 2>&1 || fail 'Cannot identify the system-service namespace.'
RUNTIME_PID=$(pidof surfaceflinger)
case "$RUNTIME_PID" in ''|*[!0-9]*) fail 'Expected one running SurfaceFlinger process.' ;; esac
[ "$(readlink "/proc/$RUNTIME_PID/exe")" = /system/bin/surfaceflinger ] || fail 'Unexpected system-service executable.'
real_dir /data/local/tmp || fail 'Unexpected staging parent.'
STAGE=/data/local/tmp/samsung-apple-v2-$$
present "$STAGE" && fail 'Staging path already exists; left unchanged.'
mkdir "$STAGE"
chmod 0700 "$STAGE"
outer_exit() {
 code=$?
 trap - EXIT HUP INT TERM
 rm -f "$STAGE/worker.sh" "$STAGE/receipt.txt" 2>/dev/null || :
 rmdir "$STAGE" 2>/dev/null || :
 exit "$code"
}
trap outer_exit EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
cp "$0" "$STAGE/worker.sh"
chmod 0600 "$STAGE/worker.sh"
nsenter -t "$RUNTIME_PID" -m /system/bin/sh "$STAGE/worker.sh" __global "$mode" "$STAGE" "$RUNTIME_PID"
if [ -f "$STAGE/receipt.txt" ]; then
 if present "$RECEIPT"; then
  [ -f "$RECEIPT" ] && [ ! -L "$RECEIPT" ] && grep -Fx "signature=$SIGNATURE" "$RECEIPT" >/dev/null || fail 'Repair staged; an unrelated Download receipt was preserved.'
 fi
 cp "$STAGE/receipt.txt" "$RECEIPT"
 chmod 0644 "$RECEIPT"
 say "Receipt: $RECEIPT"
fi
