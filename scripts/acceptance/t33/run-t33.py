"""T33 targeted monitoring regression: real MySQL/isolated Redis/PKCE/HTTP/Chrome."""
import hashlib, json, os, re, runpy, socket, subprocess, time, uuid
from pathlib import Path
from urllib.parse import urlencode
from environment import Environment, Client, OUT, ROOT, immutable_candidate

PORT=20081;BASE='/api/v1/enterprises/ent-acme';RUNTIME='/internal/v1/diagnostics/runtime'
grants=runpy.run_path(str(ROOT/'scripts/acceptance/identity/grants.py'))
result={'issue':141,'checks':[],'networkMocking':False,'businessSqlWrites':False,'stage':'setup'}
env=ui=browser=ws=None
class LocalClient(Client):
    def request(self,port,*args,**kwargs):return super().request(PORT,*args,**kwargs)
def check(name,passed,**facts):
    result['checks'].append({'name':name,'passed':bool(passed),**facts});print(name,'PASS' if passed else 'FAIL',flush=True)
def request(name,client,path,method='GET',body=None,expected=200,headers=None):
    rid='t33-'+uuid.uuid4().hex
    code,data,h=client.request(PORT,path,method,body,headers={'X-Request-Id':rid,**(headers or {})})
    check(name,code in expected if isinstance(expected,tuple) else code==expected,status=code,expected=expected)
    if isinstance(data,dict) and 'success' in data:
        check(name+'_correlation',data.get('requestId')==h.get('X-Request-Id')==rid and data.get('success')==(code<400))
        if code>=400:check(name+'_summer_error',str((data.get('error') or {}).get('code','')).startswith('SUMMER_'))
    return code,data,h,rid
def wait_until(test,seconds=8):
    deadline=time.monotonic()+seconds
    while time.monotonic()<deadline:
        if test():return True
        time.sleep(.2)
    return bool(test())
def redis(*args):
    def encode(parts):return b'*'+str(len(parts)).encode()+b'\r\n'+b''.join(b'$'+str(len(str(a).encode())).encode()+b'\r\n'+str(a).encode()+b'\r\n' for a in parts)
    def read(f):
        line=f.readline().rstrip(b'\r\n');kind=line[:1];rest=line[1:]
        if kind==b'+':return rest.decode()
        if kind==b'-':raise RuntimeError('isolated_redis_command_failed')
        if kind==b':':return int(rest)
        if kind==b'$':
            size=int(rest)
            if size<0:return None
            value=f.read(size);f.read(2);return value.decode()
        if kind==b'*':return [read(f) for _ in range(int(rest))]
        raise RuntimeError('isolated_redis_protocol_error')
    with socket.create_connection(('127.0.0.1',env.redis_port),timeout=3) as s:
        s.settimeout(4);s.sendall(encode(['SELECT',15])+encode(args));f=s.makefile('rb');assert read(f)=='OK';return read(f)
def mh():return {'Idempotency-Key':uuid.uuid4().hex}
def logged(username,password):
    client=LocalClient().login(PORT,username,password);env.additional_secrets.append(client.token);return client
def component_map(data):return {x['name']:x['status'] for x in data['data']['components']}
def inputs():
    names=[p for top in ['summer-sample/src/main/java/com/abluepoint/summer/sample/operations','summer-mvc/src/main/java/com/abluepoint/summer/mvc/logging','summer-sample/src/main/java/com/abluepoint/summer/sample/observability','summer-ui/src/pages/diagnostics/runtime'] for p in (ROOT/top).rglob('*') if p.is_file()]
    names += [ROOT/'summer-sample/src/main/java/com/abluepoint/summer/sample/customer/CustomerController.java',ROOT/'summer-sample/src/main/java/com/abluepoint/summer/sample/customer/CustomerAuditService.java',*Path(__file__).parent.glob('*.py'),*Path(__file__).parent.glob('*.mjs')]
    names += [ROOT/name for name in json.loads((ROOT/'.git/summer4-closeout-20261015/frontend/t33-141/SOURCE_FROZEN.json').read_text())]
    return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in names}
