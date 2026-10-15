// Own the Chrome child explicitly so a stalled protocol close cannot leak it.
export async function launchBrowser(chromium) {
  const server = await chromium.launchServer({headless:true, executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', host:'127.0.0.1'});
  try { return {server, browser:await chromium.connect(server.wsEndpoint())}; }
  catch (error) { await server.kill(); throw error; }
}
async function bounded(work, ms) {
  let timer;
  try { return await Promise.race([work, new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error('browser_shutdown_timeout')),ms);})]); }
  finally { clearTimeout(timer); }
}
export async function closeBrowser(owned, timeoutMs=10000) {
  if (!owned) return {processExited:true, forced:false};
  const {browser,server}=owned, child=server.process();let forced=false;
  try {
    await bounded((async()=>{await Promise.all(browser.contexts().map(c=>c.close()));await browser.close();await server.close();})(),timeoutMs);
  } catch {
    forced=true;
    // Only this launch's child is signalled; never match or kill other browsers.
    if(child.exitCode===null && child.signalCode===null)child.kill('SIGKILL');
  }
  if(child.exitCode===null && child.signalCode===null)await bounded(new Promise(resolve=>child.once('exit',resolve)),5000);
  if(child.exitCode===null && child.signalCode===null)throw new Error('owned_chrome_still_running');
  return {processExited:true, forced};
}
