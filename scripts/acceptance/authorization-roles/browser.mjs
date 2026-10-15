import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {writeFile,rename} from 'node:fs/promises';
import {createInterface} from 'node:readline';
const require=createRequire(process.cwd()+'/package.json');
const {chromium}=require('playwright-core');
const output=process.env.ROLES_OUTPUT, origin='http://127.0.0.1:23573', base='/api/v1/enterprises/ent-acme';
const permission='GET:/api/customers', user=process.env.ROLES_UI_USERNAME;
const result={mode:'real-chrome-real-form-pkce-real-http',checks:[],requests:[],failures:0,errors:0,skipped:0};
const planned=['roles_page_real_admin_login','ui_create_role_from_scanned_resource','ui_assign_role_and_allow_actual_api','ui_add_explicit_deny_and_deny_actual_api','ui_remove_deny_using_latest_assignment_version','ui_invalid_role_is_rejected','ui_unauthorized_user_cannot_read_roles','ui_real_outage_and_recovery'];
if(process.env.ROLES_SHUTDOWN_ONLY==='1'){planned.length=0;result.mode='browser-shutdown-only';}
if(process.env.ROLES_DENIED_ONLY==='1')planned.splice(0,planned.length,'ui_unauthorized_user_cannot_read_roles');
async function flush(){result.tests=result.checks.length;result.passed=result.checks.filter(c=>c.status==='passed').length;result.skipped=planned.length-result.checks.length;await writeFile(output+'/browser-result.json.tmp',JSON.stringify(result,null,2)+'\n');await rename(output+'/browser-result.json.tmp',output+'/browser-result.json');}
function waitResponse(target,predicate){const waiting=target.waitForResponse(predicate);waiting.catch(()=>{});return waiting;}
const input=createInterface({input:process.stdin});const replies=[];input.on('line',line=>replies.shift()?.(JSON.parse(line)));
async function control(action){const reply=new Promise(resolve=>replies.push(resolve));console.log('CONTROL '+action);return reply;}
let browser,page,step,bearer,roleId;
async function run(name,action){
  if(!planned.includes(name))return;
  step=name;
  try{const evidence=await action();result.checks.push({name,status:'passed',...evidence});}
  catch(error){result.failures++;let detail=String(error.message).split('\n')[0].slice(0,400);for(const secret of [process.env.SUMMER_E2E_PASSWORD,process.env.ROLES_DENIED_PASSWORD,bearer,bearer?.replace(/^Bearer /,'')])if(secret)detail=detail.replaceAll(secret,'<redacted>');result.checks.push({name,status:'failed',error_type:error.name,step,detail});
    if(page && await page.locator('input[type="password"]:visible').count()===0)await page.screenshot({path:output+'/'+name+'-red.png',fullPage:false}).catch(()=>{});
  }
  await flush();
}
async function login(target,username,password){await target.goto(origin+'/roles');await target.locator('input[autocomplete="username"]').fill(username);await target.locator('input[autocomplete="current-password"]').fill(password);await target.getByRole('button',{name:/^(登录|login)$/i}).click();}
function track(target){target.on('request',request=>{if(new URL(request.url()).pathname.startsWith(base))bearer=request.headers().authorization||bearer;});target.on('response',response=>{const path=new URL(response.url()).pathname;if(path.startsWith(base)||path==='/api/v1/platform/interface-resources')result.requests.push({method:response.request().method(),path,status:response.status(),ifMatch:response.request().headers()['if-match']||null});});}
async function setCheckbox(box,checked){
 const metadata=await box.evaluate(el=>({tag:el.tagName,parentTag:el.parentElement.tagName,parentClass:el.parentElement.className}));
 result.checkboxMetadata??=[];result.checkboxMetadata.push(metadata);
 if(await box.isChecked()!==checked){const label=box.locator('xpath=ancestor::label[1]');assert.equal(await label.count(),1);await label.click();}
 assert.equal(await box.isChecked(),checked);
}
async function assignment(expected){
 const response=waitResponse(page,r=>new URL(r.url()).pathname===base+'/users/'+user+'/roles'&&r.request().method()==='PUT');
 await page.getByRole('button',{name:'保存分配',exact:true}).click();const actual=await response;const body=await actual.json();
 result.assignmentAttempts??=[];result.assignmentAttempts.push({status:actual.status(),submittedIfMatch:actual.request().headers()['if-match'],returnedVersion:body.data?.version,code:body.error?.code,requestId:body.requestId});assert.equal(actual.status(),expected);return body.data;
}
try{
 browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
 const context=await browser.newContext({viewport:{width:1500,height:1050}});page=await context.newPage();page.setDefaultTimeout(25000);track(page);
 await run('roles_page_real_admin_login',async()=>{await login(page,process.env.SUMMER_E2E_USERNAME,process.env.SUMMER_E2E_PASSWORD);await page.getByText('为用户分配角色',{exact:true}).waitFor();await page.getByRole('table').first().waitFor();assert.ok(result.requests.some(r=>r.path===base+'/roles'&&r.status===200));});
 await run('ui_create_role_from_scanned_resource',async()=>{
  let row=page.getByRole('row').filter({has:page.getByText(permission,{exact:true})});
  for(let i=0;i<60 && await row.count()===0;i++){await page.locator('.arco-pagination-item-next:not(.arco-pagination-item-disabled)').click();}
  result.locatorDiagnostics={rows:await row.count(),checkboxes:await row.getByRole('checkbox').count()};step='create-checkbox';await setCheckbox(row.getByRole('checkbox'),true);step='create-fill';await page.getByPlaceholder('角色名称',{exact:true}).fill('T15 Chrome role');await page.getByPlaceholder('角色编码',{exact:true}).fill('t15-chrome-role');
  const response=waitResponse(page,r=>new URL(r.url()).pathname===base+'/roles'&&r.request().method()==='POST');await page.getByRole('button',{name:'创建角色',exact:true}).click();const actual=await response;assert.equal(actual.status(),201);roleId=(await actual.json()).data.roleId;await page.getByText('角色已创建。',{exact:true}).waitFor();
  await page.screenshot({path:output+'/role-created.png',fullPage:false});return {http_status:201};
 });
 if(!roleId && planned.includes('ui_create_role_from_scanned_resource'))throw new Error('browser_setup_not_ready');
 await run('ui_assign_role_and_allow_actual_api',async()=>{assert.ok(roleId);const loaded=waitResponse(page,r=>new URL(r.url()).pathname===base+'/users/'+user+'/roles'&&r.request().method()==='GET');await page.getByPlaceholder('用户名',{exact:true}).fill(user);await loaded;await page.locator('.arco-select-view').click();await page.locator('.arco-select-option').filter({hasText:'T15 Chrome role (t15-chrome-role)'}).click();const saved=await assignment(200);assert.equal(saved.version,1);const access=await control('user_access');assert.equal(access.status,200);return {binding_version:saved.version,actual_business_status:access.status};});
 await run('ui_add_explicit_deny_and_deny_actual_api',async()=>{step='deny-checkbox';await setCheckbox(page.getByRole('checkbox',{name:permission,exact:true}),true);step='deny-save';const saved=await assignment(200);assert.equal(saved.version,2);const access=await control('user_access');assert.equal(access.status,403);return {binding_version:saved.version,actual_business_status:access.status};});
 await run('ui_remove_deny_using_latest_assignment_version',async()=>{await setCheckbox(page.getByRole('checkbox',{name:permission,exact:true}),false);const saved=await assignment(200);assert.equal(saved.version,3);const access=await control('user_access');assert.equal(access.status,200);return {binding_version:saved.version,actual_business_status:access.status};});
 await run('ui_invalid_role_is_rejected',async()=>{await page.getByPlaceholder('角色名称',{exact:true}).fill('   ');await page.getByPlaceholder('角色编码',{exact:true}).fill('t15-invalid-ui');const response=waitResponse(page,r=>new URL(r.url()).pathname===base+'/roles'&&r.request().method()==='POST');await page.getByRole('button',{name:'创建角色',exact:true}).click();const actual=await response;assert.equal(actual.status(),400);assert.equal((await actual.json()).error.code,'SUMMER_AUTHORIZATION_INVALID');await page.locator('.arco-alert-error').waitFor();return {http_status:400};});
 await run('ui_unauthorized_user_cannot_read_roles',async()=>{const denied=await browser.newContext({viewport:{width:1500,height:1050}});const target=await denied.newPage();target.setDefaultTimeout(25000);track(target);try{await login(target,process.env.ROLES_DENIED_USERNAME,process.env.ROLES_DENIED_PASSWORD);await target.getByText('对不起，您没有访问该资源的权限',{exact:true}).waitFor();assert.equal(await target.getByRole('row').filter({hasText:'GET:/api/customers'}).count(),0);await target.screenshot({path:output+'/roles-denied.png',fullPage:false});const access=await control('denied_access');assert.equal(access.status,403);assert.equal(access.code,'SUMMER_FORBIDDEN');return {ui_route_denied:true,http_status:access.status,code:access.code,requestId:access.requestId};}catch(error){if(await target.locator('input[type="password"]:visible').count()===0)await target.screenshot({path:output+'/roles-denied-diagnostic.png',fullPage:false}).catch(()=>{});throw error;}finally{await denied.close();}});
 await run('ui_real_outage_and_recovery',async()=>{await control('stop');const unavailable=waitResponse(page,r=>new URL(r.url()).pathname===base+'/roles'&&r.status()===502);await page.getByRole('button',{name:'刷新',exact:true}).click();await unavailable;await page.locator('.arco-alert-error').waitFor();await control('restart');const restored=waitResponse(page,r=>new URL(r.url()).pathname===base+'/roles'&&r.status()===200);await page.getByRole('button',{name:'刷新',exact:true}).click();await restored;await page.locator('.arco-alert-error').waitFor({state:'hidden'});return {outage_status:502,recovered_status:200};});
 result.status=result.failures?'RED':'PASSED';
}catch(error){result.status='INCOMPLETE';result.errors++;result.error_type=error.name;result.failed_step=step;}
finally{
 await flush();
 input.close();process.stdin.destroy();
 let timer;
 try {
  if(browser)await Promise.race([browser.close(),new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error('browser_shutdown_timeout')),15000);})]);
 }catch(error){result.status='INCOMPLETE';result.errors++;result.shutdown_error=error.message;}
 finally{clearTimeout(timer);await flush();}
}
process.exit(result.status==='PASSED'?0:1);
