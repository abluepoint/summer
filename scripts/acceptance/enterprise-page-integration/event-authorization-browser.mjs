// New-candidate selected-B persisted revocation; no synthetic network or token edits.
import { createRequire } from 'node:module';
import { access, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
const {chromium}=createRequire(process.cwd()+'/package.json')('playwright-core');
const out=process.env.SUMMER_T34_EVIDENCE_DIR, b=process.env.SUMMER_EP_SECOND_ENTERPRISE, base='http://127.0.0.1:20573';
const result={checks:[],responses:[],networkMocking:false,complete:false,pageErrors:0};let browser,page;
const check=(name,passed)=>result.checks.push({name,passed:Boolean(passed)});
const response=()=>{const p=page.waitForResponse(r=>r.request().method()==='GET'&&new URL(r.url()).pathname==='/api/authorization-events'&&new URL(r.url()).searchParams.get('enterpriseId')===b);p.catch(()=>{});return p;};
try{
 browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
 page=await browser.newPage({viewport:{width:1440,height:1100}});page.setDefaultTimeout(25000);
 page.on('pageerror',()=>result.pageErrors++);page.on('response',r=>{const u=new URL(r.url());if(u.pathname==='/api/authorization-events'||u.pathname.endsWith('/menu-manifest'))result.responses.push({path:u.pathname,status:r.status(),enterpriseId:u.searchParams.get('enterpriseId')});});
 await page.goto(base+'/authorization-events');await page.locator('input[autocomplete="username"]').fill(process.env.SUMMER_E2E_USERNAME);await page.locator('input[autocomplete="current-password"]').fill(process.env.SUMMER_E2E_PASSWORD);await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(base+'/authorization-events');
 const select=page.locator('[aria-label="活动租户"]');await select.waitFor();
 if(!(await select.innerText()).includes(b)){const loaded=response();await select.click();await page.getByRole('option',{name:b,exact:true}).click();const reply=await loaded;check('selected_B_real_list',reply.status()===200&&(await reply.json()).data.items.every(x=>x.enterpriseId===b));}
 else {await page.getByRole('button',{name:'重试',exact:true}).first().waitFor();check('selected_B_real_list',result.responses.some(r=>r.enterpriseId===b&&r.status===200));}
 await page.getByRole('button',{name:'重试',exact:true}).first().waitFor();
 // Keep only the digest in browser memory; never emit the bearer token.
 await page.evaluate(async()=>{const {getAuthenticatedUser}=await import('/src/auth/oidc.ts');const user=await getAuthenticatedUser();window.eventTokenDigest=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(user.access_token)))).join(',');});
 await page.screenshot({path:resolve(out,'events-B-before-revocation.png'),fullPage:true});
 await writeFile(resolve(out,'browser-deny-B.request'),'request\n');let ready=false;
 for(let i=0;i<200;i++){try{await access(resolve(out,'browser-deny-B.done'));ready=true;break;}catch{}await new Promise(r=>setTimeout(r,100));}
 if(!ready)throw new Error('revocation_control_timeout');
 const denied=response();await page.getByRole('button',{name:'刷新',exact:true}).click();const reply=await denied;const body=await reply.json();
 await page.getByText(body.requestId,{exact:false}).waitFor();
 check('persisted_B_deny_returns403_without_data_and_with_requestId',reply.status()===403&&body.data==null&&body.requestId===reply.headers()['x-request-id']);
 check('selected_B_denial_clears_previous_events',await page.getByRole('button',{name:'重试',exact:true}).count()===0&&(await select.innerText()).includes(b));
 check('browser_keeps_same_JWT_after_B_permission_revocation',await page.evaluate(async()=>{const {getAuthenticatedUser}=await import('/src/auth/oidc.ts');const user=await getAuthenticatedUser();return window.eventTokenDigest===Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(user.access_token)))).join(',');}));
 await page.screenshot({path:resolve(out,'events-B-persisted-denied.png'),fullPage:true});check('no_unhandled_page_errors',result.pageErrors===0);result.complete=true;
}catch(error){result.failureType=error.name;result.localFrames=error.stack?.match(/event-authorization-browser\.mjs:\d+:\d+/g)||[];if(page&&new URL(page.url()).pathname==='/authorization-events')await page.screenshot({path:resolve(out,'incomplete.png'),fullPage:true}).catch(()=>{});}
finally{await writeFile(resolve(out,'browser-before-close.json'),JSON.stringify(result,null,2)+'\n');await browser?.close();result.closed=true;result.counts={tests:result.checks.length,failures:result.checks.filter(c=>!c.passed).length,errors:Number(Boolean(result.failureType)),skipped:0};await writeFile(resolve(out,'browser-result.json'),JSON.stringify(result,null,2)+'\n');}
process.exitCode=result.complete&&!result.counts.failures&&!result.counts.errors?0:1;
