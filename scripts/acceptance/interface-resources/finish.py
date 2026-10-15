"""Focused continuation: real Chrome plus opaque/conflicting runtime mapping acceptance."""
import hashlib,json,os,subprocess,sys
from pathlib import Path
from environment import Environment,Client,OUT,ROOT
from fixtures import Fixtures
CANDIDATE=ROOT/'.git/summer4-closeout-20261015/runtime/candidate-4c81/candidate.jar'
EXPECTED='4c81a803dd19f610999a8a8b5b336b4a25771af82a981a3a47e79a5c3e86eeae'
SCRIPT=Path(__file__).resolve().parent
FIXTURE=OUT/'interface-resource-fixture.jar';API='/api/v1/platform/interface-resources'
env=None;ui=None;browser=None;stage='setup';result={'candidate_sha256':EXPECTED,'checks':[],'failures':0,'errors':0}
def start(phase):env.start(23081,jar=CANDIDATE,acceptance_fixture=FIXTURE,extra_properties={'acceptance.catalog.phase':phase})
def view(client):
 status,body,_=client.request(23081,API);assert status==200;return body['data']
try:
 assert hashlib.sha256(CANDIDATE.read_bytes()).hexdigest()==EXPECTED
 env=Environment();env.setup();start('original');fixture=Fixtures(env).seed();original=view(fixture.admin)
 env.stop(23081);start('replacement');replacement=view(fixture.admin)
 runenv=os.environ.copy();runenv.update({'SUMMER_E2E_USERNAME':env.username,'SUMMER_E2E_PASSWORD':env.password,'CATALOG_DENIED_USERNAME':env.denied_username,'CATALOG_DENIED_PASSWORD':env.denied_password,'CATALOG_OUTPUT':str(OUT),'CATALOG_ORIGINAL':str(original['revision']),'CATALOG_REPLACEMENT':str(replacement['revision']),'CATALOG_VITE_CACHE':str(OUT/('vite-cache-'+env.run))})
 ui=subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node',str(SCRIPT/'ui-server.mjs')],cwd=ROOT/'summer-ui',env=runenv,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True);assert ui.stdout.readline().strip()=='UI_READY'
 stage='browser';browser=subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node',str(SCRIPT/'browser.mjs')],cwd=ROOT/'summer-ui',env=runenv,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
 for line in browser.stdout:
  if line.strip()=='CONTROL stop':env.stop(23081);browser.stdin.write('ACK\n');browser.stdin.flush()
  elif line.strip()=='CONTROL restart':start('replacement');browser.stdin.write('ACK\n');browser.stdin.flush()
 browser.wait(timeout=30);result['browser']=json.loads((OUT/'browser-result.json').read_text());result['failures']+=int(browser.returncode!=0)
 if '--browser-only' not in sys.argv:
  stage='opaque_component_reports_incomplete';env.stop(23081);start('opaque');opaque=view(fixture.admin);assert opaque['complete'] is False and any(i['code']=='SUMMER_RESOURCE_DISCOVERY_UNSUPPORTED' for i in opaque['issues']);assert any(i['normalizedPath']=='/api/acceptance/catalog/explicit' for i in opaque['items']);result['checks'].append({'name':stage,'status':'passed'});print('passed',stage,flush=True)
  stage='mapping_conflict_fails_startup';env.stop(23081);before=len(env.diagnostics)
  try:start('conflict')
  except RuntimeError:pass
  else:raise AssertionError('conflict_accepted')
  assert any(i.get('catalog_conflict') for i in env.diagnostics[before:]);result['checks'].append({'name':stage,'status':'passed'});print('passed',stage,flush=True)
except Exception as error:result.update(failedStage=stage,failureType=type(error).__name__);result['failures']+=1
finally:
 for p in [browser,ui]:
  if p and p.poll() is None:
   p.terminate()
   try:p.wait(timeout=15)
   except subprocess.TimeoutExpired:p.kill();p.wait()
 if env:
  result['diagnostics']=env.diagnostics
  try:env.close();result['cleanup']='VERIFIED'
  except Exception as error:result['cleanup']='FAILED';result['cleanupFailureType']=type(error).__name__;result['errors']+=1
 result['http_observations']=Client.observations;result['status']='PASSED' if not result['failures'] and not result['errors'] else 'FAILED'
 (OUT/'finish-result.json').write_text(json.dumps(result,indent=2));print(json.dumps({k:result.get(k) for k in ['status','failures','errors','failedStage','cleanup']}),flush=True)
raise SystemExit(0 if result['status']=='PASSED' else 1)
