import {createRequire} from 'node:module';
import {writeFile} from 'node:fs/promises';
import {createInterface} from 'node:readline';
import {resolve} from 'node:path';
const require=createRequire(process.cwd()+'/package.json');const {chromium}=require('playwright-core');
const out=process.env.SUMMER_T08_EVIDENCE_DIR,base='http://127.0.0.1:20573',path='/system-parameters';
const events='/api/v1/enterprises/ent-acme/system-parameter-events',eventId=process.env.SUMMER_E2E_EVENT_ID,key=process.env.SUMMER_E2E_EVENT_KEY;
const result={checks:[],httpErrors:[],networkMocking:false,pageErrors:0,complete:false};let browser,page,context;
const check=(name,passed,facts={})=>{result.checks.push({name,passed:Boolean(passed),...facts});console.log(name,passed?'PASS':'FAIL');};
const input=createInterface({input:process.stdin});const commands=[];let waiter;input.on('line',line=>{if(waiter){const fn=waiter;waiter=null;fn(line);}else commands.push(line);});
const barrier=async name=>{console.log(name);if(commands.length)commands.shift();else await new Promise(r=>{waiter=r;});};
const observe=(method='GET',suffix='')=>{const pending=page.waitForResponse(r=>r.request().method()===method&&new URL(r.url()).pathname===events+suffix);pending.catch(()=>{});return pending;};
const table=()=>page.locator('.arco-table').nth(1);
const row=()=>table().getByRole('row').filter({has:page.getByRole('cell',{name:key,exact:true})});
async function newPage(){await context?.close();context=await browser.newContext({viewport:{width:1600,height:1200}});page=await context.newPage();page.setDefaultTimeout(30000);page.on('pageerror',()=>result.pageErrors++);page.on('response',r=>{const path=new URL(r.url()).pathname;if(path.startsWith('/api/')&&r.status()>=400)result.httpErrors.push({path,status:r.status(),requestId:r.headers()['x-request-id']});});}
async function login(username,password){await page.goto(base+path);await page.locator('input[autocomplete="username"]').fill(username);await page.locator('input[autocomplete="current-password"]').fill(password);await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(base+path);await page.locator('[aria-label="活动租户"]').click();await page.getByRole('option',{name:'ent-acme',exact:true}).click();}
async function refresh(value){const pending=observe();await page.getByPlaceholder('键',{exact:true}).fill(value);return await pending;}
async function shot(name){await table().screenshot({path:resolve(out,name),animations:'disabled',timeout:45000});}
try{
 browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
 await newPage();let pending=observe();await login(process.env.SUMMER_E2E_USERNAME,process.env.SUMMER_E2E_PASSWORD);let observed=await pending;check('chrome_admin_initializes_real_menu_and_pkce_page',observed.status()===200);
 await newPage();pending=observe();await login(process.env.SUMMER_E2E_READER,process.env.SUMMER_E2E_READER_PASSWORD);observed=await pending;
 await row().getByText('DEAD_LETTER',{exact:true}).waitFor();check('chrome_reader_sees_real_dead_letter',observed.status()===200);
 check('chrome_delivery_metadata_visible',(await row().innerText()).includes('3')&&(await row().innerText()).includes('t08-'));
 pending=observe('POST','/'+eventId+'/retry');await row().getByRole('button',{name:'重试',exact:true}).click();observed=await pending;let data=await observed.json();
 await page.getByText('无权执行此配置操作',{exact:false}).first().waitFor();check('chrome_retry_denied_by_real_grant',observed.status()===403&&data.error?.code==='SUMMER_FORBIDDEN');
 check('chrome_retry_denial_has_correlation',await page.getByText(data.requestId,{exact:false}).count()>0&&observed.headers()['x-request-id']===data.requestId);
 check('chrome_denied_retry_keeps_dead_letter',(await row().innerText()).includes('DEAD_LETTER'));await shot('event-retry-denied.png');
 await newPage();pending=observe();await login(process.env.SUMMER_E2E_USERNAME,process.env.SUMMER_E2E_PASSWORD);observed=await pending;await row().getByText('DEAD_LETTER',{exact:true}).waitFor();
 check('chrome_admin_sees_delivery_details',observed.status()===200);
 pending=observe('POST','/'+eventId+'/retry');await row().getByRole('button',{name:'重试',exact:true}).click();observed=await pending;data=await observed.json();
 check('chrome_admin_retry_success',observed.status()===200&&data.success===true&&data.data.eventId===eventId&&data.data.status==='PENDING');
 check('chrome_success_retry_has_correlation',data.requestId===observed.headers()['x-request-id']);
 await barrier('ADMIN_RETRY_READY');observed=await refresh('event.');await row().getByText('PUBLISHED',{exact:true}).waitFor();check('chrome_retried_event_shows_published',observed.status()===200);await shot('event-retry-published.png');
 await barrier('FAULT_READY');observed=await refresh('event');data=await observed.json();await page.getByText(data.requestId,{exact:false}).first().waitFor();
 check('chrome_real_event_database_fault_visible',observed.status()===500&&String(data.error?.code).startsWith('SUMMER_'));
 check('chrome_fault_clears_delivery_rows',await table().getByRole('cell',{name:/^event\./}).count()===0);await shot('event-database-fault.png');
 await barrier('RECOVER_READY');observed=await refresh('event.');await row().getByText('PUBLISHED',{exact:true}).waitFor();check('chrome_event_list_recovers_after_database_restore',observed.status()===200);await shot('event-recovered.png');
 await barrier('READ_DENY_READY');await newPage();pending=observe();await login(process.env.SUMMER_E2E_READER,process.env.SUMMER_E2E_READER_PASSWORD);observed=await pending;data=await observed.json();
 await page.getByText('无权执行此配置操作',{exact:false}).first().waitFor();check('chrome_event_read_denied_by_real_grant',observed.status()===403&&data.error?.code==='SUMMER_FORBIDDEN');
 check('chrome_read_denial_clears_delivery_rows',await table().getByRole('cell',{name:/^event\./}).count()===0);
 const visibleErrors=(await page.locator('.arco-alert').allTextContents()).join(' ');
 check('chrome_read_denial_shows_request_id',data.requestId===observed.headers()['x-request-id']&&result.httpErrors.some(e=>e.status===403&&e.requestId&&visibleErrors.includes(e.requestId)));await shot('event-read-denied.png');
 check('chrome_no_unhandled_errors',result.pageErrors===0);result.complete=true;
}catch(error){result.failureType=error.name;result.failureCategory=error.message?.includes('strict mode violation')?'ambiguous_locator':error.name;result.localFrames=error.stack?.match(/t08-browser\.mjs:\d+:\d+/g)||[];if(page)result.failurePath=new URL(page.url()).pathname;}
finally{input.close();await browser?.close();await writeFile(resolve(out,'browser-t08.json'),JSON.stringify(result,null,2)+'\n');}
process.exitCode=result.complete&&result.checks.every(c=>c.passed)?0:1;
