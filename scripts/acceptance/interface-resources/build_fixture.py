import hashlib, json, os, subprocess, zipfile
from pathlib import Path
from environment import ROOT, OUT, JAVA
CANDIDATE=Path(os.environ.get('CATALOG_CANDIDATE_PATH',str(ROOT/'.git/summer4-closeout-20261015/runtime/candidate-4c81/candidate.jar')))
EXPECTED=os.environ.get('CATALOG_CANDIDATE_SHA256','4c81a803dd19f610999a8a8b5b336b4a25771af82a981a3a47e79a5c3e86eeae')
assert hashlib.sha256(CANDIDATE.read_bytes()).hexdigest()==EXPECTED, 'candidate_digest_mismatch'
libs=OUT/'fixture-libs';app=OUT/'fixture-app';classes=OUT/'fixture-classes'
for directory in [libs,app,classes]:directory.mkdir(exist_ok=True)
with zipfile.ZipFile(CANDIDATE) as archive:
    assert 'org/springframework/boot/loader/launch/PropertiesLauncher.class' in archive.namelist()
    for item in archive.namelist():
        if item.startswith('BOOT-INF/lib/') and item.endswith('.jar'):(libs/Path(item).name).write_bytes(archive.read(item))
        if item.startswith('BOOT-INF/classes/') and not item.endswith('/'):
            target=app/item.removeprefix('BOOT-INF/classes/');target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(archive.read(item))
source=Path(__file__).with_name('InterfaceResourceAcceptanceConfiguration.java')
r=subprocess.run([str(JAVA.parent/'javac'),'-proc:none','-cp',str(libs/'*')+os.pathsep+str(app),'-d',str(classes),str(source)],capture_output=True)
if r.returncode:raise RuntimeError('fixture_compile_failed')
fixture=OUT/'interface-resource-fixture.jar'
subprocess.run([str(JAVA.parent/'jar'),'cf',str(fixture),'-C',str(classes),'.'],check=True,capture_output=True)
(OUT/'fixture-build.json').write_text(json.dumps({'candidate_sha256':EXPECTED,'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'fixture_sha256':hashlib.sha256(fixture.read_bytes()).hexdigest(),'test_only':True},indent=2))
print('fixture_compiled_against_frozen_candidate')
