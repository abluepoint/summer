import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {writeFile} from 'node:fs/promises';
import {createInterface} from 'node:readline';
import {launchBrowser,closeBrowser} from '../data-scopes/browser-lifecycle.mjs';
const {chromium}=createRequire(process.cwd()+'/package.json')('playwright-core');
const out=process.env.SUMMER_AUTHORIZED_CONFIGURATION_EVIDENCE_DIR,origin='http://127.0.0.1:21573';
const publicPath='/public/enterprises/ent-acme/dictionaries/customer-segment',writePath='/api/v1/enterprises/ent-acme/dictionaries/customer-segment/overrides/_tenant';
const result={checks:[],networkMocking:false,pageErrors:0,errors:0,skipped:0};let owned,page;
const input=createInterface({input:process.stdin});const lines=[];let waiter;
input.on('line',line=>{if(waiter){const next=waiter;waiter=undefined;next(line);}else lines.push(line);});
async function signal(command){process.stdout.write(command+'\n');assert.equal(lines.length?lines.shift():await new Promise(r=>{waiter=r;}),'GO');}
async function flush(){result.tests=result.checks.length;result.failures=result.checks.filter(x=>!x.passed).length;await writeFile(out+'/browser-result.json',JSON.stringify(result,null,2));}
async function run(name,fn){try{await fn();result.checks.push({name,passed:true});}catch(e){result.checks.push({name,passed:false,errorType:e.name});await flush();throw e;}await flush();}
function response(path,method){const p=page.waitForResponse(r=>new URL(r.url()).pathname===path&&r.request().method()===method);p.catch(()=>{});return p;}
async function login(user,password){const context=await owned.browser.newContext({viewport:{width:1500,height:1100}});page=await context.newPage();page.setDefaultTimeout(30000);page.on('pageerror',()=>result.pageErrors++);await page.goto(origin+'/dictionary/form');await page.locator('input[autocomplete=username]').fill(user);await page.locator('input[autocomplete=current-password]').fill(password);await page.getByRole('button',{name:'登录',exact:true}).click();await page.getByLabel('覆盖选项 JSON',{exact:true}).waitFor();}
const editor=()=>page.getByLabel('覆盖选项 JSON',{exact:true});
const formValue=label=>JSON.stringify([{value:'alpha',label,sortOrder:0,enabled:true}]);
async function publish(expected){const w=response(writePath,'PUT');await page.getByRole('button',{name:'发布字典覆盖',exact:true}).click();const r=await w;assert.equal(r.status(),expected);return r.json();}
async function refresh(expected){const w=response(publicPath,'GET');await page.getByRole('button',{name:'刷新选项',exact:true}).click();const r=await w;assert.equal(r.status(),expected);return r.json();}
try{
 owned=await launchBrowser(chromium);
 await run('actual_admin_login_and_public_snapshot',async()=>{await login(process.env.SUMMER_E2E_USERNAME,process.env.SUMMER_E2E_PASSWORD);const b=await refresh(200);assert.equal(b.data.items[0].label,'T19 基础选项');});
 await run('actual_authorized_override_publish_and_reread',async()=>{await editor().fill(formValue('T19 已发布覆盖'));const b=await publish(200);assert.equal(b.data.version,0);const view=await refresh(200);assert.equal(view.data.items[0].label,'T19 已发布覆盖');await page.screenshot({path:out+'/override-published.png',fullPage:true});});
 await run('invalid_json_preserved_without_request',async()=>{await editor().fill('{');await page.getByRole('button',{name:'发布字典覆盖',exact:true}).click();await page.getByText('请输入有效选项 JSON',{exact:true}).waitFor();assert.equal(await editor().inputValue(),'{');});
 await run('real_mysql_audit_failure_keeps_draft_and_request_id',async()=>{await signal('AUDIT_FAULT');const dirty=formValue('T19 待重试草稿');await editor().fill(dirty);const b=await publish(500);assert.match(b.error.code,/^SUMMER_/);assert.ok(b.requestId);await page.locator('.arco-alert-error').filter({hasText:b.requestId}).waitFor();assert.equal(await editor().inputValue(),dirty);await page.screenshot({path:out+'/override-fault.png',fullPage:true});});
 await run('same_page_retry_succeeds_after_fault_removed',async()=>{await signal('RECOVER');const b=await publish(200);assert.equal(b.data.version,1);assert.equal((await refresh(200)).data.items[0].label,'T19 待重试草稿');});
 await run('persisted_publish_denial_shown_by_real_reader',async()=>{await login(process.env.SUMMER_E2E_READER,process.env.SUMMER_E2E_READER_PASSWORD);await editor().fill(formValue('T19 拒绝写入'));const b=await publish(403);assert.equal(b.error.code,'SUMMER_FORBIDDEN');await page.locator('.arco-alert-error').filter({hasText:b.requestId}).waitFor();await page.getByText(/无权发布字典覆盖/).waitFor();await page.screenshot({path:out+'/override-write-denied.png',fullPage:true});});
 await run('persisted_read_denial_clears_consumer_and_draft',async()=>{await signal('REVOKE_READ');const b=await refresh(403);assert.equal(b.error.code,'SUMMER_FORBIDDEN');await page.locator('.arco-alert-error').filter({hasText:b.requestId}).waitFor();await page.getByText(/无权读取此字典/).waitFor();assert.equal(await editor().inputValue(),'[]');assert.equal(await page.locator('[data-testid=dictionary-version]').count(),0);await page.screenshot({path:out+'/override-read-denied.png',fullPage:true});});
}catch(e){result.errors++;result.errorType=e.name;result.localFrames=e.stack?.match(/browser\.mjs:\d+:\d+/g)||[];}
finally{input.close();process.stdin.destroy();try{result.shutdown=await closeBrowser(owned);}catch(e){result.errors++;result.shutdownErrorType=e.name;}await flush();}
process.exit(result.failures||result.errors||result.pageErrors?1:0);
