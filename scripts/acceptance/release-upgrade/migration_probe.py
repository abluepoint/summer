"""Actual historical Flyway 31 -> current 39 upgrade on an owned restored database."""
import hashlib,json,zipfile
from environment import Environment,Client,OUT,immutable_candidate
from support import Evidence,mutation,pin_old_candidate,snapshot
from support import pin_inputs,inputs_stable
from archive import backup_restore

env=None;ev=Evidence();B='/api/v1/enterprises/ent-acme'
try:
    candidate=immutable_candidate();old=pin_old_candidate();snapshot();inputs=pin_inputs(candidate)
    with zipfile.ZipFile(old)as a,zipfile.ZipFile(candidate)as b:
        migrations=[n for n in a.namelist()if '/db/migration/mysql/V'in n]
        ev.check('historical Flyway checksums unchanged',len(migrations)==31 and all(n in b.namelist()and a.read(n)==b.read(n)for n in migrations))
    env=Environment();env.setup();env.start(23081,old);admin=Client().login(23081,env.username,env.password)
    ids=[]
    for code in ['internal.release.read','internal.release.write']:
        cap,_=ev.call(admin,B+'/capabilities','POST',{'code':code,'resource':code,'action':'invoke','scope':'PLATFORM','riskLevel':'HIGH'},201,mutation());ids.append(cap['capabilityId'])
    codes=ids+['enterprise.roles.read','enterprise.roles.write','enterprise.data-scope.read','enterprise.data-scope.write','system.parameter.read','system.parameter.create','system.parameter.update','system.parameter.write','system.parameter.publish','GET:/api/system-parameters/{key}','POST:/api/system-parameters','PATCH:/api/system-parameters/{key}','PUT:/api/v1/enterprises/{enterpriseId}/data-scopes/{resource}','GET:/api/v1/enterprises/{enterpriseId}/users/{username}/roles','PUT:/api/v1/enterprises/{enterpriseId}/users/{username}/roles']
    role,_=ev.call(admin,B+'/roles','POST',{'name':'Migration operator','code':'migration-'+env.run,'capabilityIds':codes},201,mutation())
    ev.call(admin,B+'/users/'+env.username+'/roles','PUT',{'roleIds':[role['roleId']],'deniedCapabilityCodes':[]},headers=mutation('"1"'))
    admin=Client().login(23081,env.username,env.password)
    ev.call(admin,B+'/data-scopes/system-parameters','PUT',{'conditions':{'field':'TENANT'}})
    created,_=ev.call(admin,'/api/system-parameters','POST',{'key':'t36-migration-value','type':'STRING','value':'legacy-preserved','public':True},201)
    ev.check('old schema has 31 applied migration files',env.sql('SELECT COUNT(*) FROM flyway_schema_history WHERE success=1',env.schema)=='31')
    before=env.sql("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='"+env.schema+"' AND table_name='summer_system_parameter' AND index_name='uq_summer_system_parameter_key'",env.schema)
    ev.check('old global uniqueness constraint exists',int(before)>0)
    ev.result['stage']='restore-and-real-expand-contract'
    tables=['summer_security_keys','summer_authorization_user_binding','summer_authorization_role','summer_authorization_capability','summer_auth_user','summer_system_parameter','oauth2_registered_client','oauth2_authorization','flyway_schema_history']
    backup_restore(env,ev,admin,candidate,'/api/system-parameters/t36-migration-value',tables)
    count=int(env.sql('SELECT COUNT(*) FROM flyway_schema_history WHERE success=1',env.restore));ev.check('restored database migrated to all 39 current files',count==39,applied=count)
    expanded=env.sql("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='"+env.restore+"' AND table_name='summer_system_parameter' AND index_name='uq_summer_parameter_tenant_key'",env.restore)
    contracted=env.sql("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='"+env.restore+"' AND table_name='summer_system_parameter' AND index_name='uq_summer_system_parameter_key'",env.restore)
    ev.check('V32 expands tenant uniqueness and contracts old global index',int(expanded)==2 and contracted=='0')
    memberships=env.sql("SELECT COUNT(*) FROM summer_enterprise_member WHERE subject='"+env.username+"' AND enterprise_id='ent-acme' AND status='ACTIVE'",env.restore)
    ev.check('V36 preserves existing bound active identity membership',memberships=='1')
    restored=Client().login(23083,env.username,env.password)
    data,_=ev.call(restored,'/api/v1/enterprises/ent-acme/system-parameters/t36-migration-value',port=23083)
    ev.check('current application reads upgraded legacy value',data['value']=='legacy-preserved')
    ev.call(restored,'/api/v1/enterprises/ent-acme/system-parameters/t36-migration-value','PATCH',{'value':'current-write','version':data['version']},port=23083)
    current,_=ev.call(restored,'/api/v1/enterprises/ent-acme/system-parameters/t36-migration-value',port=23083)
    ev.check('current application writes after contract step',current['value']=='current-write')
    ev.check('old source database remains unchanged at 31 migrations',env.sql('SELECT COUNT(*) FROM flyway_schema_history WHERE success=1',env.schema)=='31')
    ev.check('no sensitive logs detected',not env.sensitive_log_detected)
    ev.check('executed backend and UI inputs remain stable',inputs_stable(inputs))
    ev.result['stage']='complete'
except Exception as error:
    import traceback
    ev.result['errorType']=type(error).__name__;ev.result['localFrames']=[{'file':f.filename.rsplit('/',1)[-1],'line':f.lineno}for f in traceback.extract_tb(error.__traceback__)if 'release-upgrade'in f.filename]
finally:
    if env:
        try:env.close()
        except Exception as error:ev.result['cleanupErrorType']=type(error).__name__;ev.result.setdefault('errorType',type(error).__name__)
    backup=OUT/'recovery.sql.enc'
    if backup.exists():backup.unlink()
    ev.save();print(json.dumps({'stage':ev.result['stage'],'counts':ev.result['counts'],'errorType':ev.result.get('errorType')}),flush=True)

raise SystemExit(1 if ev.result['counts']['failures'] or ev.result['counts']['errors'] else 0)
