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
SIGNATURE=Bootmark-v1
OLD_SIGNATURE=SamsungPlusAppleBoot-v1
OLD_SHA=b0c57bc0281f6aaca90f049fff21c8f23d58e6a1ed27ca0f07ef9cc8971124c7
ASSET_SHA=beb3f8acb83ec0b4053c63a26455b3bd7703b0231552824cdefd0a8e0ae1fd01
GZIP_SHA=46780c84b63b653706fa33ceb7bf2e3781cd3effb463ef5f86579e96723b342f
PLAYER_SHA=76afbd61a515ba4c29221531984f3684be3caf6c6a0d1602942aced3928ddf9b
RC_SHA=e7a8c6e4fa1b3176d2571f8f52b6fb24f555a14a0867a5a1d0ed129fda83d504
DATA=/data/system/b2b
EFS=/efs/knoxcustom
MAIN=$DATA/bootanimation.qmg
LOOP=$DATA/bootloop.qmg
CONFIG=$DATA/BootFileInfo.txt
MARKER=$EFS/KnoxCustomBootEnable.txt
PENDING=$EFS/.SamsungPlusApple-v2.pending
STATE=/data/adb/bootmark-v1
OLD_STATE=/data/adb/samsung-plus-apple-boot-v1
RECEIPT=$BASE/Bootmark-receipt.txt

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
custom_matches() {
  [ -f "$STATE/custom-hashes" ] && [ ! -L "$STATE/custom-hashes" ] || return 1
  [ -f "$1" ] && [ ! -L "$1" ] || return 1
  hash=$(digest "$1")
  grep -Fx "$hash" "$STATE/custom-hashes" >/dev/null
}

