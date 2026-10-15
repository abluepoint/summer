import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {writeFile} from 'node:fs/promises';
import {launchBrowser,closeBrowser} from './browser-lifecycle.mjs';
const require=createRequire(process.cwd()+'/package.json');const {chromium}=require('playwright-core');
const result={mode:'real-chrome-lifecycle-only',tests:0,failures:0,errors:0,skipped:0,businessChecks:0,checks:[]};
for(const stalled of [false,true]){
 let owned;try{owned=await launchBrowser(chromium);for(let i=0;i<2;i++){const c=await owned.browser.newContext();const p=await c.newPage();await p.goto('about:blank');}
 if(stalled)owned.server.process().kill('SIGSTOP');
 const start=Date.now();const closed=await closeBrowser(owned,stalled?1000:10000);assert.equal(closed.processExited,true);assert.equal(closed.forced,stalled);assert.ok(Date.now()-start<16000);
 result.checks.push({name:stalled?'stalled_owned_chrome_forcibly_reaped':'normal_contexts_and_chrome_closed',status:'passed',...closed,elapsedMs:Date.now()-start});
 }catch(e){result.failures++;result.checks.push({name:stalled?'stalled':'normal',status:'failed',error:e.message});if(owned)await closeBrowser(owned,1000).catch(()=>{});}result.tests++;}
await writeFile(process.env.MENU_LIFECYCLE_RESULT,JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));process.exit(result.failures?1:0);
