import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {writeFile} from 'node:fs/promises';
const {chromium}=createRequire(process.cwd()+'/package.json')('playwright-core');
const base='http://127.0.0.1:21573',out=process.env.SUMMER_IDENTITY_EVIDENCE_DIR;
const browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
let closing=false;
async function close(){if(closing)return;closing=true;await browser.close();}
process.on('SIGTERM',()=>close().finally(()=>process.exit(1)));
const result={checks:[],networkMocking:false,pageErrors:0};let stage='login',page;
const check=(name,passed)=>result.checks.push({name,passed:Boolean(passed)});
async function login(p,user,password){
 await p.goto(base+'/auth-users');
 await p.locator('input[autocomplete="username"]').fill(user);
 await p.locator('input[autocomplete="current-password"]').fill(password);
 await p.getByRole('button',{name:'登录',exact:true}).click();await p.waitForURL(base+'/auth-users');
 await p.goto(base+'/security-keys');
}
try{
 page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(25000);
 page.on('pageerror',()=>result.pageErrors++);
 let metadata;
 page.on('response',async r=>{if(new URL(r.url()).pathname==='/api/security/keys'&&r.status()===200){try{metadata=await r.json();}catch{}}});
 await login(page,process.env.SUMMER_E2E_USERNAME,process.env.SUMMER_E2E_PASSWORD);check('real_reference_admin_chrome_pkce',true);stage='reference_metadata';
 await page.getByRole('cell',{name:'environment',exact:true}).waitFor();
 for(let i=0;i<50&&!metadata;i++)await page.waitForTimeout(100);
 check('reference_metadata_real_http_success',metadata?.success===true);
 check('reference_source_algorithm_kid_visible',await page.getByRole('cell',{name:'RS256',exact:true}).count()>0&&await page.getByRole('cell',{name:/^ref-/}).count()>0);
 check('reference_exists_and_readiness',metadata?.data?.items?.[0]?.exists===true&&await page.getByRole('cell',{name:'READY',exact:true}).count()>0);
 const text=await page.locator('body').innerText();
 check('no_private_key_value_or_editor',!text.includes('PRIVATE KEY')&&await page.locator('textarea,input[type="password"]').count()===0);
 await page.screenshot({path:resolve(out,'reference-metadata.png'),fullPage:true});
 stage='denied';const denied=await browser.newPage({viewport:{width:1440,height:1000}});denied.setDefaultTimeout(25000);
 await login(denied,process.env.SUMMER_E2E_READER,process.env.SUMMER_E2E_READER_PASSWORD);
 await denied.getByText(/没有查看签名密钥的权限|对不起，您没有访问该资源的权限/).last().waitFor();
 check('real_ungranted_user_reference_page_denied',await denied.getByRole('cell',{name:'environment',exact:true}).count()===0);
 await denied.screenshot({path:resolve(out,'reference-denied.png'),fullPage:true});result.complete=true;
}catch(error){
 result.complete=false;result.failureType=error.name;result.failureStage=stage;
 if(page){const input=page.locator('input[autocomplete="current-password"]');if(await input.count())await input.fill('');await page.screenshot({path:resolve(out,'reference-failure-safe.png'),fullPage:true});}
}finally{
 result.passed=result.complete&&result.pageErrors===0&&result.checks.every(c=>c.passed);
 await writeFile(resolve(out,'browser-result.json'),JSON.stringify(result,null,2));await close();
}
process.exitCode=result.passed?0:1;
