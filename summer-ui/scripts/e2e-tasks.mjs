// Actual PKCE / HTTP / EventSource; no interception or saved authentication.
// Use a disposable schema owned by the runtime harness; created task definitions are left for schema teardown.
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {writeFile,mkdir} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
const require=createRequire(new URL('../package.json',import.meta.url));
const {chromium}=require('playwright-core');
const required=['SUMMER_E2E_ORIGIN','SUMMER_E2E_USERNAME','SUMMER_E2E_PASSWORD','SUMMER_E2E_ENTERPRISE_ID','SUMMER_E2E_ISOLATED_SCHEMA'];
if(required.some(name=>!process.env[name])){console.log(JSON.stringify({executed:false,requiredEnvironmentNames:required}));process.exit(2);}
const base=new URL(process.env.SUMMER_E2E_ORIGIN);
assert.ok(['127.0.0.1','localhost','[::1]'].includes(base.hostname)&&!base.username&&!base.password&&base.pathname==='/'&&!base.search&&!base.hash);
assert.match(process.env.SUMMER_E2E_ENTERPRISE_ID,/^[A-Za-z0-9_-]{1,64}$/);
assert.match(process.env.SUMMER_E2E_ISOLATED_SCHEMA,/^summer_(?:closeout|t19|t25|t36|task)_[a-z0-9_]+$/);
const repo=fileURLToPath(new URL('../..',import.meta.url));
const evidence=resolve(repo,'.git/summer4-e2e/tasks',String(Date.now()));await mkdir(evidence,{recursive:true});
const output=resolve(evidence,'result.json');
const result={mode:'real-browser-real-backend',executed:true,requiresSchemaOwnerTeardown:true,checks:[],failures:0,errors:0,skipped:0};
let browser; let stage='launch'; let bearer; const requests=[];
const root='/api/v1/enterprises/'+encodeURIComponent(process.env.SUMMER_E2E_ENTERPRISE_ID)+'/tasks';
const origin=base.origin;
const name='task-ui-'+Date.now();
const mark=name=>result.checks.push({name,status:'passed'});
const response=(page,path,method)=>page.waitForResponse(r=>new URL(r.url()).pathname===path && r.request().method()===method);
async function persistedCompletion(page, taskId, status) {
  const reply = await page.request.get(origin + root + '/' + taskId + '/executions', { headers: { Authorization: bearer } });
  assert.equal(reply.status(), 200);
  const item = (await reply.json()).data.items.find(item => item.status === status);
  assert.ok(item?.finishedAt);
  await page.getByRole('cell', { name: item.finishedAt, exact: true }).waitFor();
}

