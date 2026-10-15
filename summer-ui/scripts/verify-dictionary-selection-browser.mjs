import assert from 'node:assert/strict';
import {before,after,test} from 'node:test';
import {fileURLToPath} from 'node:url';
import {createServer} from 'vite';
import {chromium} from 'playwright-core';
let vite,browser,origin,pageErrors=0;
const publicPath='/public/enterprises/ent-acme/dictionaries/customer-segment';
const items=[{value:'selected',label:'已选项',sortOrder:0,enabled:true},{value:'fallback',label:'备用项',sortOrder:1,enabled:true}];
const success=data=>({success:true,data,requestId:'selection-read'});
const failure=(status,id)=>({success:false,error:{code:status===403?'SUMMER_FORBIDDEN':'SUMMER_NOT_FOUND',message:'unavailable'},requestId:id});
before(async()=>{vite=await createServer({root:fileURLToPath(new URL('..',import.meta.url)),logLevel:'error',cacheDir:process.env.SUMMER_DICTIONARY_COMPONENT_CACHE_DIR,server:{port:0,hmr:false,watch:{ignored:['**/*']}},plugins:[{name:'dictionary-selection',configureServer(server){server.middlewares.use(async(req,res,next)=>{if(!req.url.startsWith('/__t18-t19/'))return next();res.setHeader('Content-Type','text/html');res.end(await server.transformIndexHtml(req.url,'<div id="root"></div><script type="module" src="/scripts/t18-t19-browser-fixture.tsx"></script>'));});}}]});await vite.listen();origin=`http://127.0.0.1:${vite.httpServer.address().port}`;browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});});
after(async()=>{await browser?.close();await vite?.watcher.close();vite?.httpServer.closeAllConnections();await vite?.close();assert.equal(pageErrors,0);});
const selection=page=>page.locator('.arco-form-item').filter({has:page.getByText('客户分层',{exact:true})}).locator('.arco-select');
async function pageFor(handler){const page=await browser.newPage();page.setDefaultTimeout(5000);page.setDefaultNavigationTimeout(30000);page.on('pageerror',()=>pageErrors++);await page.route('**/api/**',r=>r.fulfill({status:200,contentType:'text/event-stream',body:'event: connected\ndata: {}\n\n'}));await page.route('**/public/**',handler);await page.goto(origin+'/__t18-t19/dictionary');await selection(page).getByText('已选项',{exact:true}).waitFor();await page.waitForFunction(()=>![...document.querySelectorAll('button')].some(button=>button.textContent==='刷新选项'&&button.classList.contains('arco-btn-loading')));return page;}
const fulfill=(route,status,body)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify(body)});

test('refresh validates the selected disabled item, displays correlation and keeps other options consumable',async()=>{
 let disabled=false;const page=await pageFor(route=>{const path=new URL(route.request().url()).pathname;if(path===publicPath)return fulfill(route,200,success({code:'customer-segment',items:disabled?[items[1]]:items}));if(path.endsWith('/selected')&&disabled)return fulfill(route,404,failure(404,'selected-disabled'));return fulfill(route,200,success(path.endsWith('/fallback')?items[1]:items[0]));});
 try{disabled=true;await page.getByRole('button',{name:'刷新选项',exact:true}).click();await page.getByText(/selected-disabled/).waitFor();await selection(page).click();assert.deepEqual(await page.getByRole('option').allTextContents(),['备用项']);const consumed=page.waitForResponse(r=>new URL(r.url()).pathname===publicPath+'/items/fallback');await page.getByRole('option',{name:'备用项',exact:true}).click();assert.equal((await consumed).status(),200);await page.locator('.arco-alert-error').waitFor({state:'hidden'});}finally{await page.close();}
});

test('selection read denial clears consumer data and displays request correlation',async()=>{
 const page=await pageFor(route=>{const path=new URL(route.request().url()).pathname;return path===publicPath?fulfill(route,200,success({code:'customer-segment',items})):path.endsWith('/fallback')?fulfill(route,403,failure(403,'selection-denied')):fulfill(route,200,success(items[0]));});
 try{await selection(page).click();await page.getByRole('option',{name:'备用项',exact:true}).click();await page.getByText(/无权读取此字典.*selection-denied/).waitFor();assert.equal(await page.getByLabel('覆盖选项 JSON').inputValue(),'[]');assert.equal((await selection(page).innerText()).includes('已选项'),false);}finally{await page.close();}
});

