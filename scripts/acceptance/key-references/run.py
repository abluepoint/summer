"""T22 fixed-candidate reference sources, fail-closed startup, real HTTP and Chrome."""
import os,json,subprocess,hashlib,base64,uuid,time
from pathlib import Path
from reference_environment import ReferenceEnvironment,Client,ROOT,OUT,immutable_candidate
from grants import bootstrap,create_role,unwrap

SCRIPT=Path(__file__).resolve().parent
NODE=os.environ.get('SUMMER_IDENTITY_NODE','/opt/homebrew/opt/node@22/bin/node')
result={'stage':'setup','checks':[],'networkMocking':False,'businessAuthorizationSqlWrites':False}
env=None;ui=None;browser=None
def check(name,passed,**facts):
 result['checks'].append({'name':name,'passed':bool(passed),**facts});print(name,'PASS' if passed else 'FAIL',flush=True)
def crypto(data):
 r=subprocess.run([NODE,str(SCRIPT/'crypto.mjs')],input=json.dumps(data),text=True,capture_output=True)
 if r.returncode:raise RuntimeError('crypto_process_failed')
 return json.loads(r.stdout)
def jwt_header(token):
 part=token.split('.')[0];return json.loads(base64.urlsafe_b64decode(part+'='*((4-len(part)%4)%4)))
def properties(source,name,allowed=None):
 values={'summer.security.storage.keys':source,'summer.sample.security.key-reference.source':source,
         'summer.sample.security.key-reference.name':name}
 if allowed is not None:values['summer.sample.security.key-reference.allowed-directory']=str(allowed)
 return values
def verify_mode(label,source,props,injections=None):
 state=env.launch(candidate,label,props,injections);check(label+'_starts',state['outcome']=='ready',**state)
 if state['outcome']!='ready':return None
 client=Client().login(21081,env.username,env.password)
 env.additional_secrets.append(client.token)
 status,jwks,headers=Client().request(21081,'/oauth2/jwks',bearer=False)
 check(label+'_actual_sign_and_verify',status==200 and crypto({'token':client.token,'jwks':jwks})['verified'])
 check(label+'_public_jwk_only',status==200 and all(not set(k)&{'d','p','q','dp','dq','qi','oth','k'} for k in jwks['keys']))
 status,data,headers=client.request(21081,'/api/security/keys')
 check(label+'_management_metadata',status==200 and data.get('success') is True,status=status)
 if status==200:
  items=data['data']['items'];safe={'source','algorithm','kid','exists','health','status','version'}
  check(label+'_metadata_value_separation',len(items)==1 and set(items[0])<=safe and items[0]['source']==source
        and items[0].get('exists') is True and items[0]['kid']==jwt_header(client.token)['kid'] and 'PRIVATE KEY' not in json.dumps(data))
  check(label+'_request_correlation',bool(data.get('requestId')) and data['requestId']==headers.get('X-Request-Id'))
 return client
def invalid(label,props,injections=None):
 state=env.launch(candidate,label,props,injections)
 check(label+'_fails_closed',state['outcome']=='exited' and state['exitCode']!=0
       and bool(set(state['codes'])&{'SUMMER_KEY_REFERENCE_INVALID','SUMMER_KEY_REFERENCE_UNAVAILABLE'}),**state)
def source_inputs():
 inputs={}
 for base in ['summer-ui/src','summer-sample/src/main/java/com/abluepoint/summer/sample/key']:
  for p in (ROOT/base).rglob('*'):
   if p.is_file():inputs[str(p.relative_to(ROOT))]=hashlib.sha256(p.read_bytes()).hexdigest()
 return inputs

