"""Run Android UI and import tests on the host; no device or root access."""
from pathlib import Path
import os, subprocess, zipfile
ROOT=Path(__file__).resolve().parents[1]
BUILD=ROOT/'build'
SDK=Path(os.environ.get('BOOTMARK_TOOLCHAIN',ROOT/'toolchain'))
LIBS=Path(os.environ.get('BOOTMARK_TEST_LIBS', BUILD/'test-libs'))
if not LIBS.is_dir():
    subprocess.run(['mvn','-B','-f',str(ROOT/'testtools/pom.xml'),'dependency:copy-dependencies','-DincludeScope=test','-DoutputDirectory='+str(LIBS)],check=True)
for aar in LIBS.glob('*.aar'):
    with zipfile.ZipFile(aar) as archive:(LIBS/(aar.stem+'.jar')).write_bytes(archive.read('classes.jar'))
TEST=BUILD/'test-classes';TEST.mkdir(exist_ok=True)
config=TEST/'com/android/tools/test_config.properties';config.parent.mkdir(parents=True,exist_ok=True)
config.write_text('android_merged_assets='+str(ROOT/'app/assets')+'\nandroid_merged_manifest='+str(ROOT/'app/AndroidManifest.xml')+'\nandroid_resource_apk='+str(BUILD/'resources.apk')+'\nandroid_custom_package=dev.dov.bootmark\n')
jars=sorted(LIBS.glob('*.jar'))
compile_cp=os.pathsep.join(map(str,[BUILD/'classes',SDK/'android-36/android.jar',*jars]))
subprocess.run(['java','-jar',str(SDK/'ecj-3.39.0.jar'),'-8','-cp',compile_cp,'-d',str(TEST),str(ROOT/'testtools/LaunchTest.java'),str(ROOT/'testtools/ArtworkTest.java')],check=True)
run_cp=os.pathsep.join(map(str,[TEST,BUILD/'classes',*jars]))
subprocess.run(['java','--add-opens=java.base/java.io=ALL-UNNAMED','-Xmx2g','-Drobolectric.usePreinstrumentedJars=false','-Drobolectric.dependency.dir='+str(LIBS),'-Drobolectric.offline=true','-cp',run_cp,'org.junit.runner.JUnitCore','dev.dov.bootmark.LaunchTest','dev.dov.bootmark.ArtworkTest'],cwd=ROOT,check=True)
