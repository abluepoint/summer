"""Real PKCE regression: same-code ENTERPRISE grant must not replace PLATFORM scope."""
import json
from environment import Environment,Client,OUT,immutable_candidate
from support import Evidence,mutation,snapshot,pin_inputs,inputs_stable

ev=Evidence();env=None;B='/api/v1/enterprises/ent-acme'
try:
    candidate=immutable_candidate();snapshot();inputs=pin_inputs(candidate)
    env=Environment();env.setup();env.start(23081,candidate);admin=Client().login(23081,env.username,env.password)
    capabilities={}
    for scope in ['PLATFORM','ENTERPRISE']:
        for action in ['read','write']:
            code='internal.release.'+action
            value,_=ev.call(admin,B+'/capabilities','POST',{'code':code,'resource':code,'action':action,'scope':scope,'riskLevel':'HIGH'},201,mutation())
            ev.check('created capability keeps requested scope and code',value['scope']==scope and value['code']==code,scope=scope,action=action)
            capabilities[(scope,action)]=value['capabilityId']
    manager=['enterprise.roles.read','enterprise.roles.write']
    all_ids=list(capabilities.values())+manager
    role,_=ev.call(admin,B+'/roles','POST',{'name':'Platform scope regression','code':'scope-'+env.run,'capabilityIds':all_ids},201,mutation());rolepath=B+'/roles/'+role['roleId']
    ev.call(admin,B+'/users/'+env.username+'/roles','PUT',{'roleIds':[role['roleId']],'deniedCapabilityCodes':[]},headers=mutation('"1"'))
    admin=Client().login(23081,env.username,env.password);issued_token=admin.token
    def role_capabilities(ids):
        _,headers=ev.call(admin,rolepath)
        updated,_=ev.call(admin,rolepath+'/capabilities','PUT',{'capabilityIds':ids},headers=mutation(headers['ETag']))
        return updated['capabilityIds']
    def read(client,expected):return ev.call(client,'/internal/v1/release/readiness',expected=expected,expected_code='SUMMER_FORBIDDEN'if expected==403 else None)
    def write(client,expected):return ev.call(client,'/internal/v1/release/gates:validate','POST',{'releaseVersion':'scope-regression','dryRun':True},expected,mutation(),expected_code='SUMMER_FORBIDDEN'if expected==403 else None)
    read(admin,200);write(admin,200)
    for action in ['read','write']:
        ev.result['stage']='revoke-platform-'+action
        retained=[value for value in all_ids if value!=capabilities[('PLATFORM',action)]]
        observed=role_capabilities(retained)
        ev.check('only PLATFORM grant removed, same-code ENTERPRISE grant retained',capabilities[('PLATFORM',action)]not in observed and capabilities[('ENTERPRISE',action)]in observed,action=action)
        ev.check('the exact previously issued JWT is reused',admin.token==issued_token,action=action)
        if action=='read':read(admin,403);write(admin,403)
        else:read(admin,200);write(admin,403)
        fresh=Client().login(23081,env.username,env.password)
        if action=='read':read(fresh,403)
        else:write(fresh,403)
        role_capabilities(all_ids);read(admin,200);write(admin,200)
    ev.check('no sensitive application log detected',not env.sensitive_log_detected)
    ev.check('executed backend and UI inputs remain stable',inputs_stable(inputs));ev.result['stage']='complete'
except Exception as error:
    import traceback
    ev.result['errorType']=type(error).__name__;ev.result['localFrames']=[{'file':f.filename.rsplit('/',1)[-1],'line':f.lineno}for f in traceback.extract_tb(error.__traceback__)if 'release-upgrade'in f.filename]
finally:
    if env:
        try:env.close()
        except Exception as error:ev.result['cleanupErrorType']=type(error).__name__;ev.result.setdefault('errorType',type(error).__name__)
    ev.save();print(json.dumps({'stage':ev.result['stage'],'counts':ev.result['counts'],'errorType':ev.result.get('errorType')}),flush=True)
raise SystemExit(1 if ev.result['counts']['failures']or ev.result['counts']['errors']else 0)
