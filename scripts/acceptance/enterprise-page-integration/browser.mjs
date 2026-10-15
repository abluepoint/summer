// Real Chrome, real HTTP and PKCE; no request routing or synthetic responses.
import { createRequire } from 'node:module';
import { writeFile, access } from 'node:fs/promises';
import { resolve } from 'node:path';
const require=createRequire(process.cwd()+'/package.json');
const { chromium }=require('playwright-core');
const out=process.env.SUMMER_T34_EVIDENCE_DIR, tenant=process.env.SUMMER_EP_SECOND_ENTERPRISE;
const base='http://127.0.0.1:20573', tenantPath='/api/v1/enterprises/'+tenant;
const supplement=process.env.SUMMER_EP_SUPPLEMENT==='1';
const finalOnly=process.env.SUMMER_EP_FINAL_ONLY==='1';
const result={supplement,finalOnly,eventsDeniedEnterprise:'ent-acme',checks:[],responses:[],networkMocking:false,pageErrors:0,complete:false};
let browser,page;
const check=(name,passed)=>result.checks.push({name,passed:Boolean(passed)});
const waitResponse=(path,method='GET')=>{const promise=page.waitForResponse(r=>new URL(r.url()).pathname===path&&r.request().method()===method);promise.catch(()=>{});return promise;};
const choose=async(label,id)=>{await page.locator(`[aria-label="${label}"]`).click();await page.getByRole('option',{name:id,exact:true}).click();};
const selected=async(label,id)=>(await page.locator(`[aria-label="${label}"]`).innerText()).includes(id);
const screenshot=async(name)=>page.screenshot({path:resolve(out,name),fullPage:true,animations:'disabled'});
async function command(name){await writeFile(resolve(out,name+'.request'),'request\n');for(let n=0;n<200;n++){try{await access(resolve(out,name+'.done'));return;}catch{}await new Promise(r=>setTimeout(r,100));}throw new Error('command_timeout');}
try {
 browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
 page=await browser.newPage({viewport:{width:1440,height:1100}});page.setDefaultTimeout(25000);
 page.on('pageerror',()=>result.pageErrors++);
 page.on('response',r=>{const u=new URL(r.url());if(u.pathname.startsWith('/api/'))result.responses.push({path:u.pathname,method:r.request().method(),status:r.status(),enterpriseId:u.searchParams.get('enterpriseId')});});
 await page.goto(base+'/roles');await page.locator('input[autocomplete="username"]').fill(process.env.SUMMER_E2E_USERNAME);await page.locator('input[autocomplete="current-password"]').fill(process.env.SUMMER_E2E_PASSWORD);await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(base+'/roles');
 await page.locator('[aria-label="活动租户"]').waitFor();
 let pending=waitResponse(tenantPath+'/roles');await choose('活动租户',tenant);let reply=await pending;
 await page.getByPlaceholder('角色名称',{exact:true}).waitFor();
 check('global_and_roles_selection_B_read_B',reply.status()===200&&await selected('全局活动租户',tenant)&&await selected('活动租户',tenant));
 if(!finalOnly){
 if(!supplement){
 await page.getByPlaceholder('角色名称',{exact:true}).fill('Page integration Chrome role');await page.getByPlaceholder('角色编码',{exact:true}).fill('ep-browser-role');
 // Locate the live permission through the paginated catalog.
 const row=page.locator('.arco-table').first().getByRole('row').filter({has:page.getByText('GET:/api/customers',{exact:true})});for(let i=0;i<60&&await row.count()===0;i++)await page.locator('.arco-pagination-item-next:not(.arco-pagination-item-disabled)').click();await row.locator('label.arco-checkbox').click();
 await choose('全局活动租户','ent-acme');await page.getByText('切换租户会放弃未保存修改，是否继续？',{exact:true}).waitFor();await page.getByRole('button',{name:'继续编辑',exact:true}).click();
 check('dirty_switch_cancel_preserves_B_form',await selected('全局活动租户',tenant)&&await page.getByPlaceholder('角色编码',{exact:true}).inputValue()==='ep-browser-role');
 pending=waitResponse(tenantPath+'/roles','POST');await page.getByRole('button',{name:'创建角色',exact:true}).click();reply=await pending;const created=(await reply.json()).data;
 check('Chrome_create_request_and_response_B',reply.status()===201&&created.enterpriseId===tenant);result.createdRoleId=created.roleId;
 await page.getByPlaceholder('角色名称',{exact:true}).waitFor();
 pending=waitResponse(tenantPath+'/users/'+process.env.SUMMER_E2E_MEMBER+'/roles');await page.getByPlaceholder('用户名',{exact:true}).fill(process.env.SUMMER_E2E_MEMBER);await pending;
 await page.locator('.arco-card .arco-select-view').click();await page.getByRole('option',{name:'Page integration Chrome role (ep-browser-role)',exact:true}).click();
 const cdp=await page.context().newCDPSession(page);await cdp.send('Network.enable');await cdp.send('Network.emulateNetworkConditions',{offline:false,latency:300,downloadThroughput:-1,uploadThroughput:-1});
 pending=waitResponse(tenantPath+'/users/'+process.env.SUMMER_E2E_MEMBER+'/roles','PUT');await page.getByRole('button',{name:'保存分配',exact:true}).click();
 check('role_save_blocks_global_switch_while_real_request_pending',/arco-select-disabled/.test(await page.locator('[aria-label="全局活动租户"]').getAttribute('class')));
 reply=await pending;check('Chrome_binding_request_and_response_B',reply.status()===200&&(await reply.json()).data.roleIds.includes(created.roleId));await page.getByRole('button',{name:'保存分配',exact:true}).waitFor();
 await screenshot('roles-B-saved.png');
 } else {
 const row=page.locator('.arco-table').first().getByRole('row').filter({has:page.getByText('GET:/api/customers',{exact:true})});for(let i=0;i<60&&await row.count()===0;i++)await page.locator('.arco-pagination-item-next:not(.arco-pagination-item-disabled)').click();
 pending=waitResponse(tenantPath+'/users/'+process.env.SUMMER_E2E_MEMBER+'/roles');await page.getByPlaceholder('用户名',{exact:true}).fill(process.env.SUMMER_E2E_MEMBER);reply=await pending;check('existing_B_binding_loaded_before_denial',reply.status()===200);
 const cdp=await page.context().newCDPSession(page);await cdp.send('Network.enable');await cdp.send('Network.emulateNetworkConditions',{offline:false,latency:300,downloadThroughput:-1,uploadThroughput:-1});await screenshot('roles-B-loaded.png');
 }
 await command('roles-deny');pending=waitResponse(tenantPath+'/roles');await page.getByRole('button',{name:'刷新',exact:true}).click();reply=await pending;const roleError=await reply.json();await page.getByText(roleError.requestId,{exact:false}).waitFor();
 check('role_403_clears_catalog_assignment_and_shows_requestId',reply.status()===403&&await page.locator('.arco-table label.arco-checkbox').count()===0&&await page.getByPlaceholder('用户名',{exact:true}).inputValue()===''&&await page.getByRole('button',{name:'保存分配',exact:true}).isDisabled());await screenshot('roles-B-denied.png');
 await command('roles-restore');pending=waitResponse(tenantPath+'/roles');await page.getByRole('button',{name:'刷新',exact:true}).click();await pending;
 }
 pending=waitResponse('/api/authorization-events');await page.locator('.arco-menu').getByText('权限变更',{exact:true}).click();reply=await pending;await page.waitForURL(base+'/authorization-events');const events=(await reply.json()).data.items;
 check('cross_page_keeps_B_and_lists_only_B_events',reply.status()===200&&new URL(reply.url()).searchParams.get('enterpriseId')===tenant&&events.length>0&&events.every(x=>x.enterpriseId===tenant)&&await selected('全局活动租户',tenant)&&await selected('活动租户',tenant));
 if(!finalOnly){
 const retryButton=page.getByRole('button',{name:'重试',exact:true}).first();await retryButton.waitFor();
 pending=page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/retry'));pending.catch(()=>{});await retryButton.click();
 check('event_retry_blocks_global_switch_while_pending',/arco-select-disabled/.test(await page.locator('[aria-label="全局活动租户"]').getAttribute('class')));
 reply=await pending;check('event_retry_explicitly_targets_B',reply.status()===200&&new URL(reply.url()).searchParams.get('enterpriseId')===tenant&&(await reply.json()).data.enterpriseId===tenant);
 await page.getByRole('button',{name:'重试',exact:true}).first().waitFor();await screenshot('events-B-retried.png');
 }
 await command('events-deny');pending=waitResponse('/api/authorization-events');await page.getByRole('button',{name:'刷新',exact:true}).click();reply=await pending;const eventError=await reply.json();await page.getByText(eventError.requestId,{exact:false}).waitFor();
 check('event_A_gate_403_clears_B_rows_and_shows_requestId',reply.status()===403&&await page.getByRole('button',{name:'重试',exact:true}).count()===0);await screenshot('events-B-denied.png');
 await command('events-restore');
 pending=waitResponse('/api/authorization-events');await choose('全局活动租户','ent-acme');reply=await pending;
 check('global_switch_A_updates_event_scope_and_clears_B_error',reply.status()===200&&new URL(reply.url()).searchParams.get('enterpriseId')==='ent-acme'&&await selected('活动租户','ent-acme')&&await page.getByText(eventError.requestId,{exact:false}).count()===0);
 pending=waitResponse('/api/v1/enterprises/ent-acme/roles');await page.locator('.arco-menu').getByText('角色与权限',{exact:true}).click();reply=await pending;
 check('cross_page_return_roles_uses_latest_A',reply.status()===200&&await selected('全局活动租户','ent-acme')&&await selected('活动租户','ent-acme'));
 check('selection_does_not_write_server_organization_context',!result.responses.some(r=>r.method==='PUT'&&r.path.endsWith('/context')));
 check('no_unhandled_page_errors',result.pageErrors===0);result.complete=true;
} catch(error){result.failureType=error.name;result.localFrames=error.stack?.match(/browser\.mjs:\d+:\d+/g)||[];if(page){result.failurePath=new URL(page.url()).pathname;if(result.failurePath!=='/oauth2/callback'&&!await page.locator('input[autocomplete="current-password"]').count())await screenshot('incomplete.png').catch(()=>{});}}
finally {await writeFile(resolve(out,'browser-before-close.json'),JSON.stringify(result,null,2)+'\n');await browser?.close();result.closed=true;result.counts={tests:result.checks.length,failures:result.checks.filter(c=>!c.passed).length,errors:Number(Boolean(result.failureType)),skipped:0};await writeFile(resolve(out,'browser-result.json'),JSON.stringify(result,null,2)+'\n');}
process.exitCode=result.complete&&!result.counts.failures&&!result.counts.errors?0:1;
