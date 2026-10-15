import {createRequire} from 'node:module';
import {writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
const require=createRequire(process.cwd()+'/package.json');const {chromium}=require('playwright-core');
const base='http://127.0.0.1:20573',api='/api/v1/enterprises/ent-acme/statistics',out=process.env.SUMMER_T34_EVIDENCE_DIR;
const result={checks:[],networkMocking:false,pageErrors:0,complete:false};let browser,page;
const check=(name,passed)=>{result.checks.push({name,passed:Boolean(passed)});console.log(name,passed?'PASS':'FAIL');};
try{
 browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
 page=await browser.newPage({viewport:{width:1440,height:1100}});page.setDefaultTimeout(30000);page.on('pageerror',()=>result.pageErrors++);
 let pending=page.waitForResponse(r=>r.request().method()==='GET'&&new URL(r.url()).pathname===api);pending.catch(()=>{});
 await page.goto(base+'/statistics');await page.locator('input[autocomplete="username"]').fill(process.env.SUMMER_E2E_USERNAME);await page.locator('input[autocomplete="current-password"]').fill(process.env.SUMMER_E2E_PASSWORD);await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(base+'/statistics');
 let response=await pending;let body=await response.json();await page.getByText('统计数据加载失败。',{exact:true}).waitFor();
 check('chrome_actual_query_failure_is_distinct_from_empty_and_forbidden',response.status()===500&&!(await page.getByText('当前范围和时间窗口没有数据。',{exact:true}).count())&&!(await page.getByText('没有查看统计数据的权限。',{exact:true}).count()));
 check('chrome_failure_request_id_visible',typeof body.requestId==='string'&&(await page.locator('body').innerText()).includes(body.requestId));
 pending=page.waitForResponse(r=>r.request().method()==='GET'&&new URL(r.url()).pathname===api);pending.catch(()=>{});await page.getByRole('button',{name:'重试',exact:true}).click();response=await pending;
 check('chrome_retry_reaches_real_failed_backend',response.status()===500);await page.screenshot({path:resolve(out,'statistics-query-failure.png'),fullPage:true,animations:'disabled',timeout:45000});
 check('chrome_no_unhandled_page_errors',result.pageErrors===0);result.complete=true;
}catch(error){result.failureType=error.name;result.localFrames=error.stack?.match(/failure-browser\.mjs:\d+:\d+/g)||[];if(page)result.failurePath=new URL(page.url()).pathname;}
finally{await writeFile(resolve(out,'browser-result.json'),JSON.stringify(result,null,2)+'\n');await browser?.close();result.browserClosed=true;await writeFile(resolve(out,'browser-result.json'),JSON.stringify(result,null,2)+'\n');}
process.exitCode=result.complete&&result.checks.every(c=>c.passed)?0:1;
