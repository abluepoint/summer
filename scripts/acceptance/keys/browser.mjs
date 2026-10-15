import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {writeFile} from 'node:fs/promises';
const {chromium}=createRequire(process.cwd()+'/package.json')('playwright-core');
const base='http://127.0.0.1:21573',out=process.env.SUMMER_IDENTITY_EVIDENCE_DIR;
const browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
process.on('SIGTERM',()=>browser.close().finally(()=>process.exit(1)));
const result={checks:[],networkMocking:false,pageErrors:0};let stage='login',activePage;
function check(name,passed,facts={}){result.checks.push({name,passed:Boolean(passed),...facts});}
async function login(page,user,password){
 activePage=page;stage='login_navigation';await page.goto(base+'/auth-users');
 stage='login_username';
 await page.locator('input[autocomplete="username"]').fill(user);
 stage='login_password';
 await page.locator('input[autocomplete="current-password"]').fill(password);
 stage='login_submit';
 await page.getByRole('button',{name:'登录',exact:true}).click();
 stage='login_callback';await page.waitForURL(base+'/auth-users');
 stage='key_page_navigation';await page.goto(base+'/security-keys');
}
try{
 const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(20000);page.on('pageerror',()=>result.pageErrors++);
 let metadataStatus=null;page.on('response',r=>{if(new URL(r.url()).pathname==='/api/security/keys')metadataStatus=r.status();});
 await login(page,process.env.SUMMER_E2E_USERNAME,process.env.SUMMER_E2E_PASSWORD);check('real_admin_chrome_pkce',true);stage='metadata';
 await page.getByText(/签名密钥|对不起，您没有访问该资源的权限/).last().waitFor();
 for(let i=0;i<100&&metadataStatus===null;i++)await page.waitForTimeout(100);
 check('authorized_key_page_real_metadata',metadataStatus===200,{status:metadataStatus});
 if(metadataStatus===200){
  await page.getByRole('cell',{name:'mysql',exact:true}).first().waitFor();
  check('metadata_source_algorithm_health_visible',await page.getByRole('cell',{name:'RS256',exact:true}).count()>0&&await page.getByRole('cell',{name:'READY',exact:true}).count()>0);
  check('page_has_no_private_key_editor',await page.locator('textarea,input[type="password"]').count()===0);
  await page.screenshot({path:resolve(out,'keys-metadata.png'),fullPage:true});
  stage='generate';const response=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/security/keys:rotate'&&r.request().method()==='POST');response.catch(()=>{});
  await page.getByRole('button',{name:'轮换密钥',exact:true}).click();const r=await response;check('restricted_ui_key_generation_real_http',r.status()===200,{status:r.status()});
  result.generated=r.status()===200;
  if(result.generated)await page.getByText('签名密钥已轮换。',{exact:true}).waitFor();
  await page.screenshot({path:resolve(out,'keys-generated.png'),fullPage:true});
 }else await page.screenshot({path:resolve(out,'keys-authorized-denied.png'),fullPage:true});
 stage='reader';const restricted=await browser.newPage({viewport:{width:1440,height:1000}});restricted.setDefaultTimeout(20000);restricted.on('pageerror',()=>result.pageErrors++);
 let deniedStatus=null;restricted.on('response',r=>{if(new URL(r.url()).pathname==='/api/security/keys')deniedStatus=r.status();});
 await login(restricted,process.env.SUMMER_E2E_READER,process.env.SUMMER_E2E_READER_PASSWORD);
 await restricted.getByText(/没有查看签名密钥的权限|对不起，您没有访问该资源的权限/).last().waitFor();
 check('ungranted_real_user_page_denied',await restricted.getByRole('button',{name:'轮换密钥',exact:true}).count()===0,{status:deniedStatus});
 await restricted.screenshot({path:resolve(out,'keys-ungranted.png'),fullPage:true});result.complete=true;
}catch(error){
 result.complete=false;result.failureType=error.name;result.failureStage=stage;
 if(activePage){
  result.failurePath=new URL(activePage.url()).pathname;
  const password=activePage.locator('input[autocomplete="current-password"]');
  if(await password.count())await password.fill('');
  await activePage.screenshot({path:resolve(out,'browser-failure-safe.png'),fullPage:true});
 }
}
finally{result.passed=result.complete&&result.pageErrors===0&&result.checks.every(c=>c.passed);await writeFile(resolve(out,'browser-result.json'),JSON.stringify(result,null,2));await browser.close();}
process.exitCode=result.passed?0:1;
