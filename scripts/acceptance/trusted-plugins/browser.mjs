import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {writeFile} from 'node:fs/promises';
import {createInterface} from 'node:readline';
import {launchBrowser,closeBrowser} from '../data-scopes/browser-lifecycle.mjs';
const {chromium}=createRequire(process.cwd()+'/package.json')('playwright-core');
const out=process.env.SUMMER_TRUSTED_PLUGINS_EVIDENCE_DIR,origin='http://127.0.0.1:21573',platform='/api/v1/platform/plugins',item=platform+'/customer-insights',summary='/api/v1/enterprises/ent-acme/plugins/customer-insights/summary';
const result={checks:[],networkMocking:false,pageErrors:0,errors:0,skipped:0};let owned,page;
const input=createInterface({input:process.stdin});const lines=[];let waiter;
input.on('line',line=>{if(waiter){const next=waiter;waiter=undefined;next(line);}else lines.push(line);});
async function signal(command){process.stdout.write(command+'\n');assert.equal(lines.length?lines.shift():await new Promise(r=>{waiter=r;}),'GO');}
async function flush(){result.tests=result.checks.length;result.failures=result.checks.filter(x=>!x.passed).length;await writeFile(out+'/browser-result.json',JSON.stringify(result,null,2));}
async function run(name,fn){try{const facts=await fn();result.checks.push({name,passed:true,...facts});}catch(e){result.checks.push({name,passed:false,errorType:e.name,localFrames:e.stack?.match(/browser\.mjs:\d+:\d+/g)||[]});if(page&&await page.locator('input[type=password]:visible').count()===0)await page.screenshot({fullPage:true,path:out+'/'+name+'-red.png'}).catch(()=>{});}await flush();}
function response(path,method){const p=page.waitForResponse(r=>new URL(r.url()).pathname===path&&r.request().method()===method);p.catch(()=>{});return p;}
const row=()=>page.getByRole('row').filter({hasText:'customer-insights'});
const example=()=>page.locator('.arco-card').filter({has:page.getByText('客户洞察示例',{exact:true})}).last();
async function fill(version,digest){await page.getByPlaceholder('插件 ID',{exact:true}).fill('customer-insights');await page.getByPlaceholder('版本',{exact:true}).fill(version);await page.getByPlaceholder('产物摘要',{exact:true}).fill(digest);}
async function register(expected){const w=response(platform,'POST');await page.getByRole('button',{name:'注册',exact:true}).click();const r=await w;assert.equal(r.status(),expected);return r.json();}
async function toggle(action,expected=200){const w=response(item+':'+action,'POST');await row().getByRole('button',{name:action==='enable'?'启用':'停用',exact:true}).click();const r=await w;assert.equal(r.status(),expected);return r.json();}
async function read(expected){const w=response(summary,'GET');await row().getByRole('button',{name:'读取示例',exact:true}).click();const r=await w;assert.equal(r.status(),expected);return r.json();}
try{
 owned=await launchBrowser(chromium);const context=await owned.browser.newContext({viewport:{width:1500,height:1100}});page=await context.newPage();page.setDefaultTimeout(20000);page.on('pageerror',()=>result.pageErrors++);
 await run('real_login_reaches_plugin_management',async()=>{await page.goto(origin+'/plugins');await page.locator('input[autocomplete=username]').fill(process.env.SUMMER_E2E_USERNAME);await page.locator('input[autocomplete=current-password]').fill(process.env.SUMMER_E2E_PASSWORD);await page.getByRole('button',{name:'登录',exact:true}).click();await page.getByPlaceholder('插件 ID',{exact:true}).waitFor();});
 await run('untrusted_artifact_registration_shows_correlated_error',async()=>{await fill('1.0.0','sha256:not-trusted');const b=await register(409);assert.equal(b.error.code,'SAMPLE_PLUGIN_UNTRUSTED');await page.locator('.arco-alert-error').filter({hasText:b.requestId}).waitFor();});
 await run('trusted_incompatible_version_is_blocked_and_diagnosed',async()=>{await fill('9.0.0','sha256:customer-insights-v1');await register(201);await row().getByText('INCOMPATIBLE',{exact:true}).waitFor();const b=await toggle('enable',409);assert.equal(b.error.code,'SAMPLE_PLUGIN_INCOMPATIBLE');const w=response(item+'/diagnostic','GET');await row().getByRole('button',{name:'诊断',exact:true}).click();const d=await(await w).json();assert.equal(d.data.compatible,false);assert.ok(d.data.problems.length);await page.getByText('false',{exact:true}).waitFor();await page.screenshot({fullPage:true,path:out+'/plugin-incompatible.png'});});
 await run('trusted_registration_enable_and_real_example',async()=>{await fill('1.0.0','sha256:customer-insights-v1');await register(201);await row().getByText('REGISTERED',{exact:true}).waitFor();await toggle('enable');await row().getByText('ENABLED',{exact:true}).waitFor();const b=await read(200);assert.ok(b.data.configuration.some(x=>x.configured));assert.equal(b.data.dictionary.items[0].label,'T35 browser published');await example().waitFor();assert.ok((await example().innerText()).includes(b.requestId));await page.screenshot({fullPage:true,path:out+'/plugin-enabled-example.png'});});
 await run('disable_hides_example_and_blocks_api',async()=>{await toggle('disable');await row().getByText('DISABLED',{exact:true}).waitFor();assert.equal(await row().getByRole('button',{name:'读取示例',exact:true}).count(),0);assert.equal(await example().count(),0);await signal('VERIFY_DISABLED');await toggle('enable');await row().getByText('ENABLED',{exact:true}).waitFor();await read(200);await example().waitFor();});
 await run('persisted_plugin_api_denial_clears_previous_insights',async()=>{await signal('DENY_READ');const b=await read(403);assert.equal(b.error.code,'SUMMER_FORBIDDEN');await page.locator('.arco-alert-error').filter({hasText:b.requestId}).waitFor();assert.equal(await example().count(),0);});
 await run('real_audit_failure_shows_correlated_error',async()=>{await signal('AUDIT_FAULT');const b=await read(500);assert.match(b.error.code,/^SUMMER_/);await page.locator('.arco-alert-error').filter({hasText:b.requestId}).waitFor();await page.screenshot({fullPage:true,path:out+'/plugin-audit-fault.png'});await signal('RECOVER');await read(200);});
 await run('declared_plugin_menu_path_reaches_extension',async()=>{await page.goto(origin+'/plugins/customer-insights');await page.getByText('客户洞察示例',{exact:true}).waitFor({timeout:8000});});
}catch(e){result.errors++;result.errorType=e.name;result.localFrames=e.stack?.match(/browser\.mjs:\d+:\d+/g)||[];}
finally{input.close();process.stdin.destroy();try{result.shutdown=await closeBrowser(owned);}catch(e){result.errors++;result.shutdownErrorType=e.name;}await flush();}
process.exit(result.failures||result.errors||result.pageErrors?1:0);
