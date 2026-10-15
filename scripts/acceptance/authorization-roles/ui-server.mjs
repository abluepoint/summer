import http from 'node:http';
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
const req=createRequire(process.cwd()+'/package.json');
const {createServer}=await import(pathToFileURL(req.resolve('vite')));
const proxy=http.createServer((request,response)=>{
 const upstream=http.request({hostname:'127.0.0.1',port:23081,path:request.url,method:request.method,headers:{...request.headers,host:'127.0.0.1:23573','x-forwarded-host':'127.0.0.1:23573','x-forwarded-proto':'http','x-forwarded-port':'23573'}},incoming=>{response.writeHead(incoming.statusCode,incoming.headers);incoming.pipe(response);});
 upstream.on('error',()=>{response.writeHead(502);response.end();});request.pipe(upstream);
});
await new Promise(resolve=>proxy.listen(23580,'127.0.0.1',resolve));
process.env.SUMMER_BACKEND_ORIGIN='http://127.0.0.1:23580';process.env.SUMMER_UI_PORT='23573';process.env.VITE_ENABLE_MOCK='false';
const vite=await createServer({logLevel:'silent',cacheDir:process.env.AUTHORIZATION_ROLES_VITE_CACHE,server:{strictPort:true,hmr:false,watch:{ignored:['**/*']}}});await vite.listen();console.log('UI_READY');
let stopping=false;
async function close(){if(stopping)return;stopping=true;await vite.close();proxy.closeAllConnections();await new Promise(resolve=>proxy.close(resolve));process.exit(0);}
process.on('SIGTERM',close);process.on('SIGINT',close);
