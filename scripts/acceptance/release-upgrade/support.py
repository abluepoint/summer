"""Small native acceptance helpers; records contain status/correlation, never credentials."""
import base64,hashlib,json,os,shutil,socket,time,uuid
from pathlib import Path
from environment import OUT,SCRIPT,ROOT

class Evidence:
    def __init__(self):
        self.result={'issue':144,'checks':[],'businessSqlWrites':False,'networkMocking':False,'stage':'setup'}
    def save(self):
        r=self.result;c=r['checks'];r['counts']={'tests':len(c),'failures':sum(not x['passed']for x in c),'errors':int('errorType'in r),'skipped':0}
        temporary=OUT/'result.tmp';temporary.write_text(json.dumps(r,indent=2));temporary.replace(OUT/'result.json')
    def check(self,name,passed,**facts):
        self.result['checks'].append({'name':name,'passed':bool(passed),**facts});self.save();print(name,'PASS'if passed else'FAIL',flush=True)
        if not passed:raise AssertionError(name)
    def call(self,client,path,method='GET',body=None,expected=200,headers=None,port=23081,bearer=True,expected_code=None):
        rid='t36-'+uuid.uuid4().hex
        s,b,h=client.request(port,path,method,body,headers={'X-Request-Id':rid,**(headers or {})},bearer=bearer)
        self.check(method+' '+path,s==expected,httpStatus=s,expected=expected,node=port,code=(b.get('error')or{}).get('code')if isinstance(b,dict)and isinstance(b.get('error',{}),dict)else None,requestId=b.get('requestId')if isinstance(b,dict)else None,responseRequestId=h.get('X-Request-Id'))
        self.check('HTTP correlation',h.get('X-Request-Id')==rid and (not isinstance(b,dict)or b.get('requestId')==rid),node=port,requestId=rid,responseRequestId=h.get('X-Request-Id'))
        if isinstance(b,dict):
            self.check('Unified response',b.get('success')==(s<400),node=port)
            if s>=400:
                code=(b.get('error')or{}).get('code')
                self.check('failure envelope has stable code and no data',isinstance(code,str)and bool(code)and b.get('data')is None and(expected_code is None or code==expected_code),code=code,node=port)
        return b.get('data')if isinstance(b,dict)else None,h

def mutation(etag=None):
    h={'Idempotency-Key':uuid.uuid4().hex}
    if etag:h['If-Match']=etag
    return h

def wait(predicate,seconds=30):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        value=predicate()
        if value:return value
        time.sleep(.3)
    return None

def redis(env,*args,database=15):
    with socket.create_connection(('127.0.0.1',env.redis_port),timeout=5)as sock:
        f=sock.makefile('rb')
        def command(values):
            parts=[str(v).encode()for v in values];sock.sendall(b'*'+str(len(parts)).encode()+b'\r\n'+b''.join(b'$'+str(len(p)).encode()+b'\r\n'+p+b'\r\n'for p in parts))
            def read():
                h=f.readline();kind=h[:1];v=h[1:-2]
                if kind==b'-':raise RuntimeError('redis_command_failed')
                if kind==b':':return int(v)
                if kind==b'+':return v.decode()
                if kind==b'$':
                    length=int(v)
                    if length<0:return None
                    value=f.read(length);f.read(2);return value.decode()
                if kind==b'*':return [read()for _ in range(int(v))]
                raise RuntimeError('redis_protocol_failed')
            return read()
        if env.redis:command(['AUTH','default',env.redis])
        command(['SELECT',database]);return command(args)

def snapshot():
    archive=OUT/'executed-scripts';archive.mkdir()
    files=[]
    for p in SCRIPT.rglob('*'):
        if p.is_file()and'__pycache__'not in p.parts:
            target=archive/p.relative_to(SCRIPT);target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,target)
            files.append({'path':str(p.relative_to(ROOT)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
    (OUT/'executed-source.json').write_text(json.dumps(files,indent=2))

def pin_old_candidate():
    path=Path(os.environ['SUMMER_RELEASE_UPGRADE_OLD_CANDIDATE']).resolve();expected=os.environ['SUMMER_RELEASE_UPGRADE_OLD_SHA256'];actual=hashlib.sha256(path.read_bytes()).hexdigest()
    if actual!=expected:raise RuntimeError('old_candidate_hash_mismatch')
    (OUT/'old-candidate.json').write_text(json.dumps({'path':str(path),'sha256':actual},indent=2));return path

def pin_inputs(candidate):
    expected=json.loads(candidate.with_name('candidate-inputs.json').read_text())
    observed={name:hashlib.sha256((ROOT/name).read_bytes()).hexdigest()for name in expected}
    if expected!=observed:raise RuntimeError('candidate_inputs_do_not_match_workspace')
    ui={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()for p in (ROOT/'summer-ui/src').rglob('*')if p.is_file()}
    for name in ['summer-ui/package.json','summer-ui/package-lock.json','summer-ui/yarn.lock','summer-ui/pnpm-lock.yaml','summer-ui/vite.config.ts','summer-ui/tsconfig.json','scripts/acceptance/data-scopes/browser-lifecycle.mjs']:
        source=ROOT/name
        if source.exists():ui[name]=hashlib.sha256(source.read_bytes()).hexdigest()
    (OUT/'backend-inputs.json').write_text(json.dumps(observed,indent=2));(OUT/'ui-inputs-before.json').write_text(json.dumps(ui,indent=2))
    return observed,ui

def inputs_stable(inputs):
    return all((ROOT/name).exists()and hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==sha for group in inputs for name,sha in group.items())

class SseStream:
    def __init__(self,port,path,token,last=None):
        import http.client,threading
        self.events=[];self.errorType=None
        self.connection=http.client.HTTPConnection('127.0.0.1',port,timeout=30)
        headers={'Authorization':'Bearer '+token,'Host':'127.0.0.1:23573'}
        if last is not None:headers['Last-Event-ID']=str(last)
        self.connection.request('GET',path,headers=headers);self.response=self.connection.getresponse()
        if self.response.status!=200:raise RuntimeError('sse_http_'+str(self.response.status))
        def read():
            current={}
            try:
                while True:
                    raw=self.response.readline()
                    if not raw:break
                    line=raw.decode().strip()
                    if not line:
                        if current:self.events.append(current)
                        current={}
                    elif ':'in line:
                        key,value=line.split(':',1);current[key]=value.strip()
            except Exception as error:self.errorType=type(error).__name__
        self.thread=threading.Thread(target=read,daemon=True);self.thread.start()
    def close(self):
        connection_socket=self.connection.sock
        if connection_socket:
            try:connection_socket.shutdown(socket.SHUT_RDWR)
            except OSError:pass
        self.thread.join(timeout=2);self.connection.close()