before=inputs();(OUT/'inputs-before.json').write_text(json.dumps(before,indent=2))
try:
    candidate=immutable_candidate();result['candidate_sha256']=hashlib.sha256(candidate.read_bytes()).hexdigest()
    frozen=json.loads((ROOT/'.git/summer4-closeout-20261015/frontend/t33-141/SOURCE_FROZEN.json').read_text())
    candidate_inputs=json.loads((candidate.parent/'candidate-inputs.json').read_text())
    result['candidateRelevantInputsMatch']=all(before.get(path)==sha==candidate_inputs.get(path) for path,sha in frozen.items())
    assert result['candidateRelevantInputsMatch']
    env=Environment();env.extra.update({'logging.level.com.abluepoint.summer.mvc.requestlog':'INFO','logging.pattern.console':'%msg traceId=%X{traceId:-none} spanId=%X{spanId:-none}%n'})
    env.extra.update({'summer.sample.realtime.allowed-origins':'http://127.0.0.1:20573'})
    baseline=ROOT/'.git/summer4-closeout-20261015/t18-live/frozen-build-01/candidate.jar'
    assert hashlib.sha256(baseline.read_bytes()).hexdigest()=='9f0b3632cb7aa099beeb9900e8301312cb280a31977cd575efa0b31e581aa0af'
    env.setup();env.start(PORT,jar=baseline)
    result['stage']='real_authorization'
    admin=logged(env.username,env.password);bootstrap=grants['bootstrap'](admin,env.username);admin=logged(env.username,env.password)
    code,catalog,_,_=request('fixture_capability_catalog',admin,BASE+'/capabilities/catalog')
    known={row['code']:row['capabilityId'] for row in catalog['data']['capabilities']}
    ids=[]
    for capability in ['system.parameter.read','system.parameter.create','enterprise.messages.read']:
        if capability not in known:
            _,added,_,_=request('fixture_register_'+capability,admin,BASE+'/capabilities','POST',{'code':capability,'resource':capability.rsplit('.',1)[0],'action':capability.rsplit('.',1)[1],'scope':'PLATFORM' if capability.startswith('system.') else 'ENTERPRISE','riskLevel':'LOW'},201,mh())
            known[capability]=added['data']['capabilityId']
        ids.append(known[capability])
    role=grants['create_role'](admin,'ent-acme',ids+['GET:/api/customers','POST:/api/customers','GET:/api/customers/{username}'],'T33 observation fixtures')
    _,binding,h,_=request('admin_binding',admin,BASE+'/users/'+env.username+'/roles')
    request('grant_customer_routes',admin,BASE+'/users/'+env.username+'/roles','PUT',{'roleIds':[bootstrap,role],'deniedCapabilityCodes':[]},headers={**mh(),'If-Match':h['ETag']})
    admin=logged(env.username,env.password)
    request('create_reader',admin,'/api/users','POST',{'username':env.denied_username,'password':env.denied_password,'role':'USER'})
    request('create_reader_membership',admin,BASE+'/members','POST',{'subject':env.denied_username,'displayName':'T33 runtime reader','email':'t33@example.test'},201,mh())
    reader=logged(env.denied_username,env.denied_password)
    result['stage']='mysql_upgrade'
    legacy='t33.legacy.'+env.run
    request('legacy_audit_fixture',admin,'/api/customers','POST',{'username':legacy,'email':legacy+'@example.test','displayName':'Legacy audit fixture'})
    legacy_query="SELECT event_id,tenant_id,organization_id,principal,resource,action,result,request_id,detail_json,occurred_at FROM summer_audit_event WHERE resource='customer:"+legacy+"' ORDER BY event_id"
    legacy_rows=env.sql(legacy_query,env.schema)
    assert legacy_rows
    check('baseline_has_no_trace_column',env.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='"+env.schema+"' AND table_name='summer_audit_event' AND column_name='trace_id'")=='0')
    env.stop(PORT);env.start(PORT,jar=candidate)
    check('mysql_v38_upgrade_applied',env.sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='38' AND success=1",env.schema)=='1')
    check('migration_preserves_old_append_only_events',env.sql(legacy_query,env.schema)==legacy_rows and env.sql("SELECT COUNT(*) FROM summer_audit_event WHERE resource='customer:"+legacy+"' AND trace_id IS NULL",env.schema)=='1')
    result['legacyAuditDigest']=hashlib.sha256(legacy_rows.encode()).hexdigest()
    admin=logged(env.username,env.password);reader=logged(env.denied_username,env.denied_password)
    result['stage']='native_monitoring'
    code,snapshot,_,_=request('runtime_snapshot',admin,RUNTIME);assert code==200
    components=component_map(snapshot);result['initialComponents']=components
    check('mysql_redis_audit_and_migrations_healthy',all(components.get(x)=='UP' for x in ['db','redis','audit','migrations']))
    _,denied,denied_headers,denied_rid=request('reader_runtime_forbidden',reader,RUNTIME,expected=403)
    check('forbidden_has_real_trace_and_safe_log',bool(re.fullmatch('[a-f0-9]{32}',denied_headers.get('X-Trace-Id',''))) and wait_until(lambda:any(x['requestId']==denied_rid and x['traceId']==denied_headers.get('X-Trace-Id') and x['status']==403 for x in env.request_logs)))
    username='t33.'+env.run;body={'username':username,'email':username+'@example.test','displayName':'T33 audit fixture'}
    _,_,success_headers,success_rid=request('customer_success_for_metric_and_audit',admin,'/api/customers','POST',body)
    check('success_trace_persisted_with_request_id',env.sql("SELECT trace_id FROM summer_audit_event WHERE request_id='"+success_rid+"' AND result='SUCCESS'",env.schema)==success_headers.get('X-Trace-Id') and bool(re.fullmatch('[a-f0-9]{32}',success_headers.get('X-Trace-Id',''))))
    canary='t33-sensitive-'+uuid.uuid4().hex;env.additional_secrets.append(canary)
    trace=uuid.uuid4().hex;span=uuid.uuid4().hex[:16]
    _,failure,error_headers,error_rid=request('audited_conflict',admin,'/api/customers','POST',{**body,'displayName':canary},409,{'traceparent':'00-'+trace+'-'+span+'-01','X-T33-Canary':canary})
    result['errorCorrelation']={'requestId':error_rid,'incomingTraceId':trace,'responseTraceId':error_headers.get('X-Trace-Id')}
    check('error_response_propagates_otel_trace',error_headers.get('X-Trace-Id')==trace)
    _,audit,_,_=request('audit_error_lookup',admin,'/api/audit-events?'+urlencode({'requestId':error_rid}))
    audit_items=audit['data']['items']
    check('otel_trace_correlates_to_persisted_audit',len(audit_items)==1 and audit_items[0].get('traceId')==trace and audit_items[0]['requestId']==error_rid and audit_items[0]['result']=='FAILURE' and env.sql("SELECT trace_id FROM summer_audit_event WHERE request_id='"+error_rid+"'",env.schema)==trace)
    check('otel_trace_propagated_to_safe_error_log',wait_until(lambda:any(x['requestId']==error_rid and x['traceId']==trace and x['status']==409 for x in env.request_logs)))
    result['safeErrorLogs']=[x for x in env.request_logs if x['requestId']==error_rid]
    request('cache_fixture_scope',admin,BASE+'/data-scopes/system-parameters','PUT',{'operator':'AND','rules':[{'type':'TENANT'}]})
    cache_path=BASE+'/system-parameters';cache_key='monitoring.cache.'+env.run
    request('cache_fixture_create',admin,cache_path,'POST',{'key':cache_key,'type':'STRING','value':'safe-observation-fixture','public':False},201)
    request('cache_fixture_cold_read',admin,cache_path+'/'+cache_key)
    request('cache_fixture_warm_read',admin,cache_path+'/'+cache_key)
    _,cache_snapshot,_,_=request('cache_observation',admin,RUNTIME)
    cache_values=cache_snapshot['data']['metrics']['cache']['values']
    check('cache_metrics_follow_actual_reads',cache_values.get('requests',0)>=2 and cache_values.get('hits',0)>=1 and cache_values.get('loads',0)>=1)
    node='/opt/homebrew/opt/node@22/bin/node'
    ws=subprocess.Popen([node,str(Path(__file__).parent/'t33-socket.mjs')],cwd=ROOT,env={**os.environ,'SUMMER_T33_WS_TOKEN':admin.token},stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    check('actual_websocket_fixture_opened',ws.stdout.readline().strip()=='OPEN')
    _,connected,_,_=request('websocket_observation',admin,RUNTIME)
    check('realtime_gauge_counts_open_connection',connected['data']['metrics']['realtime']['values'].get('connections')==1)
    ws.stdin.write('CLOSE\n');ws.stdin.flush();ws.wait(timeout=15);ws=None
    check('realtime_gauge_drops_after_close',wait_until(lambda:admin.request(PORT,RUNTIME)[1]['data']['metrics']['realtime']['values'].get('connections')==0))
    def audit_is_current():
        data=admin.request(PORT,RUNTIME)[1]['data'];values=data['metrics']['audit']['values']
        return data['metrics']['audit']['status']=='AVAILABLE' and values.get('total')==int(env.sql('SELECT COUNT(*) FROM summer_audit_event',env.schema)) and values.get('failure',0)>=1
    check('audit_metrics_match_persisted_ledger',wait_until(audit_is_current,10))
    _,current,_,_=request('monitoring_summary',admin,RUNTIME);metrics=current['data']['metrics'];http=metrics['http']
    check('http_latency_and_error_rate_have_explicit_lifetime_basis',http['window']=='PROCESS_LIFETIME' and http['requests']>0 and http['meanLatencyMs']>=0 and abs(http['errorRate']-(http['clientErrors']+http['serverErrors'])/http['requests'])<1e-12 and http['clientErrors']>=2)
    check('authentication_metrics_remain_connected',metrics['authentication']['status']=='AVAILABLE' and metrics['authentication']['values'].get('attempts',0)>0)
    result['metricSummary']=metrics
    code,prometheus,headers,_=request('prometheus_export_endpoint',admin,'/actuator/prometheus')
    check('prometheus_text_contains_actual_metric_families',code==200 and isinstance(prometheus,str) and all(name in prometheus for name in ['http_server_requests_seconds_count','summer_audit_events','summer_cache_requests_total','summer_realtime_connections']) and any(t in headers.get('Content-Type','') for t in ['text/plain','openmetrics']))
    tag_names=set()
    if isinstance(prometheus,str):
        for labels in re.findall(r'\{([^}]+)\}',prometheus):tag_names.update(re.findall(r'(\w+)=',labels))
    check('metric_dimensions_have_no_request_or_identity_values',not any(set(key.lower().split('_')) & {'trace','traceid','request','requestid','user','userid','username','principal','token','secret','password','url','uri','path','tenant','key'} for key in tag_names) and isinstance(prometheus,str) and not any(value in prometheus for value in [env.username,env.denied_username,error_rid,trace,cache_key]))
    result['prometheusTagNames']=sorted(tag_names)
    for endpoint in ['/actuator/prometheus','/actuator/metrics']:
        request('reader_denied_'+endpoint.rsplit('/',1)[1],reader,endpoint,expected=403)
        request('anonymous_denied_'+endpoint.rsplit('/',1)[1],LocalClient(),endpoint,expected=401)
    raw=json.dumps({'runtime':current,'audit':audit})+(prometheus if isinstance(prometheus,str) else '')
    check('monitoring_responses_exclude_secrets',not any(secret and secret in raw for secret in [env.password,env.mysql,env.master,env.db_password,admin.token,reader.token,canary]))
    result['stage']='old_jwt_revocation'
    _,_,binding_headers,_=request('binding_before_monitoring_deny',admin,BASE+'/users/'+env.username+'/roles')
    request('persist_monitoring_deny',admin,BASE+'/users/'+env.username+'/roles','PUT',{'roleIds':[bootstrap,role],'deniedCapabilityCodes':['platform.capabilities.read']},headers={**mh(),'If-Match':binding_headers['ETag']})
    for endpoint in ['/actuator/prometheus','/actuator/metrics',RUNTIME]:
        request('old_jwt_denied_'+endpoint.rsplit('/',1)[1],admin,endpoint,expected=403)
    _,_,binding_headers,_=request('binding_before_monitoring_restore',admin,BASE+'/users/'+env.username+'/roles')
    request('restore_monitoring_grant',admin,BASE+'/users/'+env.username+'/roles','PUT',{'roleIds':[bootstrap,role],'deniedCapabilityCodes':[]},headers={**mh(),'If-Match':binding_headers['ETag']})
    request('same_jwt_reads_after_grant_restored',admin,RUNTIME)
    result['stage']='restart'
    started=http['processStartedAt'];durable_count=int(env.sql('SELECT COUNT(*) FROM summer_audit_event',env.schema))
    env.stop(PORT);env.start(PORT,jar=candidate);admin=logged(env.username,env.password)
    _,restarted,_,_=request('post_restart_monitoring',admin,RUNTIME)
    check('restart_preserves_audit_trace_and_resets_process_window',env.sql("SELECT trace_id FROM summer_audit_event WHERE request_id='"+error_rid+"'",env.schema)==trace and restarted['data']['metrics']['http']['processStartedAt']>started and restarted['data']['metrics']['audit']['values'].get('total',0)>=durable_count)
    result['stage']='real_chrome'
    child={**os.environ,'SUMMER_FOUNDATION_EVIDENCE_DIR':str(OUT),'SUMMER_E2E_USERNAME':env.username,'SUMMER_E2E_PASSWORD':env.password,'SUMMER_E2E_READER':env.denied_username,'SUMMER_E2E_READER_PASSWORD':env.denied_password}
    node='/opt/homebrew/opt/node@22/bin/node'
    ui=subprocess.Popen([node,str(ROOT/'scripts/acceptance/foundation/ui-server.mjs')],cwd=ROOT/'summer-ui',env=child,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True);assert ui.stdout.readline().strip()=='UI_READY'
    browser=subprocess.Popen([node,str(Path(__file__).parent/'t33-browser.mjs')],cwd=ROOT/'summer-ui',env=child,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    for line in browser.stdout:
        marker=line.strip()
        if marker=='REDIS_FAULT_READY':redis('ACL','SETUSER','default','-info')
        elif marker=='REDIS_RECOVER_READY':redis('ACL','SETUSER','default','+info','+ping')
        else:
            if marker.endswith((' PASS',' FAIL')):print(marker,flush=True)
            continue
        browser.stdin.write('GO\n');browser.stdin.flush()
    result['browserExit']=browser.wait(timeout=20);result['browser']=json.loads((OUT/'browser-t33.json').read_text())
    check('runtime_logs_exclude_credentials_and_canary',not env.sensitive_log_detected)
    result['stage']='complete'
except Exception as error:result['fatalClass']=type(error).__name__;print('fatal',type(error).__name__,flush=True)
finally:
    for process in [ws,browser,ui]:
        if process and process.poll() is None:
            process.terminate()
            try:process.wait(timeout=15)
            except subprocess.TimeoutExpired:process.kill();process.wait()
    if env:
        if env.created_redis:
            try:redis('ACL','SETUSER','default','+info','+ping')
            except Exception:pass
        env.close()
    after=inputs();result['changedInputs']=[n for n in set(before)|set(after) if before.get(n)!=after.get(n)]
    checks=result['checks']+result.get('browser',{}).get('checks',[])
    result['counts']={'tests':len(checks),'failures':sum(not x['passed'] for x in checks),'errors':int('fatalClass' in result)+int('failureType' in result.get('browser',{})),'skipped':0}
    result['passed']=result['stage']=='complete' and not result['counts']['failures'] and not result['counts']['errors'] and result.get('browserExit')==0
    (OUT/'result.json').write_text(json.dumps(result,indent=2));print('counts',json.dumps(result['counts']),flush=True)
raise SystemExit(0 if result['passed'] else 1)