try {
  browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
  const page=await browser.newPage({viewport:{width:1500,height:1000}});page.setDefaultTimeout(30000);
  page.on('request',r=>{if(new URL(r.url()).pathname.startsWith('/api/'))bearer=r.headers().authorization||bearer;});
  page.on('response',r=>{const path=new URL(r.url()).pathname;if(path.startsWith(root))requests.push({method:r.request().method(),path,status:r.status()});});
  stage='real_pkce_browser_login';
  await page.goto(origin+'/tasks');
  await page.locator('input[autocomplete="username"]').fill(process.env.SUMMER_E2E_USERNAME);
  await page.locator('input[autocomplete="current-password"]').fill(process.env.SUMMER_E2E_PASSWORD);
  await page.getByRole('button',{name:/^(登录|login)$/i}).click();
  await page.waitForURL(url=>url.pathname==='/tasks');
  await page.getByLabel('活动企业',{exact:true}).click();
  await page.locator('.arco-select-option').filter({hasText:new RegExp('^'+process.env.SUMMER_E2E_ENTERPRISE_ID+'$')}).click();
  await page.getByPlaceholder('任务名称',{exact:true}).waitFor();mark(stage);
  stage='invalid_json_rejected_before_write';
  await page.getByPlaceholder('任务名称',{exact:true}).fill(name);
  await page.locator('.arco-select-view').filter({hasText:'受信任任务类型'}).click();
  await page.locator('.arco-select-option').filter({hasText:/^system\.sleep$/}).click();
  await page.getByPlaceholder('Cron 表达式',{exact:true}).fill('0 0 0 1 1 ? 2099');
  await page.getByPlaceholder('参数 JSON',{exact:true}).fill('{');
  const before=requests.filter(r=>r.method==='POST').length;
  await page.getByRole('button',{name:'创建任务',exact:true}).click();
  await page.getByText('参数必须是 JSON 对象。',{exact:true}).waitFor();
  assert.equal(requests.filter(r=>r.method==='POST').length,before);mark(stage);
  stage='create_and_edit_real_api';
  await page.getByPlaceholder('参数 JSON',{exact:true}).fill('{"millis":5000}');
  const created=response(page,root,'POST');await page.getByRole('button',{name:'创建任务',exact:true}).click();
  const creation=await created;assert.equal(creation.status(),201);const task=(await creation.json()).data;
  const taskPath=root+'/'+task.taskId;
  let row=page.getByRole('row').filter({has:page.getByText(name,{exact:true})});
  await row.getByRole('button',{name:'编辑',exact:true}).click();
  await page.getByPlaceholder('任务名称',{exact:true}).fill(name+'-edited');
  const edited=response(page,taskPath,'PATCH');await page.getByRole('button',{name:'保存任务',exact:true}).click();
  const edit=await edited;assert.equal(edit.status(),200);assert.ok(edit.request().headers()['if-match']);
  row=page.getByRole('row').filter({has:page.getByText(name+'-edited',{exact:true})});mark(stage);
  stage='pause_resume_real_api';
  const paused=response(page,taskPath+':pause','POST');await row.getByRole('button',{name:'暂停',exact:true}).click();assert.equal((await paused).status(),200);
  await row.getByText('PAUSED',{exact:true}).waitFor();await assert.doesNotReject(()=>row.getByRole('button',{name:'立即执行',exact:true}).isDisabled().then(v=>assert.equal(v,true)));
  const resumed=response(page,taskPath+':resume','POST');await row.getByRole('button',{name:'恢复',exact:true}).click();assert.equal((await resumed).status(),200);mark(stage);
  stage='live_running_progress_then_success';
  const triggered=response(page,taskPath+':trigger','POST');await row.getByRole('button',{name:'立即执行',exact:true}).click();const trigger=await triggered;assert.equal(trigger.status(),202);assert.ok(trigger.request().headers()['idempotency-key']);
  await page.getByRole('cell',{name:'RUNNING',exact:true}).waitFor({timeout:7000});
  await page.getByRole('cell',{name:/^(?:[1-9]|[1-9][0-9])%$/}).waitFor({timeout:7000});
  await page.getByRole('cell',{name:'SUCCESS',exact:true}).waitFor({timeout:15000});await persistedCompletion(page,task.taskId,'SUCCESS');mark(stage);
  stage='real_history_retry_failure';
  const fail=await page.request.post(origin+root,{headers:{Authorization:bearer},data:{name:name+'-failure',taskType:'system.fail',cronExpression:'0 0 0 1 1 ? 2099',parameters:{},maxRetries:1,timeoutMs:30000}});assert.equal(fail.status(),201);const failing=(await fail.json()).data;
  await page.getByRole('button',{name:'刷新',exact:true}).click();
  const failureRow=page.getByRole('row').filter({has:page.getByText(name+'-failure',{exact:true})});
  const failedTrigger=response(page,root+'/'+failing.taskId+':trigger','POST');await failureRow.getByRole('button',{name:'立即执行',exact:true}).click();assert.equal((await failedTrigger).status(),202);
  await page.getByRole('cell',{name:'FAILED',exact:true}).waitFor({timeout:15000});
  const history=page.getByRole('row').filter({has:page.getByRole('cell',{name:'FAILED',exact:true})});await history.getByRole('cell',{name:'2',exact:true}).waitFor();await persistedCompletion(page,failing.taskId,'FAILED');mark(stage);
  await page.screenshot({path:resolve(evidence,'verified.png'),fullPage:true});
  result.status='SCOPED_TASK_UI_GATES_PASSED';
} catch(error) {result.status='FAILED';result.failures=1;result.failedStage=stage;result.failureType=error?.name||'Error';}
finally {if(browser)await browser.close();result.requests=requests;result.tests=result.checks.length+result.failures;await writeFile(output,JSON.stringify(result,null,2));console.log(JSON.stringify({status:result.status,checks:result.checks.length,failedStage:result.failedStage}));if(result.status==='FAILED')process.exitCode=1;}
