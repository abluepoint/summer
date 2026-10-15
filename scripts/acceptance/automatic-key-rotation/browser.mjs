import {createRequire} from 'node:module';
import {writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {launchBrowser,closeBrowser} from '../data-scopes/browser-lifecycle.mjs';
const {chromium}=createRequire(new URL('../../../summer-ui/package.json',import.meta.url))('playwright-core');
const base='http://127.0.0.1:26573',out=process.env.SUMMER_TASK_CONFIGURATION_EVIDENCE_DIR;
const mode=process.env.SUMMER_ROTATION_MODE;
const result={mode,checks:[],networkMocking:false,pageErrors:0};let owned;
const check=(name,passed)=>result.checks.push({name,passed:Boolean(passed)});
try {
 owned=await launchBrowser(chromium);
 const page=await owned.browser.newPage({viewport:{width:1500,height:1050}});page.setDefaultTimeout(25000);
 page.on('pageerror',()=>result.pageErrors++);
 await page.goto(base+'/auth-users');
 await page.locator('input[autocomplete="username"]').fill(process.env.SUMMER_E2E_USERNAME);
 await page.locator('input[autocomplete="current-password"]').fill(process.env.SUMMER_E2E_PASSWORD);
 await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(base+'/auth-users');
 await page.goto(base+'/security-keys');
 if(mode==='denied') {
  await page.getByText(/没有查看签名密钥的权限|对不起，您没有访问该资源的权限/).last().waitFor();
  check('ungranted_user_cannot_view_automatic_schedule_or_rotate',await page.getByRole('button',{name:'轮换密钥',exact:true}).count()===0);
  await page.screenshot({path:resolve(out,'automatic-denied.png'),fullPage:true,animations:'disabled'});
 } else {
  const state=mode==='failed'?'FAILED':'SUCCEEDED';
  await page.locator('.arco-alert').filter({hasText:state}).waitFor();
  check('automatic_'+state.toLowerCase()+'_schedule_visible',true);
  if(mode==='failed') check('automatic_failure_count_visible',/1/.test(await page.locator('.arco-alert-error').innerText()));
  else check('active_and_retained_public_keys_visible',await page.getByRole('cell',{name:'ACTIVE',exact:true}).count()===1&&await page.getByRole('cell',{name:'GRACE',exact:true}).count()>0);
  await page.screenshot({path:resolve(out,'automatic-'+mode+'.png'),fullPage:true,animations:'disabled'});
  await page.goto(base+'/audit');
  const action='KEY_AUTOMATIC_ROTATION_'+(mode==='failed'?'FAILED':'SUCCEEDED');
  const row=page.getByRole('row').filter({has:page.getByRole('cell',{name:action,exact:true})});
  await row.waitFor();
  check('automatic_'+mode+'_record_visible_in_audit_management',await row.getByRole('cell',{name:'system',exact:true}).count()===1&&await row.getByText(/automatic-key-rotation-/).count()>0);
  await row.getByRole('button',{name:'查看',exact:true}).click();
  await page.locator('.arco-drawer').getByText(action,{exact:false}).waitFor();
  check('audit_detail_exposes_safe_operation_and_correlation',await page.locator('.arco-drawer').getByText(/automatic-key-rotation-/).count()>0);
  await page.screenshot({path:resolve(out,'automatic-'+mode+'-audit.png'),fullPage:true,animations:'disabled'});
 }
 result.complete=true;
} catch(error) {result.failureType=error.name;result.complete=false;}
finally {
 try {result.browserShutdown=await closeBrowser(owned);}
 catch(error) {result.complete=false;result.shutdownFailureType=error.name;}
 result.counts={tests:result.checks.length,failures:result.checks.filter(c=>!c.passed).length,errors:result.complete?0:1,skipped:0};
 result.passed=Boolean(result.complete)&&result.pageErrors===0&&result.checks.every(c=>c.passed);
 await writeFile(resolve(out,'browser-'+mode+'.json'),JSON.stringify(result,null,2));
}
process.exitCode=result.passed?0:1;
