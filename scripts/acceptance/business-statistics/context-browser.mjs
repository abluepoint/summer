// Independent shared-layout UX probe. No tokens, claim values or business bodies are retained.
import {createRequire} from 'node:module';
import {writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
const require=createRequire(process.cwd()+'/package.json');const {chromium}=require('playwright-core');
const out=process.env.SUMMER_T34_EVIDENCE_DIR,base='http://127.0.0.1:20573';let browser;
const result={networkMocking:false,menuRequests:[],complete:false};
try{
 browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
 const page=await browser.newPage({viewport:{width:1440,height:1100}});page.setDefaultTimeout(30000);
 page.on('response',r=>{const u=new URL(r.url());if(u.pathname.includes('menu-manifest'))result.menuRequests.push({method:r.request().method(),path:u.pathname,status:r.status()});});
 await page.goto(base+'/statistics');await page.locator('input[autocomplete="username"]').fill(process.env.SUMMER_E2E_USERNAME);await page.locator('input[autocomplete="current-password"]').fill(process.env.SUMMER_E2E_PASSWORD);await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(base+'/statistics');
 await page.getByText('对不起，您没有访问该资源的权限',{exact:true}).waitFor();
 result.claims=await page.evaluate(async()=>{const {getAuthenticatedUser}=await import('/src/auth/oidc.ts');const u=await getAuthenticatedUser();const token=u.access_token.split('.')[1];const c=JSON.parse(atob(token.replace(/-/g,'+').replace(/_/g,'/')));return {multipleActiveMemberships:Array.isArray(c.enterprise_memberships)&&c.enterprise_memberships.filter(m=>m.status==='ACTIVE').length>1,hasActiveEnterprise:typeof c.active_enterprise_id==='string'&&c.active_enterprise_id.length>0,hasOrganizationContext:Boolean(c.organization_context),hasStatisticsGrant:Object.values(c.enterprise_capabilities||{}).some(v=>Array.isArray(v)&&v.includes('enterprise.statistics.read'))};});
 result.tenantSelectorVisible=await page.locator('[aria-label="活动租户"]').count()>0;
 result.sharedUxBlocked=result.claims.multipleActiveMemberships&&result.claims.hasStatisticsGrant&&!result.claims.hasActiveEnterprise&&!result.tenantSelectorVisible;
 await page.screenshot({path:resolve(out,'statistics-no-active-context.png'),fullPage:true,animations:'disabled'});result.complete=true;
}catch(e){result.failureType=e.name;}
finally{await browser?.close();result.browserClosed=true;await writeFile(resolve(out,'context-probe-result.json'),JSON.stringify(result,null,2)+'\n');}
process.exitCode=result.complete?0:1;
