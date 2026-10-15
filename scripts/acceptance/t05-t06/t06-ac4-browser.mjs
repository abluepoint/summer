import {createRequire} from 'node:module';
import {writeFile} from 'node:fs/promises';
import {createInterface} from 'node:readline';
import {resolve} from 'node:path';
const require=createRequire(process.cwd()+'/package.json');const {chromium}=require('playwright-core');
const out=process.env.SUMMER_CONFIG_EVIDENCE_DIR,origin='http://127.0.0.1:20573',list='/public/enterprises/ent-acme/dictionaries/customer-segment';
let browser,page;const result={checks:[],networkMocking:false,complete:false,pageErrors:0};const check=(name,passed,facts={})=>result.checks.push({name,passed:Boolean(passed),...facts});
const input=createInterface({input:process.stdin});const commands=[];let waiter;input.on('line',line=>{if(waiter){const fn=waiter;waiter=null;fn(line);}else commands.push(line);});
async function barrier(name){console.log(name);if(commands.length)commands.shift();else await new Promise(r=>{waiter=r;});}
const select=()=>page.locator('.arco-form-item').filter({has:page.getByText('客户分层',{exact:true})}).locator('.arco-select');
const itemResponse=value=>page.waitForResponse(r=>r.request().method()==='GET'&&new URL(r.url()).pathname===list+'/items/'+value,{timeout:10000}).catch(()=>null);
try{
 browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});page=await browser.newPage({viewport:{width:1440,height:1100}});page.setDefaultTimeout(30000);page.on('pageerror',()=>result.pageErrors++);
 await page.goto(origin+'/dictionary/form');await page.locator('input[autocomplete="username"]').fill(process.env.SUMMER_E2E_USERNAME);await page.locator('input[autocomplete="current-password"]').fill(process.env.SUMMER_E2E_PASSWORD);const initial=itemResponse('acceptance-option');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(origin+'/dictionary/form');await select().getByText('验收选项初版',{exact:true}).waitFor();check('real_form_establishes_selected_enabled_item',true);let observed=await initial;check('initial_selection_uses_real_item_contract',observed?.status()===200,{status:observed?.status()??null});
 await barrier('ITEM_DISABLE_READY');const selected=itemResponse('acceptance-option');const refreshed=page.waitForResponse(r=>r.request().method()==='GET'&&new URL(r.url()).pathname===list);await page.getByRole('button',{name:'刷新选项',exact:true}).click();const listResponse=await refreshed;const options=await listResponse.json();check('list_still_succeeds_and_filters_disabled_item',listResponse.status()===200&&options.data.items.every(x=>x.value!=='acceptance-option'));
 observed=await selected;const error=observed?await observed.json():null;check('disabled_selected_item_has_real_unified_404',observed?.status()===404&&error?.success===false&&error?.error?.code==='SUMMER_NOT_FOUND',{status:observed?.status()??null});
 if(error?.requestId)await page.getByText(new RegExp(error.requestId)).waitFor();check('disabled_selection_error_displays_request_id',Boolean(error?.requestId)&&(await page.locator('body').innerText()).includes(error.requestId));await page.screenshot({path:resolve(out,'selected-disabled.png'),fullPage:true});
 const fallback=itemResponse('fallback-option');await select().click();await page.getByRole('option',{name:'备用选项',exact:true}).click();observed=await fallback;check('reselection_validates_remaining_enabled_item',observed?.status()===200);if(observed?.status()===200)await page.locator('.arco-alert-error').waitFor({state:'hidden'});check('reselection_clears_selected_item_error',await page.locator('.arco-alert-error').count()===0);await page.screenshot({path:resolve(out,'selected-recovered.png'),fullPage:true});
 check('no_unhandled_browser_errors',result.pageErrors===0);result.complete=true;
}catch(error){result.failureType=error.name;result.failureCategory=error.message?.includes('strict mode violation')?'ambiguous_locator':error.name;result.localFrames=error.stack?.match(/t06-ac4-browser\.mjs:\d+:\d+/g)||[];if(page&&result.checks.length)await page.screenshot({path:resolve(out,'browser-failed.png'),fullPage:true}).catch(()=>{});}
finally{input.close();await browser?.close();await writeFile(resolve(out,'browser-ac4.json'),JSON.stringify(result,null,2)+'\n');}
process.exitCode=result.complete&&result.checks.every(x=>x.passed)?0:1;
