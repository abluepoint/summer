import {resolve} from 'node:path';import {pathToFileURL} from 'node:url';
import {createRequire} from 'node:module';import {createInterface} from 'node:readline';import {writeFile} from 'node:fs/promises';import assert from 'node:assert/strict';
const require=createRequire(process.cwd()+'/package.json'),{chromium}=require('playwright-core');const out=pathToFileURL(resolve(process.env.SUMMER_FOUNDATION_EVIDENCE_DIR || process.cwd()+'/../.git/summer-foundation-acceptance')+'/'),origin='http://127.0.0.1:20573';
const browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(30000);let errors=0;page.on('pageerror',()=>errors++);const result={networkMocking:false,cases:[]};
try{
 await page.goto(origin+'/diagnostics/redis');await page.locator('input[autocomplete="username"]').fill(process.env.SUMMER_E2E_USERNAME);await page.locator('input[autocomplete="current-password"]').fill(process.env.SUMMER_E2E_PASSWORD);await page.getByRole('button',{name:'登录',exact:true}).click();await page.getByText('Redis connection is healthy.',{exact:true}).waitFor();console.log('BROWSER_READY');
 const commands=createInterface({input:process.stdin});for await(const line of commands){
  const mode=JSON.parse(line);if(mode.stop)break;
  const [response]=await Promise.all([page.waitForResponse(r=>new URL(r.url()).pathname==='/internal/v1/diagnostics/redis'),page.getByRole('button',{name:'刷新',exact:true}).click()]);const data=await response.json();result.lastObservedHttpStatus=response.status();result.lastObservedDiagnosticStatus=data?.data?.status;assert.equal(response.status(),200);assert.equal(data.data.status,mode.status);assert.equal(data.requestId,response.headers()['x-request-id']);
  for(const secret of [process.env.SUMMER_E2E_PASSWORD,process.env.SUMMER_REDIS_SECRET])if(secret)assert.ok(!JSON.stringify(data).includes(secret));assert.ok(!JSON.stringify(data).includes('redis://'));assert.equal(data.data.endpoint,'redis');
  await page.getByText(mode.label,{exact:true}).waitFor();await page.screenshot({path:new URL('redis-'+mode.status+'.png',out).pathname,fullPage:true});result.cases.push({status:mode.status,httpStatus:response.status(),requestId:data.requestId,endpoint:data.data.endpoint,secretScanPassed:true});await writeFile(new URL('redis-browser-result.json',out),JSON.stringify(result,null,2));console.log('CASE_OK');
 }
 commands.close();process.stdin.pause();assert.equal(errors,0);result.pageErrors=errors;result.passed=true;
}catch(error){result.passed=false;result.failureType=error.name;result.lastPath=new URL(page.url()).pathname;result.failureMessage=error.message.split('Call log:')[0].slice(0,500);console.log('BROWSER_FAILED');}
finally{await writeFile(new URL('redis-browser-result.json',out),JSON.stringify(result,null,2));await browser.close();process.exit(result.passed?0:1);}
