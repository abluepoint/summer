import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {writeFile} from 'node:fs/promises';
const {chromium}=createRequire(process.cwd()+'/package.json')('playwright-core');
const base='http://127.0.0.1:21573',out=process.env.SUMMER_IDENTITY_EVIDENCE_DIR;
const mode=process.env.SUMMER_ROTATION_MODE;
const browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
process.on('SIGTERM',()=>browser.close().finally(()=>process.exit(1)));
const result={mode,checks:[],networkMocking:false,pageErrors:0};let stage='login',activePage;
function check(name,passed,facts={}){result.checks.push({name,passed:Boolean(passed),...facts});}
async function login(page,user,password){
 activePage=page;stage='login';await page.goto(base+'/auth-users');
 await page.locator('input[autocomplete="username"]').fill(user);
 await page.locator('input[autocomplete="current-password"]').fill(password);
 await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(base+'/auth-users');
 await page.goto(base+'/security-keys');
}
async function rotate(page){
 const pending=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/security/keys:rotate'&&r.request().method()==='POST');pending.catch(()=>{});
 await page.getByRole('button',{name:'轮换密钥',exact:true}).click();
 return pending;
}
try{
 const context=await browser.newContext({viewport:{width:1440,height:1000}});
 const page=await context.newPage();activePage=page;page.setDefaultTimeout(25000);page.on('pageerror',()=>result.pageErrors++);
 await login(page,process.env.SUMMER_E2E_USERNAME,process.env.SUMMER_E2E_PASSWORD);
 await page.getByRole('cell',{name:'ACTIVE',exact:true}).first().waitFor();
 if(mode==='conflict-replay'){
  const stale=await context.newPage();stale.setDefaultTimeout(25000);await stale.goto(base+'/security-keys');
  await stale.getByRole('cell',{name:'ACTIVE',exact:true}).first().waitFor();
  const successful=await rotate(page);if(successful.status()!==200)throw new Error('setup_rotation_failed');
  activePage=stale;stage='conflict_replay';const response=await rotate(stale);const body=await response.json();
  await stale.locator('.arco-alert-error').waitFor();
  check('stale_page_real_conflict',response.status()===412,{httpStatus:response.status()});
  check('conflict_request_id_visible',Boolean(body.requestId)&&await stale.getByText(body.requestId,{exact:false}).count()>0);
  await stale.screenshot({path:resolve(out,'rotation-conflict-replay.png'),fullPage:true,animations:'disabled'});
 }else if(mode==='retention'){
  check('old_public_key_grace_retention_visible',await page.getByRole('cell',{name:'GRACE',exact:true}).count()>0&&await page.getByText('旧密钥保留至',{exact:true}).count()>0);
  await page.screenshot({path:resolve(out,'rotation-retention.png'),fullPage:true,animations:'disabled'});
 }else if(mode==='fault'){
  stage='database_fault';const r=await rotate(page);const body=await r.json();
  check('real_database_failure_visible',r.status()>=500,{httpStatus:r.status()});
  await page.locator('.arco-alert-error').waitFor();
  check('database_failure_request_id_visible',Boolean(body.requestId)&&await page.getByText(body.requestId,{exact:false}).count()>0);
  await page.screenshot({path:resolve(out,'rotation-fault.png'),fullPage:true,animations:'disabled'});
 }else{
  stage='invalid_input';const input=page.locator('.arco-input-number input');
  await input.fill('0');await input.blur();
  check('invalid_zero_retention_clamped',Number(await input.inputValue())>=1);
  await input.fill('366');await input.blur();
  check('invalid_excess_retention_clamped',Number(await input.inputValue())<=365);
  await input.fill('2');await input.blur();
  const stale=await context.newPage();stale.setDefaultTimeout(25000);await stale.goto(base+'/security-keys');
  await stale.getByRole('cell',{name:'ACTIVE',exact:true}).first().waitFor();
  const oldActive=await page.getByRole('row').filter({has:page.getByRole('cell',{name:'ACTIVE',exact:true})}).innerText();
  stage='success';const r=await rotate(page);const body=await r.json();
  check('real_ui_rotation_success',r.status()===200,{httpStatus:r.status()});
  await page.getByText('签名密钥已轮换。',{exact:true}).waitFor();
  await page.getByRole('cell',{name:body.data.activeKid,exact:true}).waitFor();
  const activeRow=await page.getByRole('row').filter({has:page.getByRole('cell',{name:'ACTIVE',exact:true})}).innerText();
  check('current_active_version_changes',activeRow!==oldActive&&activeRow.includes(String(body.data.version)));
  check('old_public_key_grace_retention_visible',await page.getByRole('cell',{name:'GRACE',exact:true}).count()>0&&await page.getByText('旧密钥保留至',{exact:true}).count()>0);
  await page.screenshot({path:resolve(out,'rotation-success.png'),fullPage:true,animations:'disabled'});
  stage='stale_version';activePage=stale;const conflict=await rotate(stale);const conflictBody=await conflict.json();
  check('stale_page_real_conflict',conflict.status()===412,{httpStatus:conflict.status()});
  await stale.locator('.arco-alert-error').waitFor();
  check('stale_page_retains_form',await stale.getByRole('button',{name:'轮换密钥',exact:true}).count()===1);
  check('conflict_request_id_visible',Boolean(conflictBody.requestId)&&await stale.getByText(conflictBody.requestId,{exact:false}).count()>0);
  await stale.screenshot({path:resolve(out,'rotation-conflict.png'),fullPage:true,animations:'disabled'});
  stage='denied';const restricted=await browser.newPage({viewport:{width:1440,height:1000}});restricted.setDefaultTimeout(25000);
  await login(restricted,process.env.SUMMER_E2E_READER,process.env.SUMMER_E2E_READER_PASSWORD);
  await restricted.getByText(/没有查看签名密钥的权限|对不起，您没有访问该资源的权限/).last().waitFor();
  check('ungranted_user_rotation_control_absent',await restricted.getByRole('button',{name:'轮换密钥',exact:true}).count()===0);
  await restricted.screenshot({path:resolve(out,'rotation-denied.png'),fullPage:true,animations:'disabled'});
 }
 result.complete=true;
}catch(error){
 result.complete=false;result.failureType=error.name;result.failureStage=stage;
 if(activePage){
  result.failurePath=new URL(activePage.url()).pathname;
  const input=activePage.locator('input[autocomplete="current-password"]');if(await input.count())await input.fill('');
  await activePage.screenshot({path:resolve(out,`rotation-${mode}-safe-failure.png`),fullPage:true,animations:'disabled'}).catch(()=>{});
 }
}finally{
 result.counts={tests:result.checks.length,failures:result.checks.filter(c=>!c.passed).length,errors:result.complete?0:1,skipped:0};
 result.passed=result.complete&&result.pageErrors===0&&result.checks.every(c=>c.passed);
 await writeFile(resolve(out,`browser-${mode}.json`),JSON.stringify(result,null,2));
 await browser.close();
}
process.exitCode=result.passed?0:1;
