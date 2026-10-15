import base64, hashlib, http.cookiejar, json, os, secrets, socket, subprocess, threading, time
import urllib.error, urllib.parse, urllib.request, uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(os.environ.get('SUMMER_RESILIENCE_BROWSER_EVIDENCE_DIR', str(ROOT / '.git/summer4-closeout-20261015/t32-ui-live'))).resolve()
OUT.mkdir(parents=True, exist_ok=True)
SCRIPT = Path(__file__).resolve().parent
JAVA = Path.home()/'.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/jdk-21.0.12+8/Contents/Home/bin/java'

def clear_redis_namespace(password, run, port=6379):
    import re
    if not re.fullmatch(r'[a-f0-9]{10}', run):
        raise RuntimeError('invalid_acceptance_namespace')
    script = "local n=0;local c='0';repeat local r=redis.call('SCAN',c,'MATCH',ARGV[1],'COUNT',200);c=r[1];if #r[2]>0 then n=n+redis.call('DEL',unpack(r[2]));end;until c=='0';return n"
    def resp(*args):
        return b'*'+str(len(args)).encode()+b'\r\n'+b''.join(b'$'+str(len(str(arg).encode())).encode()+b'\r\n'+str(arg).encode()+b'\r\n' for arg in args)
    with socket.create_connection(('127.0.0.1', port), timeout=3) as connection:
        connection.settimeout(5)
        connection.sendall(resp('AUTH','default',password)+resp('SELECT',15)+resp('EVAL',script,0,'summer:t32ui:'+run+':*'))
        reader=connection.makefile('rb')
        for _ in range(2):
            if reader.readline().strip()!=b'+OK':raise RuntimeError('redis_namespace_cleanup_auth_failed')
        reply=reader.readline().strip()
        if not reply.startswith(b':'):raise RuntimeError('redis_namespace_cleanup_failed')
        return int(reply[1:])

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args): return None

class Client:
    observations=[]
    def __init__(self):
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies), NoRedirect())
        self.token = None
    def request(self, port, path, method='GET', body=None, headers=None, bearer=True):
        h = {'Host':'127.0.0.1:28573', 'X-Forwarded-Host':'127.0.0.1:28573', 'X-Forwarded-Proto':'http'}
        if self.token and bearer: h['Authorization']='Bearer '+self.token
        if isinstance(body, dict): body=json.dumps(body).encode();h['Content-Type']='application/json'
        if isinstance(body, str): body=body.encode();h['Content-Type']='application/x-www-form-urlencoded'
        h.update(headers or {})
        req=urllib.request.Request('http://127.0.0.1:'+str(port)+path,data=body,headers=h,method=method)
        try:r=self.opener.open(req,timeout=15)
        except urllib.error.HTTPError as e:r=e
        with r:
            raw=r.read()
            try:data=json.loads(raw)
            except (ValueError,UnicodeDecodeError):data=None
            error=data.get('error') if isinstance(data,dict) else None
            code=error.get('code') if isinstance(error,dict) else None
            import re
            code=code if isinstance(code,str) and re.fullmatch(r'[A-Z0-9_]+',code) else None
            self.observations.append({'method':method,'path':urllib.parse.urlsplit(path).path,'status':r.status,'code':code,'requestId':r.headers.get('X-Request-Id'),'ifMatch':h.get('If-Match')})
            return r.status,data,r.headers
    def login(self, port, username, password):
        self.token=None
        self.cookies.clear()
        status,csrf,_=self.request(port,'/csrf',bearer=False)
        assert status==200,'csrf_status_'+str(status)
        body=urllib.parse.urlencode({'username':username,'password':password,csrf['parameterName']:csrf['token']})
        status,_,_=self.request(port,'/login','POST',body,bearer=False)
        assert status==302,'login_status_'+str(status)
        verifier=secrets.token_urlsafe(48)
        challenge=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
        callback='http://127.0.0.1:28573/oauth2/callback'
        query=urllib.parse.urlencode({'response_type':'code','client_id':'summer-ui','scope':'openid profile customer.read customer.write','redirect_uri':callback,'code_challenge_method':'S256','code_challenge':challenge})
        status,_,h=self.request(port,'/oauth2/authorize?'+query,bearer=False)
        assert status==302,'authorize_status_'+str(status)
        params=urllib.parse.parse_qs(urllib.parse.urlsplit(h['Location']).query)
        assert 'code' in params,'authorization_code_absent'
        body=urllib.parse.urlencode({'grant_type':'authorization_code','client_id':'summer-ui','code':params['code'][0],'redirect_uri':callback,'code_verifier':verifier})
        status,tokens,_=self.request(port,'/oauth2/token','POST',body,bearer=False)
        assert status==200,'token_status_'+str(status)
        self.token=tokens['access_token']
        return self

