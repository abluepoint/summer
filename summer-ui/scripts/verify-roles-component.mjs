// Component regression: real React/Arco/roles API, with an explicit in-memory HTTP adapter.
// Identity selection is explicitly fixed to ent-acme here; the separate integration fixture exercises the real shared hook.
// This supplements (and never replaces) the separate real-server Chrome acceptance.
import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { build } from 'esbuild';
import { chromium } from 'playwright-core';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const entry = `
import React from 'react'; import ReactDOM from 'react-dom'; import axios from 'axios';
import Roles from './src/pages/roles'; import {GlobalContext} from './src/context';
window.fixture = {version:7, denied:[], writes:[], reads:[], conflict:false};
axios.defaults.adapter = async config => {
 const f=window.fixture, path=config.url, method=config.method;
 const response=(data,status=200)=>({data:{success:true,data},status,headers:{},config});
 if(path.endsWith('/interface-resources'))return response({items:[{httpMethod:'GET',normalizedPath:'/api/customers',permissionCode:'GET:/api/customers',resourceType:'BUSINESS',configurable:true}]});
 if(path.includes('/users/')) {
  const username=decodeURIComponent(path.split('/users/')[1].split('/')[0]);
  if(method==='get'){f.reads.push(username);return response({username,enterpriseId:'ent-acme',roleIds:['reader'],deniedCapabilityCodes:f.denied,version:f.version});}
  const body=JSON.parse(config.data);f.writes.push({version:config.headers['If-Match'],...body});
  if(f.conflict || config.headers['If-Match']!=='"'+f.version+'"') {
   if(f.conflict){f.version++;f.conflict=false;}
   throw {isAxiosError:true,response:{status:412,data:{success:false,error:{code:'SUMMER_PRECONDITION_FAILED',message:'The resource has changed'},requestId:'component-conflict'},headers:{}}};
  }
  f.version++;f.denied=body.deniedCapabilityCodes;
  return response({username,enterpriseId:'ent-acme',...body,version:f.version});
 }
 return response([{roleId:'reader',enterpriseId:'ent-acme',name:'Reader',code:'reader',status:'ACTIVE',capabilityIds:[],version:1}]);
};
ReactDOM.render(<GlobalContext.Provider value={{lang:'zh-CN'}}><Roles/></GlobalContext.Provider>,document.getElementById('root'));
`;
const compiled = await build({stdin:{contents:entry,resolveDir:root,loader:'tsx'},bundle:true,write:false,target:'es2020',format:'iife',define:{'process.env.NODE_ENV':'"test"'},plugins:[{name:'fixture-identity',setup(b){b.onResolve({filter:/useActiveEnterprise$/},()=>({path:'fixture-identity',namespace:'fixture'}));b.onLoad({filter:/.*/,namespace:'fixture'},()=>({contents:`const noop=()=>{}; export default function(){return {enterpriseId:'ent-acme',options:[{label:'ent-acme',value:'ent-acme'}],ready:true,busy:false,setDirty:noop,setBusy:noop,change:noop};}`,loader:'js'}));}},{name:'source-alias',setup(b){b.onResolve({filter:/^@\//},args=>({path:root+'src/'+args.path.slice(2)+'.ts'}));}}]});
const server=createServer((req,res)=>{res.setHeader('Content-Type',req.url==='/bundle.js'?'text/javascript':'text/html');res.end(req.url==='/bundle.js'?compiled.outputFiles[0].text:'<div id="root"></div><script src="/bundle.js"></script>');});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
const origin='http://127.0.0.1:'+server.address().port;
try {
 await test('existing assignment loads its version and three saves advance it',async()=>{
  const page=await browser.newPage();try{
   await page.goto(origin);await page.getByPlaceholder('用户名',{exact:true}).fill('alice');
   await page.waitForFunction(()=>window.fixture.reads.includes('alice'),{},{timeout:5000});
   const save=page.getByRole('button',{name:'保存分配',exact:true});
   for(let count=1;count<=3;count++){await save.click();await page.waitForFunction(n=>window.fixture.writes.length===n,count);await page.waitForFunction(n=>window.fixture.version===n,7+count);}
   assert.deepEqual(await page.evaluate(()=>window.fixture.writes.map(w=>w.version)),['"7"','"8"','"9"']);
  }finally{await page.close();}
 });
 await test('a stale save preserves edits and refreshes version before explicit retry',async()=>{
  const page=await browser.newPage();try{
   await page.goto(origin);await page.getByPlaceholder('用户名',{exact:true}).fill('alice');
   await page.waitForFunction(()=>window.fixture.reads.includes('alice'),{},{timeout:5000});
   await page.getByRole('checkbox',{name:'GET:/api/customers',exact:true}).locator('xpath=ancestor::label[1]').click();
   await page.evaluate(()=>window.fixture.conflict=true);await page.getByRole('button',{name:'保存分配',exact:true}).click();
   await page.locator('.arco-alert-error').waitFor();await page.waitForFunction(()=>window.fixture.reads.length>=2);
   assert.equal(await page.getByRole('checkbox',{name:'GET:/api/customers',exact:true}).isChecked(),true);
   assert.equal(await page.getByPlaceholder('用户名',{exact:true}).inputValue(),'alice');
   assert.equal(await page.evaluate(()=>window.fixture.writes.length),1);
   await page.getByRole('button',{name:'保存分配',exact:true}).click();await page.waitForFunction(()=>window.fixture.version===9);
   assert.deepEqual(await page.evaluate(()=>window.fixture.writes.map(w=>w.version)),['"7"','"8"']);
   assert.deepEqual(await page.evaluate(()=>window.fixture.denied),['GET:/api/customers']);
  }finally{await page.close();}
 });
}finally{await browser.close();server.closeAllConnections();await new Promise(resolve=>server.close(resolve));}
