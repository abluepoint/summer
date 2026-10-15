import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {writeFile} from 'node:fs/promises';
import {launchBrowser,closeBrowser} from './browser-lifecycle.mjs';
const require=createRequire(process.cwd()+'/package.json'),{chromium}=require('playwright-core');
const out=process.env.DATA_SCOPES_OUTPUT,origin='http://127.0.0.1:23573',policy='/api/v1/enterprises/ent-acme/data-scopes/system-parameters';
const result={mode:'real-chrome-form-pkce-http',checks:[],requests:[],tests:0,failures:0,errors:0,skipped:0};
let owned,page;
async function flush(){result.tests=result.checks.length;result.failures=result.checks.filter(c=>!c.passed).length;await writeFile(out+'/browser-result.json',JSON.stringify(result,null,2));}
async function run(name,fn){try{const facts=await fn();result.checks.push({name,passed:true,...facts});}catch(e){result.checks.push({name,passed:false,errorType:e.name});if(page&&await page.locator('input[type=password]:visible').count()===0)await page.screenshot({path:out+'/'+name+'-red.png'}).catch(()=>{});}await flush();}
function response(path,method){const p=page.waitForResponse(r=>new URL(r.url()).pathname===path&&r.request().method()===method);p.catch(()=>{});return p;}
async function login(username,password){await page.goto(origin+'/data-scopes');await page.locator('input[autocomplete=username]').fill(username);await page.locator('input[autocomplete=current-password]').fill(password);await page.getByRole('button',{name:/^(登录|login)$/i}).click();}
function track(p){p.on('response',r=>{const path=new URL(r.url()).pathname;if(path.includes('/data-scopes'))result.requests.push({path,method:r.request().method(),status:r.status()});});}
try{
 owned=await launchBrowser(chromium);const browser=owned.browser;
 const c=await browser.newContext({viewport:{width:1440,height:1100}});page=await c.newPage();page.setDefaultTimeout(15000);track(page);
 await run('admin_opens_actual_scope_editor',async()=>{await login(process.env.SUMMER_E2E_USERNAME,process.env.SUMMER_E2E_PASSWORD);await page.getByRole('textbox',{name:'system-parameters condition tree'}).waitFor();});
 const editor=()=>page.getByRole('textbox',{name:'system-parameters condition tree'});
 const card=()=>page.locator('.arco-card').filter({has:editor()}).last();
 await run('invalid_json_keeps_input_without_write',async()=>{await editor().fill('{');const before=result.requests.filter(r=>r.method==='PUT').length;await card().getByRole('button',{name:'保存资源策略',exact:true}).click();await page.getByText('条件树必须是有效 JSON',{exact:true}).waitFor();assert.equal(await editor().inputValue(),'{');assert.equal(result.requests.filter(r=>r.method==='PUT').length,before);});
 await run('server_invalid_tree_shows_correlated_error',async()=>{await editor().fill(JSON.stringify({field:'OWNER',operator:'MATCHES',value:'x'}));const wait=response(policy,'PUT');await card().getByRole('button',{name:'保存资源策略',exact:true}).click();const r=await wait,b=await r.json();assert.equal(r.status(),400);assert.equal(b.error.code,'SUMMER_DATA_SCOPE_INVALID');await page.locator('.arco-alert-error').waitFor();assert.ok((await page.locator('.arco-alert-error').innerText()).includes(b.requestId));return {status:r.status(),code:b.error.code,requestId:b.requestId};});
 await run('valid_save_recovers_from_previous_error',async()=>{await editor().fill(JSON.stringify({field:'TENANT'}));const wait=response(policy,'PUT');await card().getByRole('button',{name:'保存资源策略',exact:true}).click();const r=await wait,b=await r.json();assert.equal(r.status(),200);await page.getByText('数据范围已保存。',{exact:true}).waitFor();await page.locator('.arco-alert-error').waitFor({state:'hidden',timeout:3000});return {status:200,version:b.data.version};});
 await run('refresh_preserves_saved_tree_and_preview_version',async()=>{let wait=response('/api/v1/enterprises/ent-acme/data-scopes','GET');await page.getByRole('button',{name:'刷新',exact:true}).click();await wait;assert.equal(JSON.parse(await editor().inputValue()).field,'TENANT');wait=response(policy+':preview','POST');await card().getByRole('button',{name:'预览',exact:true}).click();const r=await wait,b=await r.json();assert.equal(r.status(),200);assert.equal(b.data.allowed,true);await page.getByText('POLICY_ALLOW',{exact:true}).waitFor();await page.screenshot({path:out+'/scope-editor-preview.png'});return {policyVersion:b.data.policyVersion};});
 await run('real_operator_is_denied_scope_management',async()=>{const rc=await browser.newContext({viewport:{width:1440,height:1000}});page=await rc.newPage();page.setDefaultTimeout(15000);track(page);await login(process.env.DATA_SCOPES_READER_USERNAME,process.env.DATA_SCOPES_READER_PASSWORD);await page.getByText(/无权管理数据范围/).waitFor();assert.equal(await page.getByRole('textbox',{name:'system-parameters condition tree'}).count(),0);assert.ok(result.requests.some(r=>r.path.endsWith('/data-scopes')&&r.status===403));await page.screenshot({path:out+'/scope-management-denied.png'});});
}catch(e){result.errors++;result.errorType=e.name;}
finally{try{result.shutdown=await closeBrowser(owned);}catch(e){result.errors++;result.shutdownErrorType=e.name;}await flush();}
process.exit(result.failures||result.errors?1:0);