test('a late failure for the old selection cannot replace a newer successful selection',async()=>{
 let hold=false,release,received;const pending=new Promise(r=>{received=r;});const page=await pageFor(async route=>{const path=new URL(route.request().url()).pathname;if(path===publicPath)return fulfill(route,200,success({code:'customer-segment',items}));if(path.endsWith('/selected')&&hold){received();await new Promise(r=>{release=r;});return fulfill(route,404,failure(404,'stale-selection'));}return fulfill(route,200,success(path.endsWith('/fallback')?items[1]:items[0]));});
 try{hold=true;await page.getByRole('button',{name:'刷新选项',exact:true}).click();await Promise.race([pending,new Promise((_,reject)=>setTimeout(()=>reject(new Error('selection request missing')),5000))]);await selection(page).click();const consumed=page.waitForResponse(r=>new URL(r.url()).pathname===publicPath+'/items/fallback');await page.getByRole('option',{name:'备用项',exact:true}).click();await consumed;const stale=page.waitForResponse(r=>new URL(r.url()).pathname===publicPath+'/items/selected'&&r.status()===404);release();await stale;assert.equal((await selection(page).innerText()).includes('备用项'),true);assert.equal(await page.locator('.arco-alert-error').count(),0);}finally{release?.();await page.close();}
});

test('a disabled dictionary and later restored snapshot preserve unsaved override input',async()=>{
 let unavailable=false;const page=await pageFor(route=>{const path=new URL(route.request().url()).pathname;if(path===publicPath&&unavailable)return fulfill(route,404,failure(404,'dictionary-disabled-dirty'));return fulfill(route,200,success(path===publicPath?{code:'customer-segment',items,version:1}:items[0]));});
 const draft='[{"value":"selected","label":"未保存覆盖","sortOrder":0,"enabled":true}]';
 try{await page.getByLabel('覆盖选项 JSON').fill(draft);unavailable=true;await page.getByRole('button',{name:'刷新选项',exact:true}).click();await page.getByText(/dictionary-disabled-dirty/).waitFor();assert.equal(await page.getByLabel('覆盖选项 JSON').inputValue(),draft);unavailable=false;await page.getByRole('button',{name:'刷新选项',exact:true}).click();await selection(page).getByText('已选项',{exact:true}).waitFor();assert.equal(await page.getByLabel('覆盖选项 JSON').inputValue(),draft);}finally{await page.close();}
});

test('authorization loss still clears unsaved override and server-derived consumer data',async()=>{
 let denied=false;const page=await pageFor(route=>{const path=new URL(route.request().url()).pathname;return denied?fulfill(route,403,failure(403,'dictionary-read-denied-dirty')):fulfill(route,200,success(path===publicPath?{code:'customer-segment',items,version:1}:items[0]));});
 try{await page.getByLabel('覆盖选项 JSON').fill('[{"value":"selected","label":"未保存"}]');denied=true;await page.getByRole('button',{name:'刷新选项',exact:true}).click();await page.getByText(/dictionary-read-denied-dirty/).waitFor();assert.equal(await page.getByLabel('覆盖选项 JSON').inputValue(),'[]');assert.equal((await selection(page).innerText()).includes('已选项'),false);}finally{await page.close();}
});

test('a forbidden override write shows the request id and releases the saving state',async()=>{
 const page=await pageFor(route=>{const path=new URL(route.request().url()).pathname;return fulfill(route,200,success(path===publicPath?{code:'customer-segment',items,version:1}:items[0]));});
 try{await page.route('**/api/v1/enterprises/ent-acme/dictionaries/customer-segment/overrides/_tenant',route=>fulfill(route,403,failure(403,'dictionary-write-denied')));await page.getByLabel('覆盖选项 JSON').fill('[{"value":"selected","label":"未保存","sortOrder":0,"enabled":true}]');await page.getByRole('button',{name:'发布字典覆盖',exact:true}).click();await page.getByText(/无权发布字典覆盖.*dictionary-write-denied/).waitFor();assert.equal(await page.getByRole('button',{name:'发布字典覆盖',exact:true}).isEnabled(),true);}finally{await page.close();}
});
