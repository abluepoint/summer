import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {writeFile} from 'node:fs/promises';
import {createInterface} from 'node:readline';
import {launchBrowser,closeBrowser} from '../data-scopes/browser-lifecycle.mjs';
const {chromium}=createRequire(process.cwd()+'/package.json')('playwright-core');
const out=process.env.SUMMER_RELEASE_UPGRADE_EVIDENCE_DIR,origin='http://127.0.0.1:23573',endpoint='/internal/v1/release/readiness';
const result={checks:[],networkMocking:false,pageErrors:0,errors:0,skipped:0};let owned,page;
const input=createInterface({input:process.stdin}),lines=[];let waiter;
input.on('line',line=>{if(waiter){const done=waiter;waiter=undefined;done(line);}else lines.push(line);});
async function signal(command){process.stdout.write(command+'\n');assert.equal(lines.length?lines.shift():await new Promise(resolve=>{waiter=resolve;}),'GO');}
async function flush(){result.tests=result.checks.length;result.failures=result.checks.filter(c=>!c.passed).length;await writeFile(out+'/browser-result.json',JSON.stringify(result,null,2));}
async function run(name,fn){try{await fn();result.checks.push({name,passed:true});}catch(e){result.checks.push({name,passed:false,errorType:e.name,localFrames:e.stack?.match(/browser\.mjs:\d+:\d+/g)||[]});if(page&&await page.locator('input[type=password]:visible').count()===0)await page.screenshot({path:out+'/'+name+'-red.png',fullPage:true}).catch(()=>{});throw e;}finally{await flush();}}
async function refresh(expected){const started=Date.now();const waiting=page.waitForResponse(r=>new URL(r.url()).pathname===endpoint&&r.request().method()==='GET');waiting.catch(()=>{});await page.getByRole('button',{name:'重新检查',exact:true}).click();const response=await waiting;const body=await response.json();(result.responses??=[]).push({status:response.status(),elapsedMs:Date.now()-started,cacheControl:response.headers()['cache-control']||null,fields:Object.keys(body),dataFields:body.data&&typeof body.data==='object'?Object.keys(body.data):[],success:typeof body.success==='boolean'?body.success:null,errorType:typeof body.error,code:body.error?.code||null,requestId:body.requestId,responseRequestId:response.headers()['x-request-id']});await flush();assert.ok((Array.isArray(expected)?expected:[expected]).includes(response.status()));assert.equal(body.requestId,response.headers()['x-request-id']);return body;}
try{
 owned=await launchBrowser(chromium);result.browserVersion=owned.browser.version();page=await owned.browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(25000);page.on('pageerror',()=>result.pageErrors++);
 await run('native_login_preflight_ten_checks',async()=>{
  await page.goto(origin+'/diagnostics/release');await page.locator('input[autocomplete=username]').fill(process.env.SUMMER_E2E_USERNAME);await page.locator('input[autocomplete=current-password]').fill(process.env.SUMMER_E2E_PASSWORD);await page.getByRole('button',{name:'登录',exact:true}).click();
  await page.getByRole('button',{name:'重新检查',exact:true}).waitFor();const body=await refresh(200);assert.equal(body.data.status,'READY');assert.equal(body.data.checks.length,10);assert.ok(body.data.checks.every(c=>c.passed));
  await page.getByText('运行条件就绪',{exact:true}).waitFor();result.tableRows={all:await page.locator('tbody tr').count(),checks:await page.locator('tbody > tr.arco-table-tr:visible').count()};assert.equal(await page.locator('tbody > tr.arco-table-tr:visible').count(),10);await page.getByText(body.requestId,{exact:true}).waitFor();await page.screenshot({path:out+'/preflight-ready.png',fullPage:true});
 });
 await signal('PAUSE_REDIS');
 let faultBody;
 try{
  faultBody=await refresh([200,503,500]);await page.screenshot({path:out+'/preflight-dependency-fault.png',fullPage:true});
  if(process.env.SUMMER_RELEASE_CACHE_FAULT==='true')await signal('CACHE_FAULT');
  await run('native_redis_fault_contract',async()=>{
   const status=result.responses.at(-1).status;
   if(status===200){assert.equal(faultBody.data.status,'NOT_READY');for(const name of ['cache','events','realtime'])assert.equal(faultBody.data.checks.find(c=>c.name===name).passed,false);await page.getByText('运行条件未就绪',{exact:true}).waitFor();result.faultContract='NOT_READY';}
   else{assert.equal(status,503);assert.ok((result.responses.at(-1).cacheControl||'').split(',').map(x=>x.trim()).includes('no-store'));assert.equal(faultBody.success,false);assert.equal(faultBody.error?.code,'SUMMER_SESSION_STORE_UNAVAILABLE');assert.equal(faultBody.error.message,'Shared session storage is unavailable');assert.ok(faultBody.data==null);await page.getByText('升级预检加载失败，请重试。',{exact:true}).waitFor();await page.getByText('requestId: '+faultBody.requestId,{exact:true}).waitFor();assert.equal(await page.getByText('运行条件就绪',{exact:true}).count(),0);assert.equal(await page.locator('tbody > tr.arco-table-tr:visible').count(),0);result.faultContract='SESSION_STORE_503';}
  }).catch(e=>{result.errors++;result.faultErrorType=e.name;});
 }finally{await signal('RESUME_REDIS');}
 await run('native_redis_recovery',async()=>{assert.equal((await refresh(200)).data.status,'READY');await page.screenshot({path:out+'/preflight-recovered.png',fullPage:true});});
 await run('native_persisted_denial_clears_previous_result',async()=>{
  await signal('DENY_RELEASE');const body=await refresh(403);assert.equal(body.error.code,'SUMMER_FORBIDDEN');await page.getByText('没有查看升级预检的权限。',{exact:true}).waitFor();await page.getByText('requestId: '+body.requestId,{exact:true}).waitFor();assert.equal(await page.getByText('运行条件就绪',{exact:true}).count(),0);assert.equal(await page.locator('tbody > tr.arco-table-tr:visible').count(),0);await page.screenshot({path:out+'/preflight-forbidden.png',fullPage:true});await signal('RESTORE_RELEASE');assert.equal((await refresh(200)).data.status,'READY');await page.screenshot({path:out+'/preflight-permission-restored.png',fullPage:true});
 });
 await run('native_cross_node_websocket_delivery_and_replay',async()=>{
  const observed=await page.evaluate(async()=>{
   const messages=[];const wait=(predicate)=>new Promise((resolve,reject)=>{const start=Date.now();const timer=setInterval(()=>{const v=predicate();if(v){clearInterval(timer);resolve(v);}else if(Date.now()-start>25000){clearInterval(timer);reject(new Error('WebSocket timeout'));}},50);});
   const socket=new WebSocket('ws://127.0.0.1:23082/ws/v1/messages?enterpriseId=ent-acme&after=0');socket.onmessage=e=>messages.push(JSON.parse(e.data));
   try{
    await wait(()=>messages.some(m=>m.type==='connected'));socket.send(JSON.stringify({type:'ping'}));await wait(()=>messages.some(m=>m.type==='pong'));
    const token=await(await import('/src/auth/oidc.ts')).getAccessTokenFromMemory();const sent=await fetch('/api/v1/enterprises/ent-acme/messages',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},body:JSON.stringify({clientMessageId:crypto.randomUUID(),recipient:'t36-admin',payload:{text:'release-replay-fixture'},ttlSeconds:300})});const body=await sent.json();if(sent.status!==201)return {http:sent.status};
    await wait(()=>messages.some(m=>m.type==='message'&&m.message.messageId===body.data.messageId));await wait(()=>messages.some(m=>m.type==='heartbeat'));
    const replay=[];const second=new WebSocket('ws://127.0.0.1:23082/ws/v1/messages?enterpriseId=ent-acme&after=0');second.onmessage=e=>replay.push(JSON.parse(e.data));try{await wait(()=>replay.some(m=>m.type==='message'&&m.message.messageId===body.data.messageId));}finally{second.close();}
    return {http:201,pong:true,delivery:true,heartbeat:true,replay:true};
   }finally{socket.close();}
  });assert.deepEqual(observed,{http:201,pong:true,delivery:true,heartbeat:true,replay:true});
 });
}catch(e){result.errors++;result.errorType=e.name;}
finally{input.close();process.stdin.destroy();try{result.shutdown=await closeBrowser(owned);}catch(e){result.errors++;result.shutdownErrorType=e.name;}await flush();}
process.exit(result.failures||result.errors||result.pageErrors?1:0);
