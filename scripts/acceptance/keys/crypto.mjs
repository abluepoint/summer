import {createHash,createDecipheriv,createPrivateKey,createPublicKey,verify} from 'node:crypto';
let input='';for await(const chunk of process.stdin)input+=chunk;
const data=JSON.parse(input),result={};
try {
 if(data.token){
  const [header,payload,signature]=data.token.split('.');
  const h=JSON.parse(Buffer.from(header,'base64url'));
  const key=data.jwks.keys.find(k=>k.kid===h.kid);
  result.signatureValid=h.alg==='RS256'&&Boolean(key)&&verify('RSA-SHA256',Buffer.from(header+'.'+payload),createPublicKey({key,format:'jwk'}),Buffer.from(signature,'base64url'));
 }
 if(data.ciphertext){
  const [iv,ciphertext]=data.ciphertext.split('.').map(x=>Buffer.from(x,'base64'));
  const decipher=createDecipheriv('aes-128-gcm',createHash('sha256').update(data.master).digest().subarray(0,16),iv);
  decipher.setAuthTag(ciphertext.subarray(-16));
  const plain=Buffer.concat([decipher.update(ciphertext.subarray(0,-16)),decipher.final()]);
  const key=createPrivateKey({key:plain,format:'der',type:'pkcs8'});
  const pub=createPublicKey(key).export({format:'jwk'});
  result.encryptedPrivateKeyMatchesPublicJwk=data.jwks.keys.some(k=>k.n===pub.n&&k.e===pub.e);
  result.ciphertextDiffersFromPlaintext=data.ciphertext!==plain.toString('base64')&&!data.ciphertext.includes(data.master);
 }
}catch(error){result.cryptoFailure=error.name;}
process.stdout.write(JSON.stringify(result));
