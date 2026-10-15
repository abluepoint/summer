"""Owned reference-key runtime; never retain raw JVM logs or Secret payloads."""
import sys,os,json,subprocess,threading,time,re,urllib.request,socket,base64,shutil
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'keys'))
from key_environment import KeyEnvironment,Client,ROOT,OUT,JAVA,immutable_candidate

class ReferenceEnvironment(KeyEnvironment):
    def __init__(self):
        super().__init__()
        self.private_dir=OUT/'private-runtime'
        self.private_dir.mkdir(mode=0o700)
        self.allowed=self.private_dir/'allowed';self.allowed.mkdir(mode=0o700)
        self.secret_name='summer-t22-'+self.run
        self.pod_name=self.secret_name+'-injection'
        self.secret_created=False;self.pod_created=False
        self.injections={};self.reference_codes=[];self.startups=[]

    def kubectl(self,*args,input=None):
        completed=subprocess.run(['kubectl','--context','docker-desktop','-n','local-base',*args],
                                 input=input,text=True,capture_output=True,timeout=90)
        if completed.returncode:raise RuntimeError('owned_kubernetes_operation_failed')
        return completed.stdout

    def inject_secret(self,pem):
        # Both Secret contents and exec output stay in memory, never in command arguments.
        secret={'apiVersion':'v1','kind':'Secret','metadata':{'name':self.secret_name},'type':'Opaque',
                'data':{'key.pem':base64.b64encode(pem.encode()).decode()}}
        self.kubectl('create','-f','-','-o','name',input=json.dumps(secret));self.secret_created=True
        image=json.loads(self.kubectl('get','pod','redis-0','-o','json'))['spec']['containers'][0]['image']
        if not image.startswith('redis:') or '@sha256:' not in image:raise RuntimeError('expected_pinned_injection_image')
        pod={'apiVersion':'v1','kind':'Pod','metadata':{'name':self.pod_name},'spec':{'restartPolicy':'Never',
             'containers':[{'name':'injection','image':image,'command':['sh','-c','sleep 1800'],
                'env':[{'name':'SUMMER_ACCEPTANCE_KEY','valueFrom':{'secretKeyRef':{'name':self.secret_name,'key':'key.pem'}}}],
                'volumeMounts':[{'name':'keys','mountPath':'/run/reference-key','readOnly':True}]}],
             'volumes':[{'name':'keys','secret':{'secretName':self.secret_name,'defaultMode':256}}]}}
        self.kubectl('create','-f','-','-o','name',input=json.dumps(pod));self.pod_created=True
        self.kubectl('wait','--for=condition=Ready','pod/'+self.pod_name,'--timeout=75s')
        injected_env=self.kubectl('exec',self.pod_name,'--','printenv','SUMMER_ACCEPTANCE_KEY').rstrip('\n')+'\n'
        injected_file=self.kubectl('exec',self.pod_name,'--','cat','/run/reference-key/key.pem')
        if injected_env!=pem or injected_file!=pem:raise RuntimeError('secret_injection_mismatch')
        return injected_env,injected_file

    def write_private(self,name,value):
        path=self.private_dir/name
        fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
        with os.fdopen(fd,'w') as output:output.write(value)
        return path

    def launch(self,jar,label,properties,injections=None):
        port=21081
        if port in self.nodes:self.stop(port)
        self.phase=label
        config=self.properties(port);config.update(properties)
        child=dict(os.environ,SUMMER_SAMPLE_REDIRECT_URI='http://127.0.0.1:21573/oauth2/callback',
                   SUMMER_SAMPLE_POST_LOGOUT_REDIRECT_URI='http://127.0.0.1:21573/login?logout',
                   SPRING_APPLICATION_JSON=json.dumps(config),HOSTNAME='key-reference-'+self.run)
        child.pop('SUMMER_ACCEPTANCE_KEY',None)
        child.update(injections or {})
        started=time.monotonic();code_start=len(self.reference_codes);classes=set();health={}
        process=subprocess.Popen([str(JAVA),'-Xmx512m','-jar',str(jar)],cwd=ROOT,env=child,
                                 stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
        self.nodes[port]=process
        def consume():
            for line in process.stdout:
                known=[self.password,self.denied_password,self.mysql,self.master,self.db_password,*self.additional_secrets]
                if any(value and len(value)>12 and value in line for value in known) or re.search(r'BEGIN (?:RSA )?PRIVATE KEY|eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}',line):
                    self.sensitive_log_detected=True
                    self.sensitive_findings.append({'case':label,'sensitiveMatch':True})
                self.reference_codes.extend(re.findall(r'SUMMER_KEY_REFERENCE_[A-Z_]+',line))
                classes.update(re.findall(r'(?:Caused by: )?((?:org|com|java|tools|io)\.[\w.$]*(?:Exception|Error)):',line))
                event=re.search(r'security_event=([a-z_]+)',line)
                if event:self.security_events.append({'node':port,'event':event[1]})
        reader=threading.Thread(target=consume,daemon=True);reader.start()
        outcome='timeout'
        while time.monotonic()-started<100:
            if process.poll() is not None:outcome='exited';reader.join(timeout=2);break
            try:
                with urllib.request.urlopen(f'http://127.0.0.1:{port}/actuator/health',timeout=1) as response:
                    if response.status==200:outcome='ready';break
            except urllib.error.HTTPError as error:
                health['httpStatus']=error.code
                try:
                    body=json.loads(error.read());value=body.get('status')
                    if value in ['UP','DOWN','OUT_OF_SERVICE','UNKNOWN']:health['status']=value
                except Exception:pass
            except Exception as error:health['transportFailureType']=type(error).__name__
            time.sleep(.25)
        item={'case':label,'outcome':outcome,'exitCode':process.poll(),'elapsedSeconds':round(time.monotonic()-started,2),
              'codes':sorted(set(self.reference_codes[code_start:])),'exceptionClasses':sorted(classes),'health':health}
        self.startups.append(item)
        return item

    def close(self):
        failures=[]
        for kind,name,created in [('pod',self.pod_name,self.pod_created),('secret',self.secret_name,self.secret_created)]:
            if created:
                try:self.kubectl('delete',kind,name,'--wait=true','--timeout=45s','--ignore-not-found=true')
                except Exception:failures.append(kind)
        shutil.rmtree(self.private_dir)
        super().close()
        for kind,name in [('pod',self.pod_name),('secret',self.secret_name)]:
            if self.kubectl('get',kind,name,'--ignore-not-found=true','-o','name').strip():failures.append(kind+'_remaining')
        cleanup=json.loads((OUT/'cleanup.json').read_text())
        cleanup.update(secret_absent='secret' not in failures and 'secret_remaining' not in failures,
                       injection_pod_absent='pod' not in failures and 'pod_remaining' not in failures,
                       private_runtime_directory_absent=not self.private_dir.exists())
        (OUT/'cleanup.json').write_text(json.dumps(cleanup,indent=2))
        if failures:raise RuntimeError('reference_resources_cleanup_failed')
