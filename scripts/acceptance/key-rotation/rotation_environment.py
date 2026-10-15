"""T23 isolated runtime; no raw runtime log is retained. Reuses the identity cleanup owner."""
import sys, os, json, secrets, subprocess, threading, time, re, urllib.request, urllib.error
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'identity'))
from environment import Environment as IdentityEnvironment, Client, ROOT, OUT, JAVA, immutable_candidate

class RotationEnvironment(IdentityEnvironment):
    def __init__(self):
        super().__init__()
        self.master=secrets.token_urlsafe(32)
        self.named_secrets={};self.sensitive_findings=[];self.phase='setup'

    def start(self,port,jar=None,database=None):
        assert jar is not None,'immutable_candidate_required'
        properties=self.properties(port,database);properties.update(self.extra)
        e=dict(os.environ, SUMMER_SAMPLE_REDIRECT_URI='http://127.0.0.1:21573/oauth2/callback',
               SUMMER_SAMPLE_POST_LOGOUT_REDIRECT_URI='http://127.0.0.1:21573/login?logout',
               SPRING_APPLICATION_JSON=json.dumps(properties),HOSTNAME='keys-'+str(port))
        p=subprocess.Popen([str(JAVA),'-Xmx512m','-jar',str(jar)],cwd=ROOT,env=e,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
        self.nodes[port]=p
        def read():
            for line in p.stdout:
                known={'bootstrap_password':self.password,'reader_password':self.denied_password,'mysql_admin_password':self.mysql,'master_key':self.master,'database_password':self.db_password,**self.named_secrets}
                matches=[name for name,value in known.items() if value and value in line]
                if any(value and value in line for value in self.additional_secrets):matches.append('additional_input')
                if re.search(r'BEGIN (?:RSA )?PRIVATE KEY',line):matches.append('private_pem_pattern')
                if re.search(r'eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}',line):matches.append('jwt_pattern')
                if matches:
                    self.sensitive_log_detected=True
                    event=re.search(r'security_event=([a-z_]+)',line)
                    self.sensitive_findings.append({'node':port,'phase':self.phase,'secretKinds':matches,'event':event[1] if event else None,'bearerInPrincipalField':bool(self.named_secrets.get('bearer_token') and ('principal='+self.named_secrets['bearer_token']) in line)})
                audit=re.search(r'security_event=([a-z_]+)',line)
                if audit:
                    record={'node':port,'event':audit[1]}
                    if audit[1] in ('security_key_rotated','security_key_rotation_failed'):
                        record['operatorMatchesAdmin']=('principal='+self.username+' attributes=') in line
                        for field, pattern in [('requestId',r'requestId=([A-Za-z0-9_-]{1,128})'),('result',r'result=(SUMMER_[A-Z_]+|FAILED)'),('version',r'version=([0-9]+)')]:
                            match=re.search(pattern,line)
                            if match:record[field]=match[1]
                    self.security_events.append(record)
                error=re.search(r'(?:Caused by: )?((?:org|com|java|tools|io)\.[\w.$]*(?:Exception|Error)):',line)
                if error:self.diagnostics.append({'node':port,'class':error[1]})
        threading.Thread(target=read,daemon=True).start()
        deadline=time.monotonic()+120
        while time.monotonic()<deadline:
            if p.poll() is not None:raise RuntimeError('node_startup_failed')
            try:
                with urllib.request.urlopen(f'http://127.0.0.1:{port}/actuator/health',timeout=2) as response:
                    if response.status==200:print('node_ready',port,flush=True);return
            except Exception:pass
            time.sleep(1)
        raise RuntimeError('node_startup_timeout')
