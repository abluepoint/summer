import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';
const source=fs.readFileSync(new URL('../src/pages/diagnostics/redis/api.ts',import.meta.url),'utf8');
const code=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText;
let accessToken='fixture-first',call;const exported={};
const dependencies={
 axios:{default:{get:async(url,options)=>{call={url,options};return {status:200,data:{success:true,data:{status:'UP'}}};},isAxiosError:()=>false}},
 '../../customer/contract.mjs':{readEnvelope:value=>value.data,readFailure:()=>({})},
 '@/auth/oidc':{getAccessTokenFromMemory:async()=>accessToken},
};
vm.runInNewContext(code,{exports:exported,require:name=>{assert.ok(name in dependencies,`unexpected dependency ${name}`);return dependencies[name];}});
assert.equal((await exported.getRedisDiagnostics()).status,'UP');
assert.equal(call.options?.headers?.Authorization,'Bearer fixture-first','diagnostic requests must use current bearer credentials so Redis session failures do not hide diagnostics');
accessToken='fixture-renewed';await exported.getRedisDiagnostics();assert.equal(call.options?.headers?.Authorization,'Bearer fixture-renewed','read credentials for each request');
accessToken=null;await exported.getRedisDiagnostics();assert.equal(call.options?.headers?.Authorization,undefined,'never synthesize a bearer credential');
console.log('Redis diagnostics: current bearer credentials, renewal and absent-token contracts passed');
