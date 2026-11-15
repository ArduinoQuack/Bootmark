"""Download the app build tools from their publishers; verify pinned hashes."""
from pathlib import Path
import hashlib, os, urllib.request, zipfile
ROOT=Path(__file__).resolve().parents[1]
SDK=Path(os.environ.get('BOOTMARK_TOOLCHAIN', ROOT/'toolchain'))
SDK.mkdir(parents=True,exist_ok=True)
FILES=[
 ('build-tools.zip','https://dl.google.com/android/repository/build-tools_r36_linux.zip','5d9ac77fb6ff43d9da518a337b4fcf8f9097113df531d99ccefe80ef7ce8250b'),
 ('platform.zip','https://dl.google.com/android/repository/platform-36_r02.zip','37607369a28c5b640b3a7998868d45898ebcb777565a0e85f9acf36f29631d2e'),
 ('ecj-3.39.0.jar','https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.39.0/ecj-3.39.0.jar','01f5a92ac19bb2b3bf85e295a68f2c73c264369109158b566ce9b490af982948')]
for name,url,expected in FILES:
    file=SDK/name
    if not file.is_file() or hashlib.sha256(file.read_bytes()).hexdigest()!=expected:
        temporary=file.with_suffix(file.suffix+'.part')
        with urllib.request.urlopen(url,timeout=60) as source,temporary.open('wb') as output:
            while chunk:=source.read(1024*1024):output.write(chunk)
        if hashlib.sha256(temporary.read_bytes()).hexdigest()!=expected:
            temporary.unlink();raise SystemExit('Checksum mismatch: '+name)
        temporary.replace(file)
    if name.endswith('.zip'):
        with zipfile.ZipFile(file) as archive:
            for entry in archive.infolist():
                if not (SDK/entry.filename).resolve().is_relative_to(SDK.resolve()):raise ValueError('Unsafe archive path')
            archive.extractall(SDK)
    print('Ready:',name)
for name in ['aapt2','zipalign']:(SDK/'android-16'/name).chmod(0o755)
