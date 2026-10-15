import {generateKeyPairSync,createPublicKey,verify} from 'node:crypto';
let raw='';for await(const chunk of process.stdin)raw+=chunk;
const input=JSON.parse(raw);
if(input.operation==='generate'){
 const {privateKey}=generateKeyPairSync('rsa',{modulusLength:2048});
 // stdout is captured only by the Python owner; never written to evidence or the terminal.
 process.stdout.write(JSON.stringify({pem:privateKey.export({type:'pkcs8',format:'pem'})}));
}else{
 const [header,payload,signature]=input.token.split('.');
 const kid=JSON.parse(Buffer.from(header,'base64url')).kid;
 const jwk=input.jwks.keys.find(k=>k.kid===kid);
 process.stdout.write(JSON.stringify({verified:Boolean(jwk)&&verify('RSA-SHA256',Buffer.from(header+'.'+payload),createPublicKey({key:jwk,format:'jwk'}),Buffer.from(signature,'base64url'))}));
}
