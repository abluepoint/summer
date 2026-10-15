import assert from 'node:assert/strict';
import {launchBrowser,closeBrowser} from '../data-scopes/browser-lifecycle.mjs';
import {createRequire} from 'node:module';
import {writeFile,rename} from 'node:fs/promises';
import {createInterface} from 'node:readline';
const require=createRequire(process.cwd()+'/package.json');const {chromium}=require('playwright-core');
const output=process.env.T20_OUTPUT,origin='http://127.0.0.1:26573';
const result={checks:[],requests:[],failures:0,errors:0,skipped:0,networkMocking:false};
const input=createInterface({input:process.stdin});const replies=[];input.on('line',line=>replies.shift()?.(JSON.parse(line)));
async function control(action){const reply=new Promise(resolve=>replies.push(resolve));console.log('CONTROL '+action);return reply;}
async function flush(){result.tests=result.checks.length;const temporary=output+'/browser-result.tmp';await writeFile(temporary,JSON.stringify(result,null,2)+'\n');await rename(temporary,output+'/browser-result.json');}
let owned,browser,page;
async function run(name,action){try{result.checks.push({name,passed:true,...await action()});}catch(error){result.failures++;result.checks.push({name,passed:false,errorType:error.name});}await flush();}
async function login(target,username,password){await target.goto(origin+'/customer/drawer');await target.locator('input[autocomplete="username"]').fill(username);await target.locator('input[autocomplete="current-password"]').fill(password);await target.getByRole('button',{name:/^(登录|login)$/i}).click();await target.getByRole('table').first().waitFor();}
try{
 owned=await launchBrowser(chromium);browser=owned.browser;
 const admin=await browser.newPage({viewport:{width:1450,height:1000}});admin.setDefaultTimeout(25000);
 await run('admin_real_pkce_publishes_frontend_manifest',async()=>{await login(admin,process.env.SUMMER_E2E_USERNAME,process.env.SUMMER_E2E_PASSWORD);await admin.getByRole('button',{name:'新增客户',exact:true}).waitFor();});
 const context=await browser.newContext({viewport:{width:1450,height:1000}});page=await context.newPage();page.setDefaultTimeout(18000);
 page.on('response',response=>{const path=new URL(response.url()).pathname;if(path.endsWith('/menu-manifest'))result.requests.push({path,status:response.status(),method:response.request().method(),at:Date.now()});});
 await run('reader_real_pkce_sees_granted_create_action',async()=>{await login(page,process.env.T20_READER,process.env.T20_READER_PASSWORD);await page.getByRole('button',{name:'新增客户',exact:true}).waitFor();await page.screenshot({path:output+'/before-revocation.png'});});
 if(['stability','transport'].includes(process.env.T20_BROWSER_MODE)){
  await run('unchanged_permission_poll_preserves_unsent_customer_draft',async()=>{
   await page.getByRole('button',{name:'新增客户',exact:true}).click();
   const draft=page.locator('#customer-displayName');await draft.fill('T20 unsent draft');
   const response=await page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/enterprises/ent-acme/menu-manifest'&&r.request().method()==='GET',{timeout:18000});
   assert.equal(response.status(),200);assert.equal(await draft.inputValue(),'T20 unsent draft');
   await page.screenshot({path:output+'/unchanged-permission-draft.png'});
  });
  if(process.env.T20_BROWSER_MODE==='transport'){
   await run('stalled_permission_read_expires_old_ui_access',async()=>{await control('stall_manifest');await page.getByText('对不起，您没有访问该资源的权限',{exact:true}).waitFor({timeout:30000});await page.screenshot({path:output+'/permission-transport-failed.png'});});
   await run('transport_recovery_restores_current_server_grants',async()=>{await control('unstall_manifest');await page.getByRole('button',{name:'新增客户',exact:true}).waitFor({timeout:18000});await page.screenshot({path:output+'/permission-transport-recovered.png'});});
  }
 }else{
 await run('live_action_revocation_refetches_capability_without_reload',async()=>{
  const prior=result.requests.length;const reply=await control('revoke_create');assert.equal(reply.status,403);assert.ok(reply.requestId);
  await page.getByRole('button',{name:'新增客户',exact:true}).waitFor({state:'hidden',timeout:18000});assert.ok(result.requests.length>prior);await page.screenshot({path:output+'/action-revoked.png'});return {nodeAWrite:true,nodeBApiDenied:403,menuRefetchCount:result.requests.length-prior};
 });
 await run('live_action_restore_recovers_without_reload',async()=>{const prior=result.requests.length;await control('restore_create');await page.getByRole('button',{name:'新增客户',exact:true}).waitFor({timeout:18000});assert.ok(result.requests.length>prior);});
 await run('live_menu_disable_rejects_current_route_without_reload',async()=>{const prior=result.requests.length;const reply=await control('disable_menu');assert.ok(reply.eventId);await page.getByText('对不起，您没有访问该资源的权限',{exact:true}).waitFor({timeout:18000});assert.ok(result.requests.length>prior);await page.screenshot({path:output+'/menu-revoked.png'});});
 await run('live_menu_restore_recovers_current_route',async()=>{await control('restore_menu');await page.getByRole('table').first().waitFor({timeout:18000});await page.getByRole('button',{name:'新增客户',exact:true}).waitFor();});
 }
 result.status=result.failures?'RED':'PASSED';
}catch(error){result.status='INCOMPLETE';result.errors++;result.errorType=error.name;}
finally{try{result.shutdown=await closeBrowser(owned);}catch(error){result.errors++;result.shutdownError=error.name;}input.close();process.stdin.destroy();await flush();}
process.exit(result.status==='PASSED'&&result.errors===0?0:1);
