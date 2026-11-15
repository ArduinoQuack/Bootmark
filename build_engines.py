"""Package the proven v2 transaction engine as private app assets."""
from pathlib import Path
import hashlib,gzip,base64,json
ROOT=Path(__file__).resolve().parent
WORKSPACE=ROOT.parent
kernel=(ROOT/'artwork/KernelSU-Stock.qmg').read_bytes()
fusion_hash='61d20c6db729b15b2e6e0f3a45989726fd93b7f844a44b37b4be9ac563b6c431'
hashes={'kernelsu':hashlib.sha256(kernel).hexdigest(),'fusion':fusion_hash}
base=(ROOT/'tools/repair_template.sh').read_text()
oldv2='61d20c6db729b15b2e6e0f3a45989726fd93b7f844a44b37b4be9ac563b6c431'
for name,asset in [('kernelsu',kernel),('custom',None)]:
 s=base
 s=s.replace('SIGNATURE=SamsungPlusAppleBoot-v2','SIGNATURE=Bootmark-v1')
 s=s.replace('STATE=/data/adb/samsung-plus-apple-boot-v2','STATE=/data/adb/bootmark-v1')
 s=s.replace('RECEIPT=$BASE/Samsung-Plus-Apple-Boot-v2-receipt.txt','RECEIPT=$BASE/Bootmark-receipt.txt')
 s=s.replace('"$OLD_STATE:$OLD_SIGNATURE" "$STATE:$SIGNATURE"',
             '"$OLD_STATE:$OLD_SIGNATURE" "/data/adb/samsung-plus-apple-boot-v2:SamsungPlusAppleBoot-v2" "$STATE:$SIGNATURE"')
 s=s.replace('matches "$path" "$OLD_SHA" || matches "$path" "$ASSET_SHA"',
             'matches "$path" "$OLD_SHA" || matches "$path" "'+oldv2+'" || matches "$path" "'+hashes['kernelsu']+'" || matches "$path" "$ASSET_SHA"')
 s=s.replace('|| matches "$path" "$ASSET_SHA"; then known=1', '|| matches "$path" "$ASSET_SHA" || custom_matches "$path"; then known=1')
 ledger='''custom_matches() {
  [ -f "$STATE/custom-hashes" ] && [ ! -L "$STATE/custom-hashes" ] || return 1
  [ -f "$1" ] && [ ! -L "$1" ] || return 1
  hash=$(digest "$1")
  grep -Fx "$hash" "$STATE/custom-hashes" >/dev/null
}
'''
 s=s.replace('owned_targets() {',ledger+'\nowned_targets() {')
 if name=='custom':
  s=s.replace('need_hash "$STATE/payload.qmg" "$ASSET_SHA"', '''need_hash "$STATE/payload.qmg" "$ASSET_SHA"
 if present "$STATE/custom-hashes"; then
  [ -f "$STATE/custom-hashes" ] && [ ! -L "$STATE/custom-hashes" ] || fail 'Unknown custom hash record.'
 fi
 printf '%s\\n' "$ASSET_SHA" >> "$STATE/custom-hashes"
 chmod 0600 "$STATE/custom-hashes"''')
 # Root confirmation is emitted only after checking the actual effective UID.
 s=s.replace('[ "$(id -u)" = 0 ] || fail \'Root must be active; run through su.\'',
             '[ "$(id -u)" = 0 ] || fail \'Root must be active; run through su.\'\nsay \'BOOTMARK_ROOT=1\'')
 s=s.replace("say 'V2 STAGED: native-size, 24-frame SAMSUNG + Apple artwork in the system-service mount namespace.'",
             "say 'Bootmark artwork configured and read back successfully.'")
 s=s.replace("say 'Readback, labels and hashes passed. First startup with v2 is not confirmed.'",
             "say 'Changes take effect at the next normal restart. No restart was requested.'")
 status='''inspect_theme() {
 baseline
 if ! (owned_targets) >/dev/null 2>&1; then say 'BOOTMARK_THEME=foreign'; return; fi
 if ! present "$MARKER"; then say 'BOOTMARK_THEME=stock'; return; fi
 if ! config_matches || ! marker_matches; then say 'BOOTMARK_THEME=foreign'; return; fi
 if matches "$MAIN" "KERNEL_HASH" && matches "$LOOP" "KERNEL_HASH"; then
  say 'BOOTMARK_THEME=kernelsu'
 elif matches "$MAIN" "FUSION_HASH" && matches "$LOOP" "FUSION_HASH"; then
  say 'BOOTMARK_THEME=fusion'
 elif matches "$MAIN" "$OLD_SHA" && matches "$LOOP" "$OLD_SHA"; then
  say 'BOOTMARK_THEME=legacy'
 elif custom_matches "$MAIN" && custom_matches "$LOOP" && [ "$(digest "$MAIN")" = "$(digest "$LOOP")" ]; then
  say 'BOOTMARK_THEME=custom'
 else say 'BOOTMARK_THEME=foreign'; fi
 say "BOOTMARK_ASSET=$(digest "$MAIN")"
}
'''.replace('KERNEL_HASH',hashes['kernelsu']).replace('FUSION_HASH',hashes['fusion'])
 s=s.replace('# Use SurfaceFlinger\'s namespace',status+'\n# Use SurfaceFlinger\'s namespace')
 s=s.replace('repair|install) repair ;;','repair|install) repair; inspect_theme ;;')
 s=s.replace('restore) restore_artwork ;;','restore) restore_artwork; inspect_theme ;;')
 s=s.replace("  check) baseline; owned_targets; say 'Boot-namespace checks passed; nothing changed.' ;;",
             "  check) baseline; owned_targets; say 'Checks passed; nothing changed.' ;;\n  status) inspect_theme ;;")
 s=s.replace('case "$mode" in repair|install|restore|check)', 'case "$mode" in repair|install|restore|check|status)')
 if asset is not None:
  s=s.replace('__ASSET_SHA__',hashes[name])
  z=gzip.compress(asset,compresslevel=9,mtime=0)
  s=s.replace('__GZIP_SHA__',hashlib.sha256(z).hexdigest()).replace('__ASSET_BASE64__',base64.encodebytes(z).decode().rstrip())
  assert '__ASSET_' not in s
 out=ROOT/'app/assets'/('custom-engine.template' if asset is None else name+'-engine.sh');out.write_text(s)
 print(name,len(s.encode()),'engine bytes')
(ROOT/'app/assets/theme-hashes.json').write_text(json.dumps(hashes,indent=2)+'\n')
(ROOT/'theme-hashes.json').write_text(json.dumps(hashes,indent=2)+'\n')
