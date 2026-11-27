// Causal component regression only: real React/Arco/page with deferred synthetic HTTP.
// This does not replace or count as the separate real-server Chrome acceptance.
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import {existsSync,readFileSync,mkdirSync,writeFileSync,copyFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {createHash} from 'node:crypto';
import {launchBrowser,closeBrowser} from '../../scripts/acceptance/data-scopes/browser-lifecycle.mjs';
const root=fileURLToPath(new URL('../',import.meta.url));
const require=createRequire(root+'package.json');
const {build}=require('esbuild'),{chromium}=require('playwright-core');
const output=process.env.SUMMER_PLUGIN_RACE_EVIDENCE_DIR;
assert.ok(output,'SUMMER_PLUGIN_RACE_EVIDENCE_DIR is required');mkdirSync(output,{recursive:true});
mkdirSync(resolve(output,'executed-inputs'),{recursive:true});copyFileSync(fileURLToPath(import.meta.url),resolve(output,'executed-inputs/verify-plugins-race-component.mjs'));copyFileSync(root+'src/pages/plugins/index.tsx',resolve(output,'executed-inputs/plugins-index.tsx'));
const entry=`
import React from 'react';import ReactDOM from 'react-dom';import axios from 'axios';
import Plugins from './src/pages/plugins';import {GlobalContext} from './src/context';
const controls=new Map();let sequence=0;
window.fixture={pending:[],unmountedWarnings:0};
const originalError=console.error;
console.error=(...args)=>{if(args.some(x=>String(x).includes('unmounted component')))window.fixture.unmountedWarnings++;originalError(...args);};
axios.defaults.adapter=config=>new Promise((resolve,reject)=>{
 const id=++sequence;window.fixture.pending.push({id,url:config.url,method:config.method,settled:false});
 controls.set(id,{config,resolve,reject});
});
window.fixture.settle=async(id,status,data)=>{
 const control=controls.get(id);if(!control)throw new Error('unknown response');controls.delete(id);
 window.fixture.pending.find(x=>x.id===id).settled=true;
 const requestId='component-'+id;
 const response={status,config:control.config,headers:{'x-request-id':requestId},data:status<400?{success:true,data,requestId}:{success:false,data:null,error:{code:'SUMMER_FORBIDDEN',message:'Synthetic denied response'},requestId}};
 if(status<400)control.resolve(response);else control.reject({isAxiosError:true,response});
 await new Promise(requestAnimationFrame);await new Promise(requestAnimationFrame);
};
window.fixture.unmount=()=>ReactDOM.unmountComponentAtNode(document.getElementById('root'));
ReactDOM.render(<GlobalContext.Provider value={{lang:'zh-CN'}}><Plugins/></GlobalContext.Provider>,document.getElementById('root'));
`;
const compiled=await build({stdin:{contents:entry,resolveDir:root,loader:'tsx'},bundle:true,write:false,target:'es2020',format:'iife',define:{'process.env.NODE_ENV':'"development"'},plugins:[{name:'source-alias',setup(b){b.onResolve({filter:/^@\/hooks\/useActiveEnterprise$/},()=>({path:'active',namespace:'tenant-fixture'}));b.onLoad({filter:/.*/,namespace:'tenant-fixture'},()=>({contents:"export default () => ({enterpriseId:'fixture-tenant'})"}));b.onResolve({filter:/^@\//},args=>{const base=root+'src/'+args.path.slice(2);const path=[base+'.ts',base+'.tsx',base+'/index.ts',base+'/index.tsx'].find(existsSync);if(!path)throw new Error('unknown source alias');return {path};});}}]});
const result={kind:'component-causal-deferred-http',realBackend:false,checks:[],pageErrors:0,counts:{tests:0,failures:0,errors:0,skipped:0},sourceSha256:createHash('sha256').update(readFileSync(root+'src/pages/plugins/index.tsx')).digest('hex')};
const platform='/api/v1/platform/plugins',enterprise='/api/v1/enterprises/fixture-tenant/plugins',summary=enterprise+'/customer-insights/summary',diagnostic=platform+'/customer-insights/diagnostic';
const plugin=(status='ENABLED',version='1.0.0')=>({pluginId:'customer-insights',version,status,trusted:true,menus:[],configuration:[]});
const list=(status,version)=>({items:[plugin(status,version)],revision:1});
const insight={pluginId:'customer-insights',version:'1.0.0',requestId:'STALE-READ',activeCustomerCount:777,configuration:[],dictionary:{code:'customer-segment',items:[]},auditRevision:1};
const diag={pluginId:'customer-insights',version:'9.0.0',status:'INCOMPATIBLE',trusted:true,compatible:false,diagnostic:'STALE-DIAGNOSTIC',problems:[]};
let owned,server;
function flush(){result.counts.tests=result.checks.length;result.counts.failures=result.checks.filter(x=>x.failure).length;result.counts.errors=result.checks.filter(x=>x.error).length;writeFileSync(resolve(output,'result.json'),JSON.stringify(result,null,2));}
async function pending(page,path,method='get',count=1){await page.waitForFunction(({path,method,count})=>window.fixture.pending.filter(x=>!x.settled&&x.url===path&&x.method===method).length>=count,{path,method,count});return page.evaluate(({path,method})=>window.fixture.pending.filter(x=>!x.settled&&x.url===path&&x.method===method).map(x=>x.id),{path,method});}
const settle=(page,id,status,data)=>page.evaluate(({id,status,data})=>window.fixture.settle(id,status,data),{id,status,data});
async function finishLoad(page,version='1.0.0',status='ENABLED'){const [a]=await pending(page,platform);await settle(page,a,200,list(status,version));}
const row=page=>page.getByRole('row').filter({hasText:'customer-insights'});
const insights=page=>page.locator('.arco-descriptions').filter({hasText:'启用客户数'});
async function run(name,fn){let context;try{context=await owned.browser.newContext();const page=await context.newPage();page.setDefaultTimeout(5000);page.on('pageerror',()=>result.pageErrors++);await page.goto('http://127.0.0.1:21573');await finishLoad(page);await row(page).getByText('ENABLED',{exact:true}).waitFor();await fn(page);result.checks.push({name,passed:true});}catch(error){result.checks.push({name,passed:false,[error.name==='AssertionError'?'failure':'error']:true,errorType:error.name});}finally{await context?.close();flush();}}
try{
 server=createServer((req,res)=>{res.setHeader('Content-Type',req.url==='/bundle.js'?'text/javascript':'text/html');res.end(req.url==='/bundle.js'?compiled.outputFiles[0].text:'<div id="root"></div><script src="/bundle.js"></script>');});
 await new Promise((yes,no)=>{server.once('error',no);server.listen(21573,'127.0.0.1',yes);});owned=await launchBrowser(chromium);
 await run('late_read_success_cannot_replace_newer_denial',async page=>{await row(page).getByRole('button',{name:'读取示例',exact:true}).click();await row(page).getByRole('button',{name:'读取示例',exact:true}).click();const [a,b]=await pending(page,summary,'get',2);await settle(page,b,403);await page.locator('.arco-alert-error').filter({hasText:'component-'+b}).waitFor();await settle(page,a,200,insight);assert.equal(await insights(page).count(),0);assert.ok((await page.locator('.arco-alert-error').innerText()).includes('component-'+b));});
 await run('late_diagnostic_cannot_replace_newer_denial',async page=>{await row(page).getByRole('button',{name:'诊断',exact:true}).click();await row(page).getByRole('button',{name:'诊断',exact:true}).click();const [a,b]=await pending(page,diagnostic,'get',2);await settle(page,b,403);await settle(page,a,200,diag);assert.equal(await page.getByText('STALE-DIAGNOSTIC',{exact:true}).count(),0);});
 await run('refresh_invalidates_inflight_insights_and_diagnostic',async page=>{await row(page).getByRole('button',{name:'读取示例',exact:true}).click();await row(page).getByRole('button',{name:'诊断',exact:true}).click();const [a]=await pending(page,summary),[b]=await pending(page,diagnostic);await page.getByRole('button',{name:'刷新',exact:true}).click();await finishLoad(page);await settle(page,a,200,insight);await settle(page,b,200,diag);assert.equal(await insights(page).count(),0);assert.equal(await page.getByText('STALE-DIAGNOSTIC',{exact:true}).count(),0);});
 await run('late_list_load_cannot_clear_newer_refresh_denial',async page=>{await page.getByRole('button',{name:'刷新',exact:true}).click();const [a]=await pending(page,platform);await page.getByPlaceholder('插件 ID',{exact:true}).fill('customer-insights');await page.getByPlaceholder('版本',{exact:true}).fill('1.0.0');await page.getByPlaceholder('产物摘要',{exact:true}).fill('sha256:customer-insights-v1');await page.getByRole('button',{name:'注册',exact:true}).click();const [mutation]=await pending(page,platform,'post');await settle(page,mutation,200,plugin());const [,b]=await pending(page,platform,'get',2);await settle(page,b,403);await page.locator('.arco-alert-error').filter({hasText:'component-'+b}).waitFor();await settle(page,a,200,list('ENABLED','9.9.9'));assert.equal(await row(page).getByText('9.9.9',{exact:true}).count(),0);assert.equal(await page.locator('.arco-alert-error').count(),1);});
 await run('lifecycle_start_invalidates_pending_read_and_diagnostic',async page=>{await row(page).getByRole('button',{name:'读取示例',exact:true}).click();await row(page).getByRole('button',{name:'诊断',exact:true}).click();const [a]=await pending(page,summary),[b]=await pending(page,diagnostic);await row(page).getByRole('button',{name:'停用',exact:true}).click();const [mutation]=await pending(page,platform+'/customer-insights:disable','post');await settle(page,a,200,insight);await settle(page,b,200,diag);assert.equal(await insights(page).count(),0);assert.equal(await page.getByText('STALE-DIAGNOSTIC',{exact:true}).count(),0);await settle(page,mutation,200,plugin('DISABLED'));await finishLoad(page,'1.0.0','DISABLED');await row(page).getByText('DISABLED',{exact:true}).waitFor();});
 await run('unmount_invalidates_pending_load_read_and_diagnostic',async page=>{await row(page).getByRole('button',{name:'读取示例',exact:true}).click();await row(page).getByRole('button',{name:'诊断',exact:true}).click();await page.getByRole('button',{name:'刷新',exact:true}).click();const ids=await page.evaluate(()=>window.fixture.pending.filter(x=>!x.settled).map(x=>({id:x.id,url:x.url})));await page.evaluate(()=>window.fixture.unmount());for(const p of ids)await settle(page,p.id,200,p.url===summary?insight:p.url===diagnostic?diag:list());assert.equal(await page.evaluate(()=>window.fixture.unmountedWarnings),0);});
}catch(error){result.setupErrorType=error.name;result.counts.errors++;}
finally{if(owned)result.shutdown=await closeBrowser(owned);if(server){server.closeAllConnections();await new Promise(done=>server.close(done));}flush();if(result.setupErrorType)result.counts.errors++;writeFileSync(resolve(output,'result.json'),JSON.stringify(result,null,2));}
process.exitCode=result.counts.failures||result.counts.errors||result.pageErrors?1:0;
