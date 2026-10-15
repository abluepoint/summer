import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {writeFile} from 'node:fs/promises';
import {createInterface} from 'node:readline';
import {launchBrowser,closeBrowser} from '../data-scopes/browser-lifecycle.mjs';
const {chromium}=createRequire(process.cwd()+'/package.json')('playwright-core');
const out=process.env.SUMMER_TRUSTED_PLUGINS_EVIDENCE_DIR,origin='http://127.0.0.1:21573',item='/api/v1/platform/plugins/customer-insights',summary='/api/v1/enterprises/ent-acme/plugins/customer-insights/summary',manifest='/api/v1/enterprises/ent-acme/plugins';
const result={checks:[],networkMocking:false,pageErrors:0,errors:0,skipped:0};let owned,page,readerReady=false;
const input=createInterface({input:process.stdin});const lines=[];let waiter;
input.on('line',line=>{if(waiter){const next=waiter;waiter=undefined;next(line);}else lines.push(line);});
async function signal(command){process.stdout.write(command+'\n');assert.equal(lines.length?lines.shift():await new Promise(r=>{waiter=r;}),'GO');}
async function flush(){result.tests=result.checks.length;result.failures=result.checks.filter(x=>x.passed===false).length;result.skipped=result.checks.filter(x=>x.skipped).length;await writeFile(out+'/browser-result.json',JSON.stringify(result,null,2));}
async function run(name,fn,requiresReader=false){if(requiresReader&&!readerReady){result.checks.push({name,skipped:true,reason:'enterprise_entry_unavailable'});await flush();return;}try{const facts=await fn();result.checks.push({name,passed:true,...facts});}catch(e){result.checks.push({name,passed:false,errorType:e.name,localFrames:e.stack?.match(/focused-browser\.mjs:\d+:\d+/g)||[]});if(page&&await page.locator('input[type=password]:visible').count()===0)await page.screenshot({fullPage:true,path:out+'/'+name+'-red.png'}).catch(()=>{});}await flush();}
function response(path,method='GET'){const p=page.waitForResponse(r=>new URL(r.url()).pathname===path&&r.request().method()===method);p.catch(()=>{});return p;}
async function login(username,password,path){await page.goto(origin+path);await page.locator('input[autocomplete=username]').fill(username);await page.locator('input[autocomplete=current-password]').fill(password);await page.getByRole('button',{name:'登录',exact:true}).click();}
const row=()=>page.getByRole('row').filter({hasText:'customer-insights'});
const insights=()=>page.locator('.arco-descriptions').filter({hasText:'启用客户数'});
async function read(status){const w=response(summary);await row().getByRole('button',{name:'读取示例',exact:true}).click();const r=await w;assert.equal(r.status(),status);return r.json();}
async function refresh(){const w=response(manifest);await page.getByRole('button',{name:'刷新',exact:true}).click();await w;}
async function noInsights(){await insights().waitFor({state:'detached'});}
try{
 owned=await launchBrowser(chromium);let context=await owned.browser.newContext({viewport:{width:1500,height:1100}});page=await context.newPage();page.setDefaultTimeout(12000);page.on('pageerror',()=>result.pageErrors++);
 if(process.env.SUMMER_T35_FOCUS==='denied-route'){
  await login(process.env.SUMMER_E2E_USERNAME,process.env.SUMMER_E2E_PASSWORD,'/plugins');await row().waitFor();await signal('UPGRADE');
  await context.close();context=await owned.browser.newContext({viewport:{width:1500,height:1100}});page=await context.newPage();page.setDefaultTimeout(20000);page.on('pageerror',()=>result.pageErrors++);
  await run('denied_reader_direct_route_renders_final_403',async()=>{const w=response(summary);await login(process.env.SUMMER_E2E_READER,process.env.SUMMER_E2E_READER_PASSWORD,'/plugins/customer-insights');assert.equal((await w).status(),200);await insights().waitFor();await signal('DENY_READER');await refresh();await noInsights();await page.getByRole('menuitem',{name:'客户洞察',exact:true}).waitFor({state:'detached'});await page.reload();await page.getByText('对不起，您没有访问该资源的权限',{exact:true}).waitFor();await noInsights();assert.equal(await page.getByRole('menuitem',{name:'客户洞察',exact:true}).count(),0);await page.screenshot({fullPage:true,path:out+'/enterprise-denied-final.png'});});
 }else{
 await login(process.env.SUMMER_E2E_USERNAME,process.env.SUMMER_E2E_PASSWORD,'/plugins');await row().waitFor();
 await run('management_refresh_clears_obsolete_diagnostic',async()=>{const w=response(item+'/diagnostic');await row().getByRole('button',{name:'诊断',exact:true}).click();await w;await page.getByText('false',{exact:true}).waitFor();await signal('UPGRADE');await refresh();await row().getByText('ENABLED',{exact:true}).waitFor();assert.equal(await page.getByText('false',{exact:true}).count(),0);});
 await run('management_denial_clears_previous_insights',async()=>{await read(200);await insights().waitFor();await signal('DENY_READ');const b=await read(403);await page.locator('.arco-alert-error').filter({hasText:b.requestId}).waitFor();await noInsights();await page.screenshot({fullPage:true,path:out+'/management-denied.png'});});
 await run('management_success_clears_previous_error',async()=>{await signal('RESTORE_READ');await read(200);await insights().waitFor();await page.locator('.arco-alert-error').waitFor({state:'detached'});});
 await run('management_audit_failure_clears_data_and_recovers',async()=>{await signal('AUDIT_FAULT');try{const b=await read(500);await page.locator('.arco-alert-error').filter({hasText:b.requestId}).waitFor();await noInsights();}finally{await signal('RECOVER');}await read(200);await insights().waitFor();await page.locator('.arco-alert-error').waitFor({state:'detached'});});
 await context.close();context=await owned.browser.newContext({viewport:{width:1500,height:1100}});page=await context.newPage();page.setDefaultTimeout(12000);page.on('pageerror',()=>result.pageErrors++);
 await run('enterprise_reader_enters_declared_menu_without_platform_access',async()=>{const w=response(summary);await login(process.env.SUMMER_E2E_READER,process.env.SUMMER_E2E_READER_PASSWORD,'/plugins/customer-insights');await page.getByRole('menuitem',{name:'客户洞察',exact:true}).waitFor();const r=await w;assert.equal(r.status(),200);const b=await r.json();assert.equal(b.data.dictionary.items[0].label,'T35 browser published');await insights().waitFor();assert.equal(await page.getByPlaceholder('产物摘要',{exact:true}).count(),0);readerReady=true;await page.screenshot({fullPage:true,path:out+'/enterprise-insights.png'});});
 await run('disabled_plugin_hides_enterprise_data',async()=>{await signal('DISABLE_PLUGIN');await refresh();await page.getByText('插件未启用或当前账户无权访问。',{exact:true}).waitFor();await noInsights();await page.screenshot({fullPage:true,path:out+'/enterprise-disabled.png'});},true);
 await run('reenabled_plugin_recovers_enterprise_view',async()=>{await signal('ENABLE_PLUGIN');const w=response(summary);await refresh();assert.equal((await w).status(),200);await insights().waitFor();assert.equal(await page.getByText('插件未启用或当前账户无权访问。',{exact:true}).count(),0);},true);
 await run('revoked_enterprise_reader_loses_menu_and_data',async()=>{await signal('DENY_READER');await refresh();await noInsights();await page.getByRole('menuitem',{name:'客户洞察',exact:true}).waitFor({state:'detached',timeout:20000});await page.reload();await page.getByText('对不起，您没有访问该资源的权限',{exact:true}).waitFor();await noInsights();assert.equal(await page.getByRole('menuitem',{name:'客户洞察',exact:true}).count(),0);await page.screenshot({fullPage:true,path:out+'/enterprise-denied.png'});},true);
 await run('restored_reader_recovers_but_unknown_descendant_is_rejected',async()=>{await signal('RESTORE_READER');const w=response(summary);await page.goto(origin+'/plugins/customer-insights');assert.equal((await w).status(),200);await insights().waitFor();await page.goto(origin+'/plugins/customer-insights/unknown');await page.getByText('插件未启用或当前账户无权访问。',{exact:true}).waitFor();await noInsights();},true);
 }
}catch(e){result.errors++;result.errorType=e.name;result.localFrames=e.stack?.match(/focused-browser\.mjs:\d+:\d+/g)||[];}
finally{input.close();process.stdin.destroy();try{result.shutdown=await closeBrowser(owned);}catch(e){result.errors++;result.shutdownErrorType=e.name;}await flush();}
process.exit(result.failures||result.errors||result.pageErrors||result.skipped?1:0);
