import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {writeFile} from 'node:fs/promises';
import {createInterface} from 'node:readline';
const require=createRequire(process.cwd()+'/package.json');
const {chromium}=require('playwright-core');
const output=process.env.CATALOG_OUTPUT;
const origin='http://127.0.0.1:23573';
const api='/api/v1/platform/interface-resources';
const result={mode:'real-chrome-real-login-real-http',checks:[],failures:0,errors:0,skipped:0,requests:[]};
const planned=['real_pkce_login_catalog_history','real_revision_diff_removed_replaced','immutable_snapshot_and_invalid_compare_guard','real_invalid_http_correlation','real_backend_outage_and_retry','real_denied_user_page'];
let browser,stage,bearer,page,step;
const input=createInterface({input:process.stdin});
const replies=[]; input.on('line',line=>replies.shift()?.(line));
async function control(action){const reply=new Promise(resolve=>replies.push(resolve));console.log('CONTROL '+action);assert.equal(await reply,'ACK');}
const mark=()=>result.checks.push({name:stage,status:'passed'});
async function selectRevision(label,value){
 const select=page.getByRole('combobox',{name:label,exact:true});
 await select.click();const popup=await select.getAttribute('aria-controls');assert.ok(popup);
 await page.locator('#'+popup).getByRole('option',{name:'版本 '+value,exact:true}).click();
}
async function login(page,username,password){
  await page.goto(origin+'/interface-resources');
  await page.locator('input[autocomplete="username"]').fill(username);
  await page.locator('input[autocomplete="current-password"]').fill(password);
  await page.getByRole('button',{name:/^(登录|login)$/i}).click();
}
try{
 browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
 const context=await browser.newContext({viewport:{width:1500,height:1050}});page=await context.newPage();page.setDefaultTimeout(25000);
 page.on('request',request=>{if(new URL(request.url()).pathname===api)bearer=request.headers().authorization||bearer;});
 page.on('response',response=>{const path=new URL(response.url()).pathname;if(path.startsWith(api))result.requests.push({method:response.request().method(),path,status:response.status()});});
 stage=planned[0];await login(page,process.env.SUMMER_E2E_USERNAME,process.env.SUMMER_E2E_PASSWORD);
 await page.getByRole('tabpanel',{name:'当前版本'}).getByRole('cell',{name:process.env.CATALOG_REPLACEMENT,exact:true}).first().waitFor();
 await page.getByRole('tab',{name:'历史版本',exact:true}).click();
 assert.ok(result.requests.some(r=>r.path===api+'/history'&&r.status===200));mark();
 stage=planned[1];
 step='from-select';await selectRevision('起始版本',process.env.CATALOG_ORIGINAL);
 step='to-select';await selectRevision('目标版本',process.env.CATALOG_REPLACEMENT);
 const diff=page.waitForResponse(r=>new URL(r.url()).pathname===api+'/diff');await page.getByRole('button',{name:'比较版本',exact:true}).click();
 const response=await diff;assert.equal(response.status(),200);const query=new URL(response.url()).searchParams;assert.equal(query.get('from'),process.env.CATALOG_ORIGINAL);assert.equal(query.get('to'),process.env.CATALOG_REPLACEMENT);
 await page.getByRole('cell',{name:'已移除',exact:true}).first().waitFor();await page.getByRole('cell',{name:'已替换',exact:true}).first().waitFor();await page.getByRole('cell',{name:'需显式迁移权限',exact:true}).first().waitFor();mark();
 await page.screenshot({path:output+'/catalog-real-diff.png',fullPage:true});
 stage=planned[2];const snapshot=page.waitForResponse(r=>new URL(r.url()).pathname===api+'/history/'+process.env.CATALOG_ORIGINAL);
 await page.getByRole('button',{name:'查看版本 '+process.env.CATALOG_ORIGINAL,exact:true}).click();assert.equal((await snapshot).status(),200);await page.locator('.arco-modal:visible').waitFor();
 await page.locator('.arco-modal:visible .arco-modal-close-icon').click();
 step='same-revision';await selectRevision('起始版本',process.env.CATALOG_REPLACEMENT);
 assert.equal(await page.getByRole('button',{name:'比较版本',exact:true}).isDisabled(),true);mark();
 stage=planned[3];assert.ok(bearer);const invalid=await page.request.get(origin+api+'/history?size=0',{headers:{Authorization:bearer}});assert.equal(invalid.status(),400);const error=await invalid.json();assert.ok(error.requestId);assert.equal(error.error.code,'SUMMER_RESOURCE_HISTORY_INVALID');mark();
 stage=planned[4];await page.getByRole('tab',{name:'当前版本',exact:true}).click();await control('stop');const unavailable=page.waitForResponse(r=>new URL(r.url()).pathname===api&&r.status()===502);await page.getByRole('button',{name:'筛选',exact:true}).click();await unavailable;
 await page.locator('.arco-alert-error').waitFor();assert.equal(await page.getByRole('tab',{name:'历史版本',exact:true}).count(),1);
 await control('restart');const recovered=page.waitForResponse(r=>new URL(r.url()).pathname===api&&r.status()===200);await page.getByRole('button',{name:'筛选',exact:true}).click();await recovered;await page.getByRole('tab',{name:'历史版本',exact:true}).waitFor();mark();
 stage=planned[5];const denied=await browser.newContext();const deniedPage=await denied.newPage();deniedPage.setDefaultTimeout(25000);
 await login(deniedPage,process.env.CATALOG_DENIED_USERNAME,process.env.CATALOG_DENIED_PASSWORD);
 await deniedPage.getByText('对不起，您没有访问该资源的权限',{exact:true}).waitFor();assert.equal(await deniedPage.getByRole('tab',{name:'历史版本',exact:true}).count(),0);mark();
 await deniedPage.screenshot({path:output+'/catalog-real-denied.png',fullPage:true});await denied.close();result.status='PASSED';
}catch(error){result.status='FAILED';result.failures=1;result.failedStage=stage;result.failureType=error?.name||'Error';result.failureStep=step;if(stage!==planned[0]){let summary=String(error?.message||'');for(const secret of [process.env.SUMMER_E2E_PASSWORD,process.env.CATALOG_DENIED_PASSWORD,bearer])if(secret)summary=summary.replaceAll(secret,'<redacted>');result.failureSummary=summary.slice(0,1200);if(page && await page.locator('input[type=password]').count()===0)await page.screenshot({path:output+'/catalog-browser-failure.png',fullPage:true}).catch(()=>{});}}
finally{input.close();if(browser)await browser.close();result.tests=result.checks.length+result.failures;result.skipped=planned.filter(name=>!result.checks.some(c=>c.name===name)&&name!==result.failedStage).length;await writeFile(output+'/browser-result.json',JSON.stringify(result,null,2));console.log(JSON.stringify({status:result.status,tests:result.tests,failures:result.failures,skipped:result.skipped,failedStage:result.failedStage}));if(result.status==='FAILED')process.exitCode=1;}
