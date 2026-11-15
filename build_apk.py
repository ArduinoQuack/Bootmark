"""Build Bootmark with Android SDK 36 build tools and ECJ."""
from pathlib import Path
import hashlib, json, os, secrets, shutil, subprocess, zipfile
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parent
SDK = Path(os.environ.get('BOOTMARK_TOOLCHAIN', ROOT / 'toolchain'))
TOOLS = SDK / 'android-16'
ANDROID = SDK / 'android-36/android.jar'
BUILD = ROOT / 'build'
ANDROID_NS = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(ROOT/'app/AndroidManifest.xml').getroot()
version = manifest.attrib[ANDROID_NS+'versionName']
for name in ['gen', 'classes', 'dex']:
    shutil.rmtree(BUILD/name, ignore_errors=True)
    (BUILD/name).mkdir(parents=True, exist_ok=True)
def run(args):
    result = subprocess.run(list(map(str,args)), capture_output=True, text=True)
    if result.returncode:
        print(result.stdout); print(result.stderr)
        raise SystemExit(result.returncode)
    return result.stdout + result.stderr
run([TOOLS/'aapt2','compile','--dir',ROOT/'app/res','-o',BUILD/'resources.zip'])
run([TOOLS/'aapt2','link','-o',BUILD/'resources.apk','-I',ANDROID,'--manifest',ROOT/'app/AndroidManifest.xml',
     '--java',BUILD/'gen','--min-sdk-version','31','--target-sdk-version','36','-A',ROOT/'app/assets',BUILD/'resources.zip'])
sources=sorted((ROOT/'app/src').rglob('*.java'))+sorted((BUILD/'gen').rglob('*.java'))
(BUILD/'compiler.txt').write_text(run(['java','-jar',SDK/'ecj-3.39.0.jar','-8','-bootclasspath',str(ANDROID)+os.pathsep+str(TOOLS/'core-lambda-stubs.jar'),'-d',BUILD/'classes',*sources]))
run(['java','-cp',TOOLS/'lib/d8.jar','com.android.tools.r8.D8','--min-api','31','--lib',ANDROID,'--output',BUILD/'dex',*sorted((BUILD/'classes').rglob('*.class'))])
with zipfile.ZipFile(BUILD/'resources.apk') as source, zipfile.ZipFile(BUILD/'unsigned.apk','w') as target:
    for info in source.infolist(): target.writestr(info,source.read(info))
    for path in sorted((BUILD/'dex').glob('*.dex')): target.write(path,path.name,compress_type=zipfile.ZIP_DEFLATED)
run([TOOLS/'zipalign','-p','-f','4',BUILD/'unsigned.apk',BUILD/'aligned.apk'])
key = Path(os.environ.get('BOOTMARK_KEYSTORE', BUILD/'local-signing/bootmark.p12'))
password = Path(os.environ.get('BOOTMARK_PASSWORD_FILE', BUILD/'local-signing/password.txt'))
if not key.exists():
    if 'BOOTMARK_KEYSTORE' in os.environ: raise SystemExit('Specified signing key does not exist.')
    key.parent.mkdir(parents=True,exist_ok=True)
    password.write_text(secrets.token_urlsafe(32)+'\n');password.chmod(0o600)
    run(['keytool','-genkeypair','-storetype','PKCS12','-keystore',key,'-storepass:file',password,'-keypass:file',password,
         '-alias','bootmark','-keyalg','RSA','-keysize','3072','-validity','9125','-dname','CN=Bootmark'])
    key.chmod(0o600)
destination = ROOT/'dist'/('Bootmark-'+version+'.apk');destination.parent.mkdir(exist_ok=True)
run(['java','-jar',TOOLS/'lib/apksigner.jar','sign','--ks',key,'--ks-key-alias','bootmark','--ks-pass','file:'+str(password),'--out',destination,BUILD/'aligned.apk'])
(BUILD/'signature.txt').write_text(run(['java','-jar',TOOLS/'lib/apksigner.jar','verify','--verbose','--print-certs',destination]))
(BUILD/'badging.txt').write_text(run([TOOLS/'aapt2','dump','badging',destination]))
result = {'apk':destination.name,'bytes':destination.stat().st_size,'sha256':hashlib.sha256(destination.read_bytes()).hexdigest(),
          'package':'dev.dov.bootmark','version':version,'version_code':int(manifest.attrib[ANDROID_NS+'versionCode']),
          'min_sdk':31,'target_sdk':36,'permissions':[],'background_services':0,'boot_receivers':0}
(BUILD/'release-manifest.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
