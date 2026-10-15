import http from 'node:http';
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
const require=createRequire(process.cwd()+'/package.json');
const {createServer}=await import(pathToFileURL(require.resolve('vite')));
let held=false;
const responses=new Set();
function deliver(entry){responses.delete(entry);if(!entry.response.destroyed){entry.response.writeHead(entry.incoming.statusCode,entry.incoming.headers);entry.incoming.pipe(entry.response);}else entry.incoming.destroy();}
// A real transport fault: hold actual upstream bodies, never synthesize API data.
process.on('SIGUSR2',()=>{held=!held;if(!held)for(const entry of responses)deliver(entry);});
// Browser requests go to node B; management controls independently write through A.
const proxy=http.createServer((request,response)=>{
 const upstream=http.request({hostname:'127.0.0.1',port:26082,path:request.url,method:request.method,headers:{...request.headers,host:'127.0.0.1:26573','x-forwarded-host':'127.0.0.1:26573','x-forwarded-proto':'http','x-forwarded-port':'26573'}},incoming=>{
  const entry={incoming,response};
  if(held&&request.method==='GET'&&request.url.split('?')[0].endsWith('/menu-manifest')){responses.add(entry);response.on('close',()=>{responses.delete(entry);incoming.destroy();});}
  else deliver(entry);
 });
 upstream.on('error',()=>{response.writeHead(502);response.end();});request.pipe(upstream);
});
await new Promise(resolve=>proxy.listen(26580,'127.0.0.1',resolve));
process.env.SUMMER_BACKEND_ORIGIN='http://127.0.0.1:26580';process.env.SUMMER_UI_PORT='26573';process.env.VITE_ENABLE_MOCK='false';
const vite=await createServer({logLevel:'silent',cacheDir:process.env.T20_VITE_CACHE,server:{strictPort:true,hmr:false,watch:{ignored:['**/*']}}});await vite.listen();console.log('UI_READY');
let stopping=false;
async function close(){if(stopping)return;stopping=true;await vite.close();proxy.closeAllConnections();await new Promise(resolve=>proxy.close(resolve));process.exit(0);}
process.on('SIGTERM',close);process.on('SIGINT',close);
