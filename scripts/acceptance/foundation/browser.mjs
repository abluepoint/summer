import {resolve} from 'node:path';import {pathToFileURL} from 'node:url';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {writeFile} from 'node:fs/promises';
const require=createRequire(process.cwd()+'/package.json');
const {chromium}=require('playwright-core');
const phase=process.argv[2],out=pathToFileURL(resolve(process.env.SUMMER_FOUNDATION_EVIDENCE_DIR || process.cwd()+'/../.git/summer-foundation-acceptance')+'/'),base='http://127.0.0.1:20573';
const username='foundation'+process.env.SUMMER_E2E_RUN;
const browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(20000);
const result={phase,steps:[],pageErrors:0,networkMocking:false};page.on('pageerror',()=>result.pageErrors++);
function step(name){result.steps.push(name);}
const response=(method,path)=>page.waitForResponse(r=>r.request().method()===method&&new URL(r.url()).pathname===path);
async function login(path){await page.goto(base+path);await page.locator('input[autocomplete="username"]').fill(process.env.SUMMER_E2E_USERNAME);await page.locator('input[autocomplete="current-password"]').fill(process.env.SUMMER_E2E_PASSWORD);await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(base+path);step('real PKCE login');}
try{
 if(phase==='before'){
  await login('/customer/drawer?sort=username&direction=desc');
  await page.getByRole('button',{name:'新增客户',exact:true}).click();const drawer=page.locator('.arco-drawer');
  await drawer.getByRole('textbox',{name:'用户名',exact:true}).fill(username);await drawer.getByRole('textbox',{name:'邮箱',exact:true}).fill(username+'@example.test');await drawer.getByRole('textbox',{name:'名称',exact:true}).fill('Foundation acceptance');
  let pending=response('POST','/api/customers');await drawer.getByRole('button',{name:'创建',exact:true}).click();assert.equal((await pending).status(),200);await drawer.waitFor({state:'hidden'});step('create customer through UI');
  const row=page.getByRole('row').filter({hasText:username});await row.getByRole('button',{name:'编辑',exact:true}).click();await drawer.getByRole('textbox',{name:'名称',exact:true}).fill('Edited foundation');pending=response('PATCH','/api/customers/'+username);await drawer.getByRole('button',{name:'保存修改',exact:true}).click();assert.equal((await pending).status(),200);await drawer.waitFor({state:'hidden'});step('edit stable username through UI');
  await row.getByRole('button',{name:username,exact:true}).click();await page.getByRole('button',{name:'停用',exact:true}).click();pending=response('PATCH','/api/customers/'+username);await page.locator('.arco-modal').getByRole('button',{name:'停用',exact:true}).click();assert.equal((await pending).status(),200);await page.getByRole('button',{name:'启用',exact:true}).waitFor();step('deactivate through UI');
 }else if(phase==='after'){
  await login('/customer/drawer?sort=username&direction=desc');const row=page.getByRole('row').filter({hasText:username});await row.getByText('Edited foundation',{exact:true}).waitFor();step('edited inactive customer restored after restart');
  await row.getByRole('button',{name:username,exact:true}).click();await page.getByRole('button',{name:'启用',exact:true}).click();let pending=response('PATCH','/api/customers/'+username);await page.locator('.arco-modal').getByRole('button',{name:'启用',exact:true}).click();assert.equal((await pending).status(),200);await page.locator('.arco-modal').waitFor({state:'hidden'});step('reactivate through UI');
  await page.getByRole('button',{name:'删除',exact:true}).click();const modal=page.locator('.arco-modal');await modal.getByPlaceholder('准确的用户名').fill(username);pending=response('DELETE','/api/customers/'+username);await modal.getByRole('button',{name:'永久删除',exact:true}).click();const deleted=await pending;assert.equal(deleted.status(),204);assert.equal((await deleted.body()).length,0);await modal.waitFor({state:'hidden'});step('delete through UI with empty raw 204 response');
 }else if(phase==='extra'){
  await login('/customer/drawer');await page.getByRole('button',{name:'新增客户',exact:true}).click();const drawer=page.locator('.arco-drawer');await drawer.getByRole('button',{name:'创建',exact:true}).click();await drawer.getByText(/3–64/).waitFor();step('real page client validation');const duplicate='unique'+process.env.SUMMER_E2E_RUN;await drawer.getByRole('textbox',{name:'用户名',exact:true}).fill(duplicate);await drawer.getByRole('textbox',{name:'邮箱',exact:true}).fill('ui-'+duplicate+'@example.test');await drawer.getByRole('textbox',{name:'名称',exact:true}).fill('Duplicate UI attempt');const pending=response('POST','/api/customers');await drawer.getByRole('button',{name:'创建',exact:true}).click();const failure=await pending;assert.equal(failure.status(),409);const envelope=await failure.json();await drawer.getByText('requestId: '+envelope.requestId,{exact:true}).waitFor();assert.equal(await drawer.getByRole('textbox',{name:'用户名',exact:true}).inputValue(),duplicate);step('real MySQL conflict displayed with requestId and draft preserved');
 }else if(phase==='denied'){
  await login('/diagnostics/redis');await page.getByText('对不起，您没有访问该资源的权限',{exact:true}).waitFor();step('unassigned authenticated reader sees forbidden page');
 }else if(phase==='runtime-reader'){
  await login('/diagnostics/runtime');await page.getByText('对不起，您没有访问该资源的权限',{exact:true}).waitFor();step('unassigned reader denied runtime management page');
 }else if(['runtime','runtime-validation-failed','runtime-disabled'].includes(phase)){
  const pending=response('GET','/internal/v1/diagnostics/runtime');await login('/diagnostics/runtime');
  const diagnostic=await pending;assert.equal(diagnostic.status(),200);const envelope=await diagnostic.json();
  assert.ok(envelope.requestId);assert.equal(diagnostic.headers()['x-request-id'],envelope.requestId);assert.equal(envelope.data.requestId,envelope.requestId);
  const expectedMigration=phase==='runtime-validation-failed'?'VALIDATION_FAILED':phase==='runtime-disabled'?'NOT_ENABLED':'UP';const components=Object.fromEntries(envelope.data.components.map(item=>[item.name,item.status]));assert.equal(components.db,'UP');assert.equal(components.migrations,expectedMigration);
  await page.getByText('运行监控',{exact:true}).last().waitFor();
  for(const [label,text] of [['数据库','正常'],['数据库迁移',expectedMigration==='UP'?'正常':expectedMigration==='NOT_ENABLED'?'未启用':'迁移校验失败']]){const row=page.getByRole('row').filter({has:page.getByRole('cell',{name:label,exact:true})});await row.getByText(text,{exact:true}).waitFor();}result.migrationStatus=expectedMigration;
  result.databaseHealthVisible=true;result.migrationStatusVisible=true;result.requestCorrelation=true;step('standard platform capability opens actual database and migration health without legacy grant');
  const refreshed=response('GET','/internal/v1/diagnostics/runtime');await page.getByRole('button',{name:'刷新',exact:true}).click();assert.equal((await refreshed).status(),200);step('refresh preserves protected diagnostics and request correlation');
 }else if(phase==='audit'){
  await login('/audit');await page.getByText('操作流水',{exact:true}).last().waitFor();
  let paging=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/audit-events'&&new URL(r.url()).searchParams.get('page')==='1');await page.locator('.arco-pagination-item').filter({hasText:/^2$/}).click();assert.equal((await (await paging).json()).data.page,1);step('real audit pagination second page');paging=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/audit-events'&&new URL(r.url()).searchParams.get('page')==='0');await page.locator('.arco-pagination-item').filter({hasText:/^1$/}).click();await paging;
  const field=label=>page.locator('.arco-form-item').filter({has:page.locator('.arco-form-label-item',{hasText:label})}).locator('input');
  await field('主体').fill(process.env.SUMMER_E2E_USERNAME);await field('资源').fill('customer:'+username);await page.getByRole('button',{name:'查询',exact:true}).click();await page.getByRole('row').filter({hasText:'CUSTOMER_DELETED'}).first().waitFor();step('audit principal and resource filtering');
  await page.getByRole('row').filter({hasText:'CUSTOMER_DELETED'}).first().getByRole('button',{name:'查看',exact:true}).click();const drawer=page.locator('.arco-drawer');await drawer.getByText('tenantId: ent-acme',{exact:true}).waitFor();await drawer.getByText('organizationId: '+(process.env.SUMMER_E2E_EXPECTED_ORG || '—'),{exact:true}).waitFor();await drawer.getByText('主体: '+process.env.SUMMER_E2E_USERNAME,{exact:true}).waitFor();await drawer.getByText('安全详情: {}',{exact:true}).waitFor();step('real audit detail principal tenant and redacted payload');await page.screenshot({path:new URL('audit-detail.png',out).pathname,fullPage:true});const requestId=(await drawer.getByText(/^请求 ID: /).innerText()).replace('请求 ID: ','');await drawer.locator('.arco-drawer-close-icon').click();await field('动作').fill('CUSTOMER_DELETED');await field('请求 ID').fill(requestId);let filtered=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/audit-events'&&new URL(r.url()).searchParams.get('requestId')===requestId);await page.getByRole('button',{name:'查询',exact:true}).click();const records=(await (await filtered).json()).data.items;assert.equal(records.length,1);assert.equal(records[0].action,'CUSTOMER_DELETED');step('real audit action and requestId filtering');
 }else if(phase==='redis'){
  await login('/diagnostics/redis');await page.getByText('Redis connection is healthy.',{exact:true}).waitFor();await page.getByText('正常',{exact:true}).waitFor();step('real Redis status and safe metrics');
 }
 assert.equal(result.pageErrors,0);await page.screenshot({path:new URL(phase+'.png',out).pathname,fullPage:true});result.passed=true;
}catch(error){result.passed=false;result.failureType=error.name;result.lastPath=new URL(page.url()).pathname;if(result.steps.length){result.failureMessage=error.message.split('Call log:')[0].slice(0,600);await page.screenshot({path:new URL(phase+'-failed.png',out).pathname,fullPage:true});}throw error;
}finally{await writeFile(new URL('browser-'+phase+'.json',out),JSON.stringify(result,null,2));await browser.close();}
