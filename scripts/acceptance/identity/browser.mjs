import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {writeFile} from 'node:fs/promises';
const req=createRequire(process.cwd()+'/package.json');
const {chromium}=req('playwright-core');
const base='http://127.0.0.1:21573',out=process.env.SUMMER_IDENTITY_EVIDENCE_DIR;
const browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(20000);
const result={phase:'permission-probe',networkMocking:false,checks:[],pageErrors:0};
let stage='login',safeScreenshot=false;
page.on('pageerror',()=>result.pageErrors++);
function check(name,passed){result.checks.push({name,passed:Boolean(passed)});}
try {
 const usersReady=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/users'&&r.status()===200);
 await page.goto(base+'/auth-users');
 await page.locator('input[autocomplete="username"]').fill(process.env.SUMMER_E2E_USERNAME);
 await page.locator('input[autocomplete="current-password"]').fill(process.env.SUMMER_E2E_PASSWORD);
 await page.getByRole('button',{name:'登录',exact:true}).click();
 await page.waitForURL(base+'/auth-users');safeScreenshot=true;stage='users';
 await (await usersReady).finished();
 await page.getByRole('cell',{name:process.env.SUMMER_E2E_USERNAME,exact:true}).waitFor();
 await page.getByText('认证用户管理',{exact:true}).last().waitFor();
 check('real_chrome_pkce_login',true);
 check('authorized_user_create_control_visible',await page.getByRole('button',{name:'新建用户',exact:true}).count()===1);
 await page.screenshot({path:resolve(out,'users-permissions.png'),fullPage:true});
 stage='organizations';
 const treeReady=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/enterprises/ent-acme/org-tree'&&r.status()===200);
 await page.goto(base+'/organization');await page.getByText('组织与成员',{exact:true}).last().waitFor();
 const treeBody=await (await treeReady).json();const first=(treeBody.data||treeBody).items[0];
 await page.getByText(`${first.name} (${first.memberCount})`,{exact:true}).waitFor();
 check('authorized_organization_create_control_visible',await page.getByRole('button',{name:'新建组织',exact:true}).count()===1);
 check('authorized_member_directory_visible',await page.getByText('租户成员',{exact:true}).count()===1);
 await page.screenshot({path:resolve(out,'organizations-permissions.png'),fullPage:true});
 result.complete=true;
} catch(error) {
 result.complete=false;result.failureType=error.name;result.failureStage=stage;
 if(safeScreenshot)await page.screenshot({path:resolve(out,'permission-probe-failed.png'),fullPage:true});
} finally {
 result.passed=result.complete&&result.checks.every(c=>c.passed)&&result.pageErrors===0;
 await writeFile(resolve(out,'browser-permissions.json'),JSON.stringify(result,null,2));await browser.close();
}
process.exitCode=result.passed?0:1;
