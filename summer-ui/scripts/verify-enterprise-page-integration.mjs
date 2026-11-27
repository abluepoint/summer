import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import { chromium } from 'playwright-core';
let vite, browser;
before(async () => {
 vite = await createServer({root:fileURLToPath(new URL('..',import.meta.url)),logLevel:'error',cacheDir:process.env.SUMMER_ENTERPRISE_PAGE_CACHE_DIR,server:{port:20573,strictPort:true,hmr:false,watch:{ignored:['**/*']}},plugins:[{name:'enterprise-pages-fixture',configureServer(server){server.middlewares.use(async(req,res,next)=>{if(!req.url?.startsWith('/__enterprise_pages'))return next();res.setHeader('Content-Type','text/html');res.end(await server.transformIndexHtml(req.url,'<div id="root"></div><script type="module" src="/scripts/enterprise-page-integration-fixture.tsx"></script>'));});}}]});
 await vite.listen();browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
});
after(async()=>{await browser?.close();await vite?.watcher.close();vite?.httpServer.closeAllConnections();await vite?.close();});
async function page() {const p=await browser.newPage();p.setDefaultTimeout(6000);await p.goto('http://127.0.0.1:20573/__enterprise_pages');await p.waitForFunction(()=>window.fixture.requests.some(r=>r.tenant));await p.locator('.arco-card-header').first().waitFor();return p;}
async function choose(p,id){await p.locator('[aria-label="fixture-global-enterprise"]').click();await p.getByRole('option',{name:id,exact:true}).click();}
async function refresh(p){await p.getByRole('button',{name:'刷新',exact:true}).click();}
test('roles reads selected tenant B, then switches to A without old state',async()=>{const p=await page();try{assert.equal(await p.evaluate(()=>window.fixture.requests.find(r=>r.tenant).tenant),'tenant-b');await choose(p,'ent-acme');await p.waitForFunction(()=>window.fixture.requests.some(r=>r.tenant==='ent-acme'));assert.match(await p.locator('body').innerText(),/ent-acme/);}finally{await p.close();}});
test('roles creates and binds in selected B with dirty and busy guards',async()=>{const p=await page();try{
 await p.getByPlaceholder('角色名称',{exact:true}).fill('B role');await p.getByPlaceholder('角色编码',{exact:true}).fill('b-role');
 await p.locator('.arco-table').first().locator('label.arco-checkbox').first().click();
 await choose(p,'ent-acme');await p.getByText('切换租户会放弃未保存修改，是否继续？',{exact:true}).waitFor();await p.getByRole('button',{name:'继续编辑',exact:true}).click();
 await p.getByRole('button',{name:'创建角色',exact:true}).click();await p.waitForFunction(()=>window.fixture.requests.some(r=>r.method==='post'));
 await p.getByPlaceholder('用户名',{exact:true}).fill('alice');await p.waitForFunction(()=>window.fixture.requests.some(r=>r.url.includes('/users/')));
 await p.getByRole('button',{name:'保存分配',exact:true}).click();await p.waitForFunction(()=>window.fixture.requests.some(r=>r.method==='put'));
 assert.equal(await p.evaluate(()=>window.fixture.requests.filter(r=>['post','put'].includes(r.method)).every(r=>r.tenant==='tenant-b')),true);
}finally{await p.close();}});
test('roles clears old data on 403 and exposes requestId',async()=>{const p=await page();try{await p.evaluate(()=>window.fixture.deny=true);await refresh(p);await p.getByText(/fixture-denied/).first().waitFor();await p.getByPlaceholder('用户名',{exact:true}).fill('alice');assert.equal(await p.getByRole('button',{name:'保存分配',exact:true}).isDisabled(),true);}finally{await p.close();}});
test('roles ignores previous tenant late reads after the new tenant returns 403',async()=>{const p=await page();try{await p.evaluate(()=>{window.fixture.hold='/roles';});await refresh(p);await p.waitForFunction(()=>window.fixture.pending.length===1);await p.evaluate(()=>window.fixture.deny=true);await choose(p,'ent-acme');await p.getByText(/fixture-denied/).first().waitFor();await p.evaluate(()=>window.fixture.pending.shift().resolve());await new Promise(r=>setTimeout(r,100));assert.equal(await p.getByText(/fixture-denied/).count()>0,true);}finally{await p.close();}});

test('roles keeps edited denies after 412, retries latest version, and guards pending save',async()=>{const p=await page();try{
 await p.getByPlaceholder('用户名',{exact:true}).fill('alice');
 const save=p.getByRole('button',{name:'保存分配',exact:true});await p.waitForFunction(()=>window.fixture.requests.some(r=>r.url.includes('/users/')));await save.waitFor();
 await p.locator('.arco-checkbox-group label.arco-checkbox').first().click();await p.evaluate(()=>window.fixture.conflict=true);await save.click();
 await p.getByText(/授权已被其他操作修改/).waitFor();assert.equal(await p.locator('.arco-checkbox-group input').first().isChecked(),true);
 await p.evaluate(()=>window.fixture.hold='/users/');await save.click();await p.waitForFunction(()=>window.fixture.pending.length===1);
 assert.match(await p.locator('[aria-label="fixture-global-enterprise"]').getAttribute('class'),/arco-select-disabled/);
 const writes=await p.evaluate(()=>window.fixture.requests.filter(r=>r.method==='put'));
 assert.deepEqual(writes.map(r=>r.ifMatch),['"4"','"9"']);assert.deepEqual(writes[1].body.deniedCapabilityCodes,['GET:/api/customers']);assert.equal(writes.every(r=>r.tenant==='tenant-b'),true);
 await p.evaluate(()=>window.fixture.pending.shift().resolve());
}finally{await p.close();}});
test('roles ignores late catalog/list success after binding channel 403',async()=>{const p=await page();try{
 await p.evaluate(()=>window.fixture.hold='/roles');await refresh(p);await p.waitForFunction(()=>window.fixture.pending.length===1);
 await p.evaluate(()=>window.fixture.deny=true);await p.getByPlaceholder('用户名',{exact:true}).fill('alice');await p.getByText(/fixture-denied/).waitFor();
 await p.evaluate(()=>window.fixture.pending.shift().resolve());await new Promise(r=>setTimeout(r,100));assert.equal(await p.locator('.arco-table label.arco-checkbox').count(),0);assert.equal(await p.getByRole('button',{name:'保存分配',exact:true}).isDisabled(),true);assert.equal(await p.getByText(/fixture-denied/).count(),1);
}finally{await p.close();}});
