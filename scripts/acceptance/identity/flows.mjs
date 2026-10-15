import {resolve} from 'node:path';

// Real rendered controls and real responses only. Passwords stay in process memory.
export async function runFlows(page,{base,out,check,setStage,setSafeScreenshot}) {
 const user='ui-'+process.env.SUMMER_E2E_RUN;
 const password=process.env.SUMMER_E2E_NEW_PASSWORD;
 const orgName='Live UI '+process.env.SUMMER_E2E_RUN;
 const drawer=page.locator('.arco-drawer');
 const response=(path,method)=>{const pending=page.waitForResponse(r=>new URL(r.url()).pathname===path&&r.request().method()===method);pending.catch(()=>{});return pending;};
 const body=async r=>{const value=await r.json();return value.data??value;};
 const btn=name=>page.getByRole('button',{name,exact:true});
 const goUsers=async()=>{const ready=response('/api/users','GET');await page.goto(base+'/auth-users');await (await ready).finished();await page.getByRole('cell',{name:process.env.SUMMER_E2E_USERNAME,exact:true}).waitFor();};
 const goOrg=async()=>{const ready=response('/api/v1/enterprises/ent-acme/org-tree','GET');await page.goto(base+'/organization');const tree=await body(await ready);const first=tree.items[0];await page.getByText(`${first.name} (${first.memberCount})`,{exact:true}).waitFor();return tree;};
 setStage('ui_user_create');await goUsers();await btn('新建用户').click();
 await drawer.getByRole('button',{name:'创建',exact:true}).click();
 await drawer.getByText('请输入不超过 128 个字符的用户名',{exact:true}).waitFor();check('ui_user_invalid_input_visible',true);
 await page.getByLabel('用户名',{exact:true}).fill(user);setSafeScreenshot(false);
 await page.getByLabel('初始密码',{exact:true}).fill(password);
 let pending=response('/api/users','POST');await drawer.getByRole('button',{name:'创建',exact:true}).click();let r=await pending;
 check('ui_user_created_real_http',r.status()===200,{status:r.status()});await drawer.waitFor({state:'hidden'});setSafeScreenshot(true);
 const row=page.getByRole('row').filter({hasText:user});await row.getByRole('button',{name:'停用',exact:true}).waitFor();
 setStage('ui_user_duplicate');await btn('新建用户').click();await page.getByLabel('用户名',{exact:true}).fill(user);setSafeScreenshot(false);await page.getByLabel('初始密码',{exact:true}).fill(password);
 pending=response('/api/users','POST');await drawer.getByRole('button',{name:'创建',exact:true}).click();r=await pending;
 check('ui_duplicate_user_controlled_conflict',r.status()===409,{status:r.status()});await drawer.getByText(/用户已存在或数据发生冲突/).waitFor();
 check('ui_duplicate_preserves_username',await page.getByLabel('用户名',{exact:true}).inputValue()===user);
 await page.getByLabel('初始密码',{exact:true}).fill('');setSafeScreenshot(true);
 await page.screenshot({path:resolve(out,'user-conflict-safe.png'),fullPage:true});
 await drawer.getByRole('button',{name:'取消',exact:true}).click();await btn('放弃修改').click();await drawer.waitFor({state:'hidden'});
 setStage('ui_user_disable_restore');
 pending=response('/api/users/'+user,'PATCH');await row.getByRole('button',{name:'停用',exact:true}).click();r=await pending;
 await row.getByRole('button',{name:'恢复',exact:true}).waitFor();check('ui_user_disabled_real_http',r.status()===200,{status:r.status()});
 pending=response('/api/users/'+user,'PATCH');await row.getByRole('button',{name:'恢复',exact:true}).click();r=await pending;
 await row.getByRole('button',{name:'停用',exact:true}).waitFor();check('ui_user_restored_real_http',r.status()===200,{status:r.status()});
 await page.screenshot({path:resolve(out,'users-crud.png'),fullPage:true});

 setStage('ui_organization_create');await goOrg();await btn('新建组织').click();
 await page.getByLabel('组织名称',{exact:true}).fill(orgName);await page.getByLabel('组织编码',{exact:true}).fill('ui-'+process.env.SUMMER_E2E_RUN);
 const orgBase='/api/v1/enterprises/ent-acme';pending=response(orgBase+'/org-units','POST');await drawer.getByRole('button',{name:'保存组织',exact:true}).click();r=await pending;
 check('ui_organization_created_real_http',r.status()===201,{status:r.status()});const org=await body(r);await drawer.waitFor({state:'hidden'});
 await page.getByText(`${orgName} (0)`,{exact:true}).waitFor();pending=response(orgBase+'/org-units/'+org.id+'/members','GET');await page.getByText(`${orgName} (0)`,{exact:true}).click();await (await pending).finished();
 setStage('ui_organization_edit');await btn('编辑组织').click();await page.getByLabel('组织说明',{exact:true}).fill('Edited through the live browser');
 pending=response(orgBase+'/org-units/'+org.id,'PATCH');await drawer.getByRole('button',{name:'保存组织',exact:true}).click();r=await pending;
 check('ui_organization_edited_real_http',r.status()===200,{status:r.status()});await drawer.waitFor({state:'hidden'});

 setStage('ui_organization_assignments');
 const adminRow=page.getByRole('row').filter({hasText:process.env.SUMMER_E2E_USERNAME});await adminRow.getByRole('button',{name:'分配组织',exact:true}).click();
 await page.getByRole('combobox',{name:'分配组织',exact:true}).click();await page.getByRole('option',{name:orgName,exact:true}).click();await page.getByRole('option',{name:'Engineering',exact:true}).click();await page.getByRole('combobox',{name:'分配组织',exact:true}).press('Escape');
 await drawer.getByRole('button',{name:'保存关系',exact:true}).click();await drawer.getByText('请选择成员，并将一个已选组织设为主组织',{exact:true}).waitFor();check('ui_missing_primary_organization_validation',true);
 await page.getByRole('combobox',{name:'主组织',exact:true}).click();await page.getByRole('option',{name:orgName,exact:true,selected:false}).click();
 pending=response(orgBase+'/org-assignments:batch','PUT');await drawer.getByRole('button',{name:'保存关系',exact:true}).click();r=await pending;const assigned=await body(r);
 check('ui_multiple_organization_assignments_saved',r.status()===200&&assigned.organizationIds.length===2&&assigned.primaryOrganizationId===org.id,{status:r.status()});
 await drawer.waitFor({state:'hidden'});await page.getByText(`${orgName} (1)`,{exact:true}).waitFor();

 setStage('ui_current_context');pending=response(orgBase+'/context','PUT');await btn('设为当前组织').click();r=await pending;const context=await body(r);
 check('ui_current_context_saved',r.status()===200&&context.organizationId===org.id,{status:r.status()});await page.getByText('本页已确认上下文：'+orgName,{exact:true}).waitFor();
 await page.screenshot({path:resolve(out,'organization-context.png'),fullPage:true});
 await goOrg();
 // Allow a context GET (when implemented) to finish after the tree; the visible label is the user-facing assertion.
 try { await page.getByText('本页已确认上下文：'+orgName,{exact:true}).waitFor({timeout:5000}); } catch {}
 check('ui_context_restored_after_reload',await page.getByText('本页已确认上下文：'+orgName,{exact:true}).count()===1,
  {emptyContextMessageVisible:await page.getByText('尚未在本页设置组织上下文',{exact:true}).count()===1});
 await page.screenshot({path:resolve(out,'context-reload.png'),fullPage:true});
 pending=response(orgBase+'/context','PUT');await btn('清除当前组织').click();r=await pending;const cleared=await body(r);check('ui_current_context_cleared',r.status()===200&&cleared.organizationId===null,{status:r.status()});

 setStage('ui_member_status');const memberRow=page.getByRole('row').filter({hasText:'member-alice'});
 pending=response(orgBase+'/members/member-alice:disable','POST');await memberRow.getByRole('button',{name:'停用成员',exact:true}).click();r=await pending;await memberRow.getByRole('button',{name:'恢复成员',exact:true}).waitFor();check('ui_member_disabled_real_http',r.status()===200,{status:r.status()});
 pending=response(orgBase+'/members/member-alice:enable','POST');await memberRow.getByRole('button',{name:'恢复成员',exact:true}).click();r=await pending;await memberRow.getByRole('button',{name:'停用成员',exact:true}).waitFor();check('ui_member_restored_real_http',r.status()===200,{status:r.status()});
 await page.screenshot({path:resolve(out,'organization-management.png'),fullPage:true});
 setStage('ui_ungranted_identity');
 const restrictedContext=await page.context().browser().newContext({viewport:{width:1440,height:1000}});
 const restricted=await restrictedContext.newPage();restricted.setDefaultTimeout(20000);let errors=0;restricted.on('pageerror',()=>errors++);
 try {
  await restricted.goto(base+'/auth-users');await restricted.locator('input[autocomplete="username"]').fill(user);
  setSafeScreenshot(false);await restricted.locator('input[autocomplete="current-password"]').fill(password);
  await restricted.getByRole('button',{name:'登录',exact:true}).click();await restricted.waitForURL(base+'/auth-users');setSafeScreenshot(true);
  await restricted.getByText('对不起，您没有访问该资源的权限',{exact:true}).waitFor();
  check('ui_ungranted_user_management_denied',await restricted.getByRole('button',{name:'新建用户',exact:true}).count()===0);
  await restricted.goto(base+'/organization');await restricted.getByText('对不起，您没有访问该资源的权限',{exact:true}).waitFor();
  check('ui_ungranted_organization_management_denied',await restricted.getByRole('button',{name:'新建组织',exact:true}).count()===0);
  check('ui_ungranted_page_has_no_javascript_errors',errors===0);
  await restricted.screenshot({path:resolve(out,'ungranted-access.png'),fullPage:true});
 } finally { await restrictedContext.close();setSafeScreenshot(true); }
}