owned_targets() {
 known=0
 for entry in "$OLD_STATE:$OLD_SIGNATURE" "/data/adb/samsung-plus-apple-boot-v2:SamsungPlusAppleBoot-v2" "$STATE:$SIGNATURE"; do
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
   if matches "$path" "$OLD_SHA" || matches "$path" "61d20c6db729b15b2e6e0f3a45989726fd93b7f844a44b37b4be9ac563b6c431" || matches "$path" "beb3f8acb83ec0b4053c63a26455b3bd7703b0231552824cdefd0a8e0ae1fd01" || matches "$path" "$ASSET_SHA" || custom_matches "$path"; then known=1
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
H4sIAAAAAAACA+2dX2hcWZ7f77S92zUZpat67aTLIGipRxAJDFHNNUhFBBoJG2KBaCwwxAI9KIo7
EYrDKBWnYoSiaY3wTptCnbaFcTm17sUgPxikBwf5QYO0bmqRbFxb9WCQWRykDYJUg42qeivsbbDQ
yfmcU1e6Zbdmut22Nxt+X3PV1dat7z3n9+/8zu+cc32q92fOr50//6PWOsdpvfYTJ+r8xHH09a/+
5CfOVNNPnE71piDMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizM
wizMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizM
wizMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizM
wizMwizMwizMwizMwizMwizMwizMwizMwizMwizMwizMP4R523Fe+Juyc6D2Lxyn81WY76ntsCor
FVYFVdnu1P89oMqd2ypcqOhvhQvb+v/UgVdhDiuVrWHWl/5RZS5o5nuK378ScwXKgiqUy9vbnYa5
EGTOapG9CnPnvXC5llk5ndkqczarmQvlA9udP5xZUxZ2mbdhLqvOWuZwuaJekVl3eLsza5jLLzBX
YK6UX405G2Aulzt1D8K7zPfK2VdmVp3aqDRzuKKZO2A2xlDL/Gqegv1qUsvswtypDdxn3v4RzFk8
oVDWPIWKZQ7rv6tl7nwlaRgfLFS0eAuVTpiDPniv48CrM5smhn3me5q5vOeD2c4fwUwTfebwAZjp
Qy1z66sx0/XOipYnzDnNrAW0a8/hAz/Cngu669rZYC6ENXNFdXRWXg9z9kXmzj3mQvbHMWsPJHJU
CoU6wxzeZc4a5leMG1mYC5ZZ1RnvhrmgHUSLH2btiK/EXA5vIwmlmbNaxJqZKNp5rxPmivYZ7Tuv
wkzkL9cwE+ULUGWrzNlXHFMOEI+0e8NcMcyMVjwwrJnLmrmMHb6idyNLw7zdUfVu/lp7npb3Nr8P
v8asIPjX9xzJvoRZmIVZmIVZmIVZmIVZmIVZmIVZmIVZmIVZmIVZmIVZmIVZmIVZmIVZmIVZmIVZ
mIVZmIVZmIVZmIVZmIVZmIVZmIVZmIVZmIVZmIVZmIVZmIVZmIVZmIVZmIVZmIVZmIVZmIVZmIVZ
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
Nk1mY2dbeCPCo4zdfboaf1t6Vz89P2creOaki8fIxw7G62ul6r5mRg1OA+CnFwJv7rHZ1OOltKmG
MXLzPi/bd7Idu6fvRT3ncv4pGf/6bi372t3TMrX24GX39AYv5iOtU5XM6eCpcRPJoiuPl1yj9Y3d
sxn+ezGx4u+jz1atA/uWv5uR3pHCN8grGzV17vew9MtmnsWbX48O5a9ZfhtH7RmHt6HNtgfzCSoK
eBtZ3H9UZGLL3nRii92nZk7MGV1GVds2+x5J9FCsvqNocW1xrfbEC3sCVelgI+8jtf3iGgy9ff3y
RoTDs8FzePZsLG0rVKtSXJPfMzqrCT13L+n8p4C1VrQ+K5npOLHLP6nHSOAm6C/+z/vOVuMvZntU
wG7Fghplxn91IahRoo45Fbmr0VvVGPZDs714gupOe1WT8wl/5Hu+njsSjMUdxcmIPX9dGCPzDr51
wbyPz7wrgzGI1i57REH1Zdfb16pXGKOS5LfPvFEx01RdpaDeZjRVbdkf1mlSR9zgCT5qbbk0Psiu
U1plzy0wYhDlye48826ES33sGq55Z53LCNYVsic5zBs9kzAxDtyKab1u2bdZNjicI85tcdey57+V
jZzIPw8/mvrhmk7F9/yVHJ3/S3ho61LfQKlUPdvPeJg78iijqisd1MEYd+Ke/ybWqwuqbtO05dgs
b+tV4ZuRpsZivHdk4qu6oc3o1er7AByPCqk9G8S6FzuiS9XcbnHNZjxv0zJSR6iB2dMqVO9thTfm
2TdGX11g/3apzq7mbOh89Gm/HrHDWT3vWI3XDU182tw9ad5OYXf3Oh6zBCzkhnlDMis//juoB0q2
TX/YulJx/r7BwbKo5iK3mGdzJTKYZY9xPDI1aTK6x0s6JwmzerMa15Hi3ebup/2jKazks20sy74l
if9vO8Qbgexoe8OsfO+Y9woSXxrM+7UanJLxENYpUqNEkIEST132WLWwM3Ge1zuis+F3WWeNTDFe
Hfqa+GTP8dg3nOig2ba4xmybve30qrYq9v2sM1GNPy+OLEcSxFJqlTYG6py2xN3IrTRWqrdeN9xj
crAPqE6Sdz3YwdvJx962lSVUMc7bBLeicJi3ZOs5lFk9/wDLYY3vwbNIdee6rYt8H0uhBos35dvt
+YJs1DO1aHsqRn1weHa8RN71YJv+48VkW9GErdU3d9ud/uMle4bDrKa38faWm5FPn1np3vD8Z9Vo
TxnNVbOf3VHDRJD2FZt35kzO+9i8IeFmRM+pPHrtV1ayUVW+1DefuDyjo0q5khlNFcae9n+43VG8
M3OipclUmu8u2HM8uT0dqV3dKKOXagt2dbGnA+XLnrdWtE7VefR4MjKdYG4xGVFl3v5tn85oXxo7
OvTh9njp2OytGBb/te7HxZh9+q78lS8LTra3V9/E39xdyRS+sadT4OTfL7g8U8ncmbF72B/2n2h5
d2dZP58aFqPn18rOTnVPNK99G/LnFd5jTCav5w/l+QS74HXsLz/KnEoxb/xw+/k6/x7DplkbuG7e
pSj7h+STfPp+n071/sz5tfPnf9Sqx/J/+b6Ogc47+m9/4hx/v1qvFAgEAoFAIBAIBAKBQCAQlL+7
jnJA6igCgUAgEAgEAoFAIBAIBLXYp45yUOooAoFAIBAIBAKBQCAQCAS12KeO8kdSRxEIBAKBQCAQ
CAQCgUAgqMU+dZQ/ljqKQCAQCAQCgUAgEAgEAkEt9qmjvCt1FIFAIBAIBAKBQCAQCASCWuxTRwlJ
HUUgEAgEAoFAIBAIBAKBoBb71FF+KnUUgUAgEAgEAoFAIBAIBIJa7FNH+UdSRxEIBAKBQCAQCAQC
gUAgqMU+dZSfSR1FIBAIBAKBQCAQCAQCgaAW+9RR6qSOIhAIBAKBQCAQCAQCgUBQi33qKP9Y6igC
gUAgEAgEAoFAIBAIBLXYp47yntRRBAKBQCAQCAQCgUAgEAhqsU8dJSx1FIFAIBAIBAKBQCAQCASC
WuxTR4lIHUUgEAgEAoFAIBAIBAKBoBb71FHelzqKQCAQCAQCgUAgEAgEAkEt9qmj/InUUQQCgUAg
EAgEAoFAIBAIarFPHeWQ1FEEAoFAIBAIBAKBQCAQCGqxTx3lsNRRBAKBQCAQCAQCgUAgEAhqsU8d
5Z9IHUUgEAgEAoFAIBAIBAKBoBb71FH+qdRRBAKBQCAQCAQCgUAgEAhqsU8d5QOpowgEAoFAIBAI
BAKBQCAQ1GKfOkpU6igCgUAgEAgEAoFAIBAIBLUwdZT/C7ZFbxqoJQMA
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
 say 'Bootmark artwork configured and read back successfully.'
 say 'Changes take effect at the next normal restart. No restart was requested.'
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

inspect_theme() {
 baseline
 if ! (owned_targets) >/dev/null 2>&1; then say 'BOOTMARK_THEME=foreign'; return; fi
 if ! present "$MARKER"; then say 'BOOTMARK_THEME=stock'; return; fi
 if ! config_matches || ! marker_matches; then say 'BOOTMARK_THEME=foreign'; return; fi
 if matches "$MAIN" "beb3f8acb83ec0b4053c63a26455b3bd7703b0231552824cdefd0a8e0ae1fd01" && matches "$LOOP" "beb3f8acb83ec0b4053c63a26455b3bd7703b0231552824cdefd0a8e0ae1fd01"; then
  say 'BOOTMARK_THEME=kernelsu'
 elif matches "$MAIN" "61d20c6db729b15b2e6e0f3a45989726fd93b7f844a44b37b4be9ac563b6c431" && matches "$LOOP" "61d20c6db729b15b2e6e0f3a45989726fd93b7f844a44b37b4be9ac563b6c431"; then
  say 'BOOTMARK_THEME=fusion'
 elif matches "$MAIN" "$OLD_SHA" && matches "$LOOP" "$OLD_SHA"; then
  say 'BOOTMARK_THEME=legacy'
 elif custom_matches "$MAIN" && custom_matches "$LOOP" && [ "$(digest "$MAIN")" = "$(digest "$LOOP")" ]; then
  say 'BOOTMARK_THEME=custom'
 else say 'BOOTMARK_THEME=foreign'; fi
 say "BOOTMARK_ASSET=$(digest "$MAIN")"
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
  repair|install) repair; inspect_theme ;;
  restore) restore_artwork; inspect_theme ;;
  check) baseline; owned_targets; say 'Checks passed; nothing changed.' ;;
  status) inspect_theme ;;
  *) fail 'Unknown worker mode.' ;;
 esac
 exit
fi
mode=${1:-repair}
if [ "$mode" = verify ]; then verify; exit; fi
case "$mode" in repair|install|restore|check|status) ;; *) fail 'Use repair, restore, check, or verify.' ;; esac
[ "$(id -u)" = 0 ] || fail 'Root must be active; run through su.'
say 'BOOTMARK_ROOT=1'
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