candidate=immutable_candidate();before=source_inputs()
try:
 env=ReferenceEnvironment();env.setup()
 material=crypto({'operation':'generate'})['pem']
 env.additional_secrets.extend([material,*[line for line in material.splitlines() if len(line)>40]])
 env_ref=properties('environment','SUMMER_ACCEPTANCE_KEY')
 started=env.launch(candidate,'environment_initial',env_ref,{'SUMMER_ACCEPTANCE_KEY':material})
 check('environment_initial_starts',started['outcome']=='ready',**started)
 if started['outcome']!='ready':raise RuntimeError('valid_environment_failed_startup')
 admin=Client().login(21081,env.username,env.password);bootstrap(admin,env.username);admin=Client().login(21081,env.username,env.password)
 role=create_role(admin,'ent-acme',['GET:/api/security/keys','security.key.read','menu.securityKeys.read'],'T22 reference metadata')
 status,data,headers=admin.request(21081,f'/api/v1/enterprises/ent-acme/users/{env.username}/roles');assert status==200
 status,_,_=admin.request(21081,f'/api/v1/enterprises/ent-acme/users/{env.username}/roles','PUT',{'roleIds':unwrap(data)['roleIds']+[role],'deniedCapabilityCodes':[]},headers={'If-Match':headers.get('ETag'),'Idempotency-Key':uuid.uuid4().hex});assert status==200
 status,_,_=admin.request(21081,'/api/users','POST',{'username':env.denied_username,'password':env.denied_password,'role':'USER'});assert status==200
 admin=verify_mode('environment','environment',env_ref,{'SUMMER_ACCEPTANCE_KEY':material})
 status,data,headers=Client().request(21081,'/api/security/keys');check('anonymous_reference_metadata_denied',status in [401,403])
 reader=Client().login(21081,env.denied_username,env.denied_password);env.additional_secrets.append(reader.token)
 status,data,headers=reader.request(21081,'/api/security/keys');check('ungranted_reference_metadata_denied',status==403)
 error=data.get('error') if isinstance(data,dict) else None
 check('denied_unified_error_and_correlation',isinstance(error,dict) and error.get('code')=='SUMMER_FORBIDDEN'
       and data.get('requestId')==headers.get('X-Request-Id') and bool(data.get('requestId')))
 result['stage']='browser';child=dict(os.environ,SUMMER_E2E_USERNAME=env.username,SUMMER_E2E_PASSWORD=env.password,
         SUMMER_E2E_READER=env.denied_username,SUMMER_E2E_READER_PASSWORD=env.denied_password)
 ui=subprocess.Popen([NODE,str(ROOT/'scripts/acceptance/identity/ui-server.mjs')],cwd=ROOT/'summer-ui',env=child,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
 assert ui.stdout.readline().strip()=='UI_READY'
 browser=subprocess.Popen([NODE,str(SCRIPT/'browser.mjs')],cwd=ROOT/'summer-ui',env=child,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 result['browserExit']=browser.wait(timeout=150);result['browser']=json.loads((OUT/'browser-result.json').read_text())
 result['stage']='file_and_secret'
 env.write_private('allowed/key.pem',material)
 verify_mode('file','file',properties('file','key.pem',env.allowed))
 secret_env,secret_file=env.inject_secret(material)
 check('actual_kubernetes_env_and_volume_injection',secret_env==material and secret_file==material)
 verify_mode('secret_environment','kubernetes-secret',properties('kubernetes-secret','SUMMER_ACCEPTANCE_KEY'),{'SUMMER_ACCEPTANCE_KEY':secret_env})
 env.write_private('allowed/projected.pem',secret_file)
 verify_mode('secret_file','kubernetes-secret',properties('kubernetes-secret','projected.pem',env.allowed))
 result['stage']='invalid_references'
 invalid('environment_missing',env_ref)
 invalid('environment_bad_name',properties('environment','invalid-reference'))
 invalid('inline_key_as_reference_name',properties('environment',material))
 malformed='invalid-private-material-'+uuid.uuid4().hex;env.additional_secrets.append(malformed)
 invalid('environment_invalid_material',env_ref,{'SUMMER_ACCEPTANCE_KEY':malformed})
 invalid('file_missing',properties('file','absent.pem',env.allowed))
 invalid('file_absolute_path',properties('file',str(env.allowed/'key.pem'),env.allowed))
 env.write_private('outside.pem',material)
 invalid('file_parent_escape',properties('file','../outside.pem',env.allowed))
 (env.allowed/'escape.pem').symlink_to(env.private_dir/'outside.pem')
 invalid('file_symlink_escape',properties('file','escape.pem',env.allowed))
 invalid('file_allowed_directory_missing',properties('file','key.pem'))
 unreadable=env.write_private('allowed/unreadable.pem',material);unreadable.chmod(0)
 check('unreadable_fixture_has_no_read_access',not os.access(unreadable,os.R_OK))
 invalid('file_permission_denied',properties('file','unreadable.pem',env.allowed))
 env.write_private('allowed/invalid.pem',malformed)
 invalid('file_invalid_material',properties('file','invalid.pem',env.allowed))
 invalid('secret_missing',properties('kubernetes-secret','SUMMER_ACCEPTANCE_KEY',env.allowed))
 invalid('secret_invalid_material',properties('kubernetes-secret','SUMMER_ACCEPTANCE_KEY'),{'SUMMER_ACCEPTANCE_KEY':malformed})
 check('reference_modes_never_generated_database_key',env.sql('SELECT COUNT(*) FROM summer_security_keys',env.schema)=='0')
 check('no_sensitive_runtime_output',not env.sensitive_log_detected)
 result['stage']='complete'
except Exception as error:
 result['failureType']=type(error).__name__
finally:
 for process in [browser,ui]:
  if process and process.poll() is None:
   process.terminate()
   try:process.wait(timeout=15)
   except subprocess.TimeoutExpired:process.kill();process.wait(timeout=5)
 if env:
  try:env.close();result['cleanup']='VERIFIED'
  except Exception as error:result['cleanup']='FAILED';result['cleanupFailureType']=type(error).__name__
 result['candidateSha256']=hashlib.sha256(candidate.read_bytes()).hexdigest();after=source_inputs()
 result['sourceInputsStable']=before==after;result['changedPaths']=[n for n in set(before)|set(after) if before.get(n)!=after.get(n)]
 result['startups']=env.startups if env else [];result['sensitiveFindings']=env.sensitive_findings if env else []
 result['counts']={'checks':len(result['checks']),'passed':sum(c['passed'] for c in result['checks']),'failed':sum(not c['passed'] for c in result['checks'])}
 result['passed']=result['stage']=='complete' and result['counts']['failed']==0 and result.get('browser',{}).get('passed') and result.get('cleanup')=='VERIFIED'
 (OUT/'source-before.json').write_text(json.dumps(before,indent=2));(OUT/'source-after.json').write_text(json.dumps(after,indent=2))
 (OUT/'result.json').write_text(json.dumps(result,indent=2));print(json.dumps({'counts':result['counts'],'stage':result['stage'],'cleanup':result.get('cleanup'),'passed':result['passed']}),flush=True)
raise SystemExit(0 if result['passed'] else 1)
