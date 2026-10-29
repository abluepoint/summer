"""Compile only the external registered task handler, with annotation processing disabled."""
import hashlib,json,os,subprocess,zipfile
from pathlib import Path
from environment import JAVA,OUT,SCRIPT,immutable_candidate
candidate=immutable_candidate();build=OUT/'fixture';libs=build/'libs';classes=build/'classes';libs.mkdir(parents=True);classes.mkdir()
with zipfile.ZipFile(candidate) as jar:
    paths=[]
    for name in jar.namelist():
        if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
            target=libs/Path(name).name;target.write_bytes(jar.read(name));paths.append(target)
    name='com/abluepoint/summer/sample/task/domain/RegisteredTaskHandler.class'
    target=classes/name;target.parent.mkdir(parents=True);target.write_bytes(jar.read('BOOT-INF/classes/'+name))
subprocess.run([str(JAVA.with_name('javac')),'-proc:none','-cp',os.pathsep.join(map(str,[classes,*paths])),'-d',str(classes),str(SCRIPT/'SseTaskConfiguration.java')],check=True,capture_output=True)
# Keep only our fixture classes in the external artifact; the interface comes from production.
import shutil
shutil.rmtree(classes/'com')
fixture=OUT/'sse-task-fixture.jar';subprocess.run([str(JAVA.with_name('jar')),'--create','--file',str(fixture),'-C',str(classes),'.'],check=True,capture_output=True)
(OUT/'fixture-provenance.json').write_text(json.dumps({'candidateSha256':hashlib.sha256(candidate.read_bytes()).hexdigest(),'fixtureSha256':hashlib.sha256(fixture.read_bytes()).hexdigest(),'sourceSha256':hashlib.sha256((SCRIPT/'SseTaskConfiguration.java').read_bytes()).hexdigest(),'role':'RegisteredTaskHandler only; actual Quartz invokes progress callback','businessSqlWrites':False,'syntheticIdentity':False},indent=2))
shutil.rmtree(build)
print('fixture_compiled')
