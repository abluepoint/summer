"""Native HTTP/PKCE release permission regression; all business setup uses HTTP."""
import json,uuid
from environment import Environment, Client, OUT, immutable_candidate
result={'tests':0,'failures':0,'errors':0,'skipped':0,'checks':[],'businessSqlWrites':False,'networkMocking':False}
env=None
B='/api/v1/enterprises/ent-acme'
def check(name,actual,expected):
 passed=actual==expected;result['checks'].append({'name':name,'actual':actual,'expected':expected,'passed':passed});result['tests']+=1;result['failures']+=int(not passed)
 print(name,'PASS'if passed else'FAIL',flush=True)
def request(client,path,method='GET',body=None,status=200,headers=None):
 rid='t36-'+uuid.uuid4().hex
 s,b,h=client.request(23081,path,method,body,headers={'X-Request-Id':rid,**(headers or {})})
 check(method+' '+path,s,status)
 if s!=status:raise AssertionError('http_status_'+str(s))
 check('request_correlation',h.get('X-Request-Id'),rid)
 return b['data'],h
def mutation(etag=None):
 h={'Idempotency-Key':uuid.uuid4().hex}
 if etag:h['If-Match']=etag
 return h
try:
 jar=immutable_candidate();env=Environment();env.setup();env.start(23081,jar)
 admin=Client().login(23081,env.username,env.password)
 ids=[]
 for code in ['internal.release.read','internal.release.write']:
  d,_=request(admin,B+'/capabilities','POST',{'code':code,'resource':code,'action':code.rsplit('.',1)[1],'scope':'PLATFORM','riskLevel':'HIGH'},201,mutation());ids.append(d['capabilityId'])
 codes=ids+['enterprise.roles.read','enterprise.roles.write','GET:/api/v1/enterprises/{enterpriseId}/users/{username}/roles','PUT:/api/v1/enterprises/{enterpriseId}/users/{username}/roles']
 d,_=request(admin,B+'/roles','POST',{'name':'Release preflight operator','code':'release-'+env.run,'capabilityIds':codes},201,mutation());role=d['roleId']
 d,h=request(admin,B+'/users/'+env.username+'/roles','PUT',{'roleIds':[role],'deniedCapabilityCodes':[]},200,mutation('"1"'))
 admin=Client().login(23081,env.username,env.password)
 request(admin,'/internal/v1/release/readiness')
 _,h=request(admin,B+'/users/'+env.username+'/roles')
 request(admin,B+'/users/'+env.username+'/roles','PUT',{'roleIds':[role],'deniedCapabilityCodes':['internal.release.read']},200,mutation(h['ETag']))
 s,b,h=admin.request(23081,'/internal/v1/release/readiness',headers={'X-Request-Id':'t36-old-token-revoked'})
 check('old_issued_token_denied_after_persisted_permission_revocation',s,403)
 check('revocation_framework_error',b.get('error',{}).get('code')if b.get('error')else None,'SUMMER_FORBIDDEN')
 check('revocation_response_correlation',b.get('requestId'),'t36-old-token-revoked')
 check('sensitive_logs_absent',env.sensitive_log_detected,False)
except Exception as error:
 result['errors']+=1;result['errorType']=type(error).__name__
finally:
 if env:
  try:env.close()
  except Exception as error:result['errors']+=1;result['cleanupErrorType']=type(error).__name__
 (OUT/'result.json').write_text(json.dumps(result,indent=2));print(json.dumps({k:v for k,v in result.items()if k!='checks'}),flush=True)

raise SystemExit(1 if result['failures'] or result['errors'] else 0)
