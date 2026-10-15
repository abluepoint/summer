import {createRequire} from 'node:module';
import {writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
const {chromium}=createRequire(process.cwd()+'/package.json')('playwright-core');
const base='http://127.0.0.1:21573',out=process.env.SUMMER_IDENTITY_EVIDENCE_DIR,mode=process.env.SUMMER_OAUTH_MODE;
const browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
process.on('SIGTERM',()=>browser.close().finally(()=>process.exit(1)));
const result={mode,checks:[],networkMocking:false,pageErrors:0};let stage='login',activePage;
const check=(name,passed,facts={})=>result.checks.push({name,passed:Boolean(passed),...facts});
const modal=p=>p.locator('.arco-modal:visible').filter({has:p.locator('form')});
const row=(p,id)=>p.locator('.arco-table').first().getByRole('row').filter({has:p.getByRole('cell',{name:id,exact:true})});
async function login(p,user,password){
 activePage=p;await p.goto(base+'/auth-users');await p.locator('input[autocomplete="username"]').fill(user);await p.locator('input[autocomplete="current-password"]').fill(password);
 await p.getByRole('button',{name:'登录',exact:true}).click();await p.waitForURL(base+'/auth-users');await p.goto(base+'/oauth-clients');
}
async function submit(p,method='PATCH'){
 const waiting=p.waitForResponse(r=>new URL(r.url()).pathname.startsWith('/api/security/oauth-clients')&&r.request().method()===method);waiting.catch(()=>{});
 await modal(p).getByRole('button',{name:'保存',exact:true}).click();return waiting;
}
try{
 const context=await browser.newContext({viewport:{width:1500,height:1100}});const page=await context.newPage();activePage=page;page.setDefaultTimeout(25000);page.on('pageerror',()=>result.pageErrors++);
 await login(page,process.env.SUMMER_E2E_USERNAME,process.env.SUMMER_E2E_PASSWORD);await row(page,'summer-ui').waitFor();
 if(mode==='fault'){
  stage='fault';await row(page,'t24-client').getByRole('button',{name:'编辑',exact:true}).click();await modal(page).getByRole('textbox',{name:'客户端名称',exact:true}).fill('Fault draft preserved');
  const response=await submit(page);const body=await response.json();await modal(page).locator('.arco-alert-error').waitFor();
  check('real_database_update_failure',response.status()>=500,{httpStatus:response.status()});
  check('database_failure_retains_draft_and_request_id',await modal(page).getByRole('textbox',{name:'客户端名称',exact:true}).inputValue()==='Fault draft preserved'&&Boolean(body.requestId)&&await modal(page).getByText(body.requestId,{exact:false}).count()>0);
  await page.screenshot({path:resolve(out,'oauth-fault.png'),fullPage:true,animations:'disabled'});
 }else{
  stage='metadata';check('protected_clients_and_consents_real_page',await page.locator('.arco-table').count()===2&&await page.getByText('授权同意',{exact:true}).count()>0);
  check('no_client_secret_editor',await page.locator('input[type="password"]').count()===0);
  const storage=await page.evaluate(()=>({local:Object.values(localStorage).some(v=>/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/.test(v)),session:Object.values(sessionStorage).some(v=>/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/.test(v))}));
  check('browser_tokens_not_in_web_storage',!storage.local&&!storage.session);
  check('browser_tokens_not_in_cookies',!(await context.cookies()).some(c=>/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/.test(c.value)));
  await page.screenshot({path:resolve(out,'oauth-metadata.png'),fullPage:true,animations:'disabled'});
  stage='create';await page.getByRole('button',{name:'注册 public 客户端',exact:true}).click();
  const fields=modal(page).getByRole('textbox');await fields.nth(0).fill('t24-browser');await fields.nth(1).fill('Browser OAuth client');await fields.nth(2).fill('http://example.test/callback');await fields.nth(3).fill('openid profile');
  const invalid=await submit(page,'POST');const invalidBody=await invalid.json();await modal(page).locator('.arco-alert-error').waitFor();
  check('invalid_redirect_real_http_400',invalid.status()===400,{httpStatus:invalid.status()});
  check('invalid_create_preserves_draft_request_id',await fields.nth(1).inputValue()==='Browser OAuth client'&&await modal(page).getByText(invalidBody.requestId,{exact:false}).count()>0);
  await fields.nth(2).fill(base+'/oauth2/browser-callback');const created=await submit(page,'POST');check('browser_public_client_registered',created.status()===201,{httpStatus:created.status()});await modal(page).waitFor({state:'hidden'});await row(page,'t24-browser').waitFor();
  const stale=await context.newPage();stale.setDefaultTimeout(25000);await stale.goto(base+'/oauth-clients');await row(stale,'t24-browser').waitFor();await row(stale,'t24-browser').getByRole('button',{name:'编辑',exact:true}).click();await modal(stale).getByRole('textbox',{name:'客户端名称',exact:true}).fill('Stale draft retained');
  stage='edit';await row(page,'t24-browser').getByRole('button',{name:'编辑',exact:true}).click();await modal(page).getByRole('textbox',{name:'客户端名称',exact:true}).fill('Edited browser client');const edited=await submit(page);check('browser_metadata_edit_persisted',edited.status()===200,{httpStatus:edited.status()});await modal(page).waitFor({state:'hidden'});await row(page,'t24-browser').getByText('Edited browser client',{exact:true}).waitFor();
  stage='conflict';activePage=stale;const conflict=await submit(stale);const conflictBody=await conflict.json();await modal(stale).locator('.arco-alert-error').waitFor();check('stale_browser_version_real_412',conflict.status()===412,{httpStatus:conflict.status()});
  check('conflict_preserves_draft_and_request_id',await modal(stale).getByRole('textbox',{name:'客户端名称',exact:true}).inputValue()==='Stale draft retained'&&await modal(stale).getByText(conflictBody.requestId,{exact:false}).count()>0);
  await stale.screenshot({path:resolve(out,'oauth-conflict.png'),fullPage:true,animations:'disabled'});await stale.close();activePage=page;
  stage='status';for(const status of ['DISABLED','ACTIVE']){
   const waiting=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/security/oauth-clients/t24-browser'&&r.request().method()==='PATCH');waiting.catch(()=>{});await row(page,'t24-browser').getByRole('switch').click();const response=await waiting;
   await row(page,'t24-browser').getByRole('cell',{name:status,exact:true}).waitFor();check('browser_status_'+status,response.status()===200,{httpStatus:response.status()});
  }
  stage='revoke_consent';const revoke=page.getByRole('button',{name:'撤销',exact:true});check('real_consent_record_in_management',await revoke.count()>0);
  if(await revoke.count()){
   const waiting=page.waitForResponse(r=>new URL(r.url()).pathname.startsWith('/api/security/oauth-consents/')&&r.request().method()==='DELETE');waiting.catch(()=>{});await revoke.first().click();const response=await waiting;check('browser_consent_revoked',response.status()===200,{httpStatus:response.status()});await page.getByText('授权同意已撤销。',{exact:true}).waitFor();
  }
  await page.screenshot({path:resolve(out,'oauth-edited-and-revoked.png'),fullPage:true,animations:'disabled'});
  stage='denied';const denied=await browser.newPage({viewport:{width:1500,height:1100}});denied.setDefaultTimeout(25000);await login(denied,process.env.SUMMER_E2E_READER,process.env.SUMMER_E2E_READER_PASSWORD);
  await denied.getByText(/对不起，您没有访问该资源的权限/).last().waitFor();check('ungranted_real_user_cannot_manage_clients',await denied.getByRole('button',{name:'注册 public 客户端',exact:true}).count()===0);await denied.screenshot({path:resolve(out,'oauth-denied.png'),fullPage:true,animations:'disabled'});
 }
 result.complete=true;
}catch(error){
 result.complete=false;result.failureType=error.name;result.failureStage=stage;
 if(activePage){result.failurePath=new URL(activePage.url()).pathname;const p=activePage.locator('input[autocomplete="current-password"]');if(await p.count())await p.fill('');await activePage.screenshot({path:resolve(out,`oauth-${mode}-safe-failure.png`),fullPage:true,animations:'disabled'}).catch(()=>{});}
}finally{
 result.counts={tests:result.checks.length,failures:result.checks.filter(c=>!c.passed).length,errors:result.complete?0:1,skipped:0};result.passed=result.complete&&result.pageErrors===0&&result.checks.every(c=>c.passed);
 await writeFile(resolve(out,`browser-${mode}.json`),JSON.stringify(result,null,2));await browser.close();
}
process.exitCode=result.passed?0:1;
