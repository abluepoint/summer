import {resolve} from 'node:path';

export async function runProtocol(page,{base,out,check,setStage,setSafeScreenshot}) {
 const loginForm=()=>page.locator('input[autocomplete="current-password"]');
 async function login() {
  setSafeScreenshot(false);
  await page.locator('input[autocomplete="username"]').fill(process.env.SUMMER_E2E_USERNAME);
  await loginForm().fill(process.env.SUMMER_E2E_PASSWORD);
  const loaded=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/users'&&r.status()===200);
  loaded.catch(()=>{});
  await page.getByRole('button',{name:'登录',exact:true}).click();
  await page.waitForURL(base+'/auth-users');
  await (await loaded).finished();
  await page.getByRole('cell',{name:process.env.SUMMER_E2E_USERNAME,exact:true}).waitFor();
  setSafeScreenshot(true);
 }
 setStage('ui_logout');
 await page.goto(base+'/auth-users');
 await page.getByRole('cell',{name:process.env.SUMMER_E2E_USERNAME,exact:true}).waitFor();
 let endSessionObserved=false;
 const observe=request=>{if(new URL(request.url()).pathname==='/connect/logout')endSessionObserved=true;};
 page.on('request',observe);
 await page.locator('.arco-avatar').first().hover();
 await page.getByText('退出登录',{exact:true}).click();
 await page.waitForURL(url=>url.pathname==='/login'&&url.searchParams.has('logout'));
 await page.getByText('已退出登录',{exact:true}).waitFor();
 check('ui_logout_oidc_end_session',endSessionObserved);
 check('ui_logout_returns_login',await loginForm().isVisible());
 page.off('request',observe);
 await page.screenshot({path:resolve(out,'logout-empty-form.png'),fullPage:true});
 setStage('post_logout_protected_route');
 await page.goto(base+'/auth-users');
 await page.waitForURL(url=>url.pathname==='/login');
 await loginForm().waitFor();
 check('ui_logout_requires_fresh_login',true);
 await login();check('ui_login_after_logout_restores_route',true);
 setStage('natural_session_expiry');
 // No cookie deletion, Redis mutation, request interception, or mocked clock.
 await page.waitForTimeout(75000);
 await page.reload();
 await page.waitForURL(url=>url.pathname==='/login');
 await loginForm().waitFor();
 check('ui_natural_idle_expiry_requires_login',true,{configuredSessionSeconds:15,idleSeconds:75});
 await page.screenshot({path:resolve(out,'expired-session-empty-form.png'),fullPage:true});
 setStage('login_after_expiry');
 await login();check('ui_login_after_expiry_restores_route',true);
}