class Environment:
    def __init__(self):
        self.run=uuid.uuid4().hex[:10];self.schema='summer_resilience_ui_'+self.run;self.restore=self.schema+'_identity_restore'
        self.container='summer-closeout-'+self.run;self.nodes={};self.created_schema=False;self.created_redis=False
        self.username='t32ui-admin';self.password=secrets.token_urlsafe(24);self.denied_username='t32ui-reader';self.denied_password=secrets.token_urlsafe(24)
        self.mysql=self.secret('mysql-auth','root-password');self.master=self.secret('mysql-auth','app-password');self.redis=''
        self.db_user='t32ui_'+self.run;self.db_password=secrets.token_urlsafe(32);self.created_user=False;self.extra={};self.allow_degraded=False;self.diagnostics=[];self.restore_created=False;self.frozen=None;self.security_events=[];self.sensitive_log_detected=False;self.additional_secrets=[]
    def secret(self,name,key):
        return base64.b64decode(subprocess.check_output(['kubectl','--context','docker-desktop','get','secret',name,'-n','local-base','-o',f'jsonpath={{.data.{key}}}'])).decode()
    def sql(self,statement,database=None):
        # Only infrastructure provisioning/cleanup and read-only evidence. Business grants use HTTP.
        if not statement.lstrip().upper().startswith(('SELECT ','CREATE DATABASE ','DROP DATABASE ','CREATE USER ','DROP USER ','GRANT ')):
            raise RuntimeError('business_sql_write_forbidden')
        args=['kubectl','--context','docker-desktop','exec','-i','-n','local-base','mysql-0','--','sh','-c','IFS= read -r MYSQL_PWD; export MYSQL_PWD; exec mysql -uroot --batch --skip-column-names']
        body=self.mysql+'\n'+('USE `'+database+'`;\n' if database else '')+statement+'\n'
        r=subprocess.run(args,input=body,text=True,capture_output=True)
        if r.returncode:
            import re
            match=re.search(r'ERROR (\d+)',r.stderr)
            raise RuntimeError('sql_error_'+(match.group(1) if match else 'unknown'))
        return r.stdout.strip()
    def setup(self):
        for port in [28081,28082,28083,28573,28580]:
            with socket.socket() as s:
                s.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1);s.bind(('127.0.0.1',port))
        assert self.sql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='"+self.schema+"'")=='0'
        self.sql('CREATE DATABASE `'+self.schema+'`');self.created_schema=True
        self.sql("CREATE USER '"+self.db_user+"'@'%' IDENTIFIED BY '"+self.db_password+"'");self.created_user=True
        self.sql("GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,DROP,REFERENCES ON `"+self.schema+"`.* TO '"+self.db_user+"'@'%'")
        subprocess.run(['docker','image','inspect','redis:7-alpine'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        subprocess.run(['docker','run','-d','--rm','--name',self.container,'-p','127.0.0.1::6379','redis:7-alpine','redis-server','--protected-mode','no','--save','','--appendonly','no'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        self.created_redis=True
        published=subprocess.check_output(['docker','port',self.container,'6379/tcp'],text=True).strip()
        self.redis_port=int(published.rsplit(':',1)[1])
        (OUT/'resources.json').write_text(json.dumps({'schema':self.schema,'container':self.container,'ports':[28081,28082,28083],'run':self.run}))
    def properties(self, port, database=None):
        properties={'server.port':port,'server.forward-headers-strategy':'framework','spring.datasource.url':f'jdbc:mysql://127.0.0.1:3306/{database or self.schema}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC','spring.datasource.username':self.db_user,'spring.datasource.password':self.db_password,'spring.data.redis.host':'127.0.0.1','spring.data.redis.port':self.redis_port,'spring.data.redis.username':'','spring.data.redis.password':self.redis,'spring.data.redis.timeout':'2s','spring.data.redis.database':15,'spring.session.data.redis.namespace':'summer:t32ui:'+self.run+':servlet','summer.sample.session-index.namespace':'summer:t32ui:'+self.run+':index','summer.sample.session-index.timeout-ms':2000,'summer.sample.security.master-key':self.master,'summer.security.storage.keys':'mysql','summer.security.authorization-server.issuer':'http://127.0.0.1:28573','summer.security.authorization-server.login-page':'http://127.0.0.1:28573/login','summer.security.authorization-server.user.username':self.username,'summer.security.authorization-server.user.password':self.password,'summer.sample.cache.node-id':'t32ui-'+str(port),'summer.sample.realtime.redis-enabled':True,'spring.quartz.properties.org.quartz.scheduler.instanceId':'t32ui-'+str(port),'spring.quartz.properties.org.quartz.jobStore.clusterCheckinInterval':1000,'summer.sample.authorization.legacy-scope-compatibility':False,'logging.level.com.abluepoint.summer.security.audit':'INFO','logging.level.root':'ERROR','spring.main.banner-mode':'off'}
        return properties

    def start(self,port,jar=None,database=None,acceptance_fixture=None):
        properties=self.properties(port,database)
        properties.update(self.extra)
        e=os.environ.copy();e['SUMMER_SAMPLE_REDIRECT_URI']='http://127.0.0.1:28573/oauth2/callback';e['SUMMER_SAMPLE_POST_LOGOUT_REDIRECT_URI']='http://127.0.0.1:28573/login?logout';e['SPRING_APPLICATION_JSON']=json.dumps(properties);e['HOSTNAME']='t32ui-'+str(port)
        if jar is None:
            if self.frozen is None:
                import shutil
                self.frozen=OUT/('run-'+self.run+'.jar');shutil.copy2(ROOT/'summer-sample/build/libs/summer-sample.jar',self.frozen)
                (OUT/('artifact-'+self.run+'.json')).write_text(json.dumps({'sha256':hashlib.sha256(self.frozen.read_bytes()).hexdigest(),'run':self.run}))
            jar=self.frozen
        jar=Path(jar)
        command=[str(JAVA),'-Xmx512m','-jar',str(jar)]
        if acceptance_fixture:
            properties['spring.main.sources']='acceptance.CountedTaskConfiguration'
            e['SPRING_APPLICATION_JSON']=json.dumps(properties)
            command=[str(JAVA),'-Xmx512m','-Dloader.path='+str(acceptance_fixture),'-cp',str(jar),'org.springframework.boot.loader.launch.PropertiesLauncher']
        p=subprocess.Popen(command,cwd=ROOT,env=e,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
        self.nodes[port]=p
        def read():
            import re
            for line in p.stdout:
                if any(value and value in line for value in [self.password,self.denied_password,self.mysql,self.master,self.db_password,*self.additional_secrets]):self.sensitive_log_detected=True
                audit=re.search(r'security_event=(authentication_success|authentication_failure) principal=([^ ]+)',line)
                if audit:self.security_events.append({'node':port,'event':audit.group(1),'principal':audit.group(2)})
                m=re.search(r'(?:Caused by: )?((?:org|com|java|tools|io)\.[\w.$]*(?:Exception|Error)):',line)
                if m:self.diagnostics.append({'node':port,'class':m.group(1)})
        threading.Thread(target=read,daemon=True).start()
        deadline=time.monotonic()+120
        while time.monotonic()<deadline:
            if p.poll() is not None:raise RuntimeError('node_'+str(port)+'_startup_exit_'+str(p.returncode))
            try:
                with urllib.request.urlopen(f'http://127.0.0.1:{port}/actuator/health',timeout=2) as r:
                    if r.status==200:
                        print('node_ready',port,flush=True);return
            except urllib.error.HTTPError as error:
                if error.code==503 and self.allow_degraded:
                    print('node_ready_degraded',port,flush=True);return
            except Exception:pass
            time.sleep(1)
        raise RuntimeError('node_'+str(port)+'_startup_timeout')
    def stop(self,port,kill=False):
        p=self.nodes.pop(port)
        if p.poll() is None:
            p.kill() if kill else p.terminate()
            try:p.wait(timeout=20)
            except subprocess.TimeoutExpired:p.kill();p.wait()
    def close(self):
        for port in list(self.nodes):self.stop(port)
        if self.frozen and self.frozen.exists():self.frozen.unlink()
        if self.created_redis:subprocess.run(['docker','rm','-f',self.container],check=True,stdout=subprocess.DEVNULL)
        if self.restore_created:self.sql('DROP DATABASE `'+self.restore+'`')
        if self.created_schema:self.sql('DROP DATABASE `'+self.schema+'`')
        if self.created_user:self.sql("DROP USER '"+self.db_user+"'@'%'")
        ports={}
        for port in [28081,28082,28083,28573,28580]:
            with socket.socket() as s:ports[str(port)]=s.connect_ex(('127.0.0.1',port))==0
        result={'schema_absent':self.sql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name IN ('"+self.schema+"','"+self.restore+"')")=='0','container_absent':self.container not in subprocess.check_output(['docker','ps','-a','--format','{{.Names}}'],text=True).splitlines(),'redis_isolated_container_destroyed':True,'listeners':ports,'app_account_absent':self.sql("SELECT COUNT(*) FROM mysql.user WHERE User='"+self.db_user+"'")=='0'}
        (OUT/'cleanup.json').write_text(json.dumps(result,indent=2));print('cleanup',json.dumps(result),flush=True)
        if not (result['schema_absent'] and result['container_absent'] and result['app_account_absent'] and not any(result['listeners'].values())):raise RuntimeError('cleanup_not_verified')


def immutable_candidate():
    candidate = Path(os.environ.get('SUMMER_RESILIENCE_BROWSER_CANDIDATE', str(OUT / 'candidate.jar'))).resolve()
    expected = os.environ.get('SUMMER_RESILIENCE_BROWSER_CANDIDATE_SHA256')
    if not expected or len(expected) != 64:
        raise RuntimeError('SUMMER_RESILIENCE_BROWSER_CANDIDATE_SHA256 is required')
    data = candidate.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    if digest != expected:
        raise RuntimeError('candidate_sha256_mismatch')
    pinned = candidate
    (OUT / 'candidate-provenance.json').write_text(json.dumps({'sha256': digest, 'source': str(candidate)}, indent=2))
    return pinned
