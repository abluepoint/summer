import {createRequire} from 'node:module';
import {writeFile} from 'node:fs/promises';
import {createInterface} from 'node:readline';
import {resolve} from 'node:path';
const require=createRequire(process.cwd()+'/package.json');const {chromium}=require('playwright-core');
const base='http://127.0.0.1:20573',path='/diagnostics/runtime',api='/internal/v1/diagnostics/runtime',out=process.env.SUMMER_T33_EVIDENCE_DIR;
const result={checks:[],networkMocking:false,pageErrors:0,complete:false};let browser,page;
const check=(name,passed,facts={})=>{result.checks.push({name,passed:Boolean(passed),...facts});console.log(name,passed?'PASS':'FAIL');};
const input=createInterface({input:process.stdin});const commands=[];let waiter;input.on('line',line=>{if(waiter){const fn=waiter;waiter=null;fn(line);}else commands.push(line);});
const barrier=async name=>{console.log(name);if(commands.length)commands.shift();else await new Promise(r=>{waiter=r;});};
const observe=()=>{const pending=page.waitForResponse(r=>r.request().method()==='GET'&&new URL(r.url()).pathname===api);pending.catch(()=>{});return pending;};
async function newPage(){await page?.close();page=await browser.newPage({viewport:{width:1440,height:1100}});page.setDefaultTimeout(30000);page.on('pageerror',()=>result.pageErrors++);}
async function login(username,password){await page.goto(base+path);await page.locator('input[autocomplete="username"]').fill(username);await page.locator('input[autocomplete="current-password"]').fill(password);await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(base+path);}
const redisRow=()=>page.getByRole('row').filter({has:page.getByRole('cell',{name:'redis',exact:true})});
try{
 browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});await newPage();
 let pending=observe();await login(process.env.SUMMER_E2E_USERNAME,process.env.SUMMER_E2E_PASSWORD);let response=await pending;let body=await response.json();
 await page.getByRole('button',{name:'刷新',exact:true}).waitFor();await redisRow().getByText('正常',{exact:true}).waitFor();
 check('chrome_real_pkce_monitoring_page',response.status()===200);check('chrome_runtime_request_correlation',body.requestId===response.headers()['x-request-id']&&await page.getByText(body.requestId,{exact:true}).count()>0);
 const text=await page.locator('body').innerText();
 check('chrome_mysql_and_migration_state_visible',text.includes('数据库')&&text.includes('数据库迁移'));
 check('chrome_http_latency_error_rate_visible',/http.server.requests|HTTP|http_request/i.test(text)&&/延迟|latency|duration/i.test(text)&&/错误率|error.rate/i.test(text));
 check('chrome_audit_metrics_visible',text.includes('成功审计事件')&&text.includes('失败审计事件')&&text.includes('持久化事件总数'));
 check('chrome_window_and_trace_visible',text.includes('本进程启动以来')&&text.includes('最多缓存 5 秒')&&/^[a-f0-9]{32}$/.test(body.data.traceId||'')&&text.includes(body.data.traceId));
 check('chrome_http_rate_matches_response',body.data.metrics.http.errorRate!=null&&text.includes((body.data.metrics.http.errorRate*100).toFixed(2)+'%'));
 check('chrome_completed_modules_have_real_metrics',text.includes('认证尝试次数')&&text.includes('缓存命中次数')&&text.includes('当前 WebSocket 连接数'));
 await page.screenshot({path:resolve(out,'runtime-normal.png'),fullPage:true,animations:'disabled',timeout:45000});
 await barrier('REDIS_FAULT_READY');pending=observe();await page.getByRole('button',{name:'刷新',exact:true}).click();response=await pending;body=await response.json();
 await redisRow().getByText('异常',{exact:true}).waitFor();check('chrome_actual_redis_fault_is_not_healthy',response.status()===200&&body.data.components.some(x=>x.name==='redis'&&x.status==='DOWN'));
 await page.screenshot({path:resolve(out,'runtime-redis-fault.png'),fullPage:true,animations:'disabled',timeout:45000});
 await barrier('REDIS_RECOVER_READY');pending=observe();await page.getByRole('button',{name:'刷新',exact:true}).click();response=await pending;body=await response.json();
 await redisRow().getByText('正常',{exact:true}).waitFor();check('chrome_runtime_recovers_after_redis_fault',response.status()===200&&body.data.components.some(x=>x.name==='redis'&&x.status==='UP'));
 await newPage();await login(process.env.SUMMER_E2E_READER,process.env.SUMMER_E2E_READER_PASSWORD);await page.getByText('对不起，您没有访问该资源的权限',{exact:true}).waitFor();
 check('chrome_unassigned_reader_denied',true);await page.screenshot({path:resolve(out,'runtime-reader-denied.png'),fullPage:true,animations:'disabled',timeout:45000});
 check('chrome_no_unhandled_page_errors',result.pageErrors===0);result.complete=true;
}catch(error){result.failureType=error.name;result.localFrames=error.stack?.match(/t33-browser\.mjs:\d+:\d+/g)||[];if(page)result.failurePath=new URL(page.url()).pathname;}
finally{input.close();await browser?.close();await writeFile(resolve(out,'browser-t33.json'),JSON.stringify(result,null,2)+'\n');}
process.exitCode=result.complete&&result.checks.every(c=>c.passed)?0:1;
