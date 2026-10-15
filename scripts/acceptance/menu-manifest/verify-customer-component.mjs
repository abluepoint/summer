// Explicit component fixture; not real-server authorization acceptance.
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {createServer} from 'node:http';
import {readFile,writeFile} from 'node:fs/promises';
import {launchBrowser,closeBrowser} from './browser-lifecycle.mjs';
const require=createRequire(process.cwd()+'/package.json');const {build}=require('esbuild'),{chromium}=require('playwright-core');
const root=process.cwd();const result={mode:'component-http-adapter',tests:0,failures:0,errors:0,skipped:0,checks:[]};
const entry=`import React,{useState} from 'react';import ReactDOM from 'react-dom';import axios from 'axios';import {MemoryRouter} from 'react-router-dom';
import CustomerScreen from './src/pages/customer/CustomerScreen';import {GlobalContext} from './src/context';import {MenuAccessContext} from './src/menu/access';
axios.defaults.adapter=async config=>({config,status:200,headers:{},data:{success:true,data:{items:[{username:'fixture-user',displayName:'Fixture',email:'fixture@example.test',active:true}],page:0,size:20,totalElements:1,totalPages:1,projection:'detail'},requestId:'component-only'}});
function App(){const [actions,setActions]=useState(['read']);window.setActions=setActions;return <GlobalContext.Provider value={{lang:'zh-CN'}}><MemoryRouter initialEntries={['/customer/drawer']}><MenuAccessContext.Provider value={[{menuId:'customer/drawer',enabled:true,actions}]}><CustomerScreen mode="drawer"/></MenuAccessContext.Provider></MemoryRouter></GlobalContext.Provider>}
ReactDOM.render(<App/>,document.getElementById('root'));`;
const plugins=[{name:'component-auth-fixture',setup(b){b.onResolve({filter:/auth\/oidc$/},()=>({path:'auth-fixture',namespace:'fixture'}));b.onLoad({filter:/.*/,namespace:'fixture'},()=>({contents:`export const DRAFT_KEY='component-draft';export const getAuthenticatedUser=async()=>({scopes:['customer.read','customer.write']});export const isAuthRedirecting=()=>false;export const readCustomerDraft=()=>null;export const registerCustomerDraftProvider=()=>()=>{};`,loader:'js'}));}}];
if(process.env.MENU_TEST_ORIGINAL)plugins.push({name:'original-source-only',setup(b){b.onLoad({filter:/CustomerScreen\.tsx$/},async()=>({contents:await readFile(process.env.MENU_TEST_ORIGINAL,'utf8'),loader:'tsx',resolveDir:root+'/src/pages/customer'}));}});
const compiled=await build({stdin:{contents:entry,resolveDir:root,loader:'tsx'},tsconfig:root+'/tsconfig.json',bundle:true,write:false,format:'iife',target:'es2020',define:{'process.env.NODE_ENV':'"test"'},plugins});
const server=createServer((req,res)=>{res.setHeader('Content-Type',req.url==='/bundle.js'?'text/javascript':'text/html');res.end(req.url==='/bundle.js'?compiled.outputFiles[0].text:'<div id="root"></div><script src="/bundle.js"></script>');});await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));let owned;
try{owned=await launchBrowser(chromium);const page=await owned.browser.newPage();await page.goto('http://127.0.0.1:'+server.address().port);await page.getByRole('table').waitFor();
for(const [name,actions,expected] of [['read_only_hides_all_mutations_despite_write_scope',['read'],[0,0,0]],['create_permission_only_shows_create',['read','create'],[1,0,0]],['delete_permission_only_shows_delete',['read','delete'],[0,0,1]],['update_permission_only_shows_edit',['read','update'],[0,1,0]]]){
 try{await page.evaluate(a=>window.setActions(a),actions);await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));const actual=[];for(const label of ['新增客户','编辑','删除'])actual.push(await page.getByRole('button',{name:label,exact:true}).count());assert.deepEqual(actual,expected);result.checks.push({name,status:'passed',actual});}
 catch(e){result.failures++;result.checks.push({name,status:'failed',detail:e.message});}result.tests++;}
} catch(e){result.errors++;result.error=e.message;}finally{result.shutdown=await closeBrowser(owned);await new Promise(resolve=>server.close(resolve));await writeFile(process.env.MENU_COMPONENT_RESULT,JSON.stringify(result,null,2)+'\n');}
console.log(JSON.stringify(result));process.exit(result.failures||result.errors?1:0);
