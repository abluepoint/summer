"""Real authorization-code protocol; secrets/HTML are returned only in memory."""
import base64, hashlib, json, secrets, urllib.parse, urllib.request, urllib.error
from html.parser import HTMLParser
from oauth_environment import Client

ORIGIN='http://127.0.0.1:21573'
SCOPES='openid profile customer.read customer.write'
class Inputs(HTMLParser):
    def __init__(self):super().__init__();self.fields=[]
    def handle_starttag(self,tag,attrs):
        a=dict(attrs)
        if tag=='input' and a.get('name') and a.get('type') in ('hidden','checkbox'):
            self.fields.append((a['name'],a.get('value','')))

class OAuthClient(Client):
    def __init__(self,environment):super().__init__();self.environment=environment
    def sign_in(self,port,username,password):
        s,d,h=self.request(port,'/csrf',bearer=False);assert s==200,'csrf_status_'+str(s)
        self.environment.additional_secrets.append(d['token'])
        s,_,_=self.request(port,'/login','POST',urllib.parse.urlencode({'username':username,'password':password,d['parameterName']:d['token']}),bearer=False)
        assert s==302,'login_status_'+str(s)
        return self
    def raw(self,port,path,body=None):
        headers={'Host':'127.0.0.1:21573','X-Forwarded-Host':'127.0.0.1:21573','X-Forwarded-Proto':'http'}
        if body is not None:headers['Content-Type']='application/x-www-form-urlencoded'
        req=urllib.request.Request(f'http://127.0.0.1:{port}'+path,data=body.encode() if body is not None else None,headers=headers)
        try:r=self.opener.open(req,timeout=15)
        except urllib.error.HTTPError as e:r=e
        with r:return r.status,r.read().decode(errors='replace'),r.headers
    def authorize(self,port,client_id='summer-ui',redirect=None,scope=SCOPES,pkce='S256',prompt=None,accept_consent=True):
        verifier=secrets.token_urlsafe(48);state=secrets.token_urlsafe(24);nonce=secrets.token_urlsafe(24)
        self.environment.additional_secrets.extend([verifier,state,nonce])
        redirect=redirect or ORIGIN+'/oauth2/callback'
        params={'response_type':'code','client_id':client_id,'scope':scope,'redirect_uri':redirect,'state':state,'nonce':nonce}
        if pkce:
            params['code_challenge_method']=pkce
            params['code_challenge']=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=') if pkce=='S256' else verifier
        if prompt:params['prompt']=prompt
        s,html,h=self.raw(port,'/oauth2/authorize?'+urllib.parse.urlencode(params));consent=False
        if s==200 and '<form' in html and accept_consent:
            parser=Inputs();parser.feed(html)
            if any(k=='client_id' for k,v in parser.fields):
                consent=True;s,html,h=self.raw(port,'/oauth2/authorize',urllib.parse.urlencode(parser.fields))
        parsed=urllib.parse.parse_qs(urllib.parse.urlsplit(h.get('Location','')).query)
        code=parsed.get('code',[None])[0]
        if code:self.environment.additional_secrets.append(code)
        return {'status':s,'code':code,'error':parsed.get('error',[None])[0],'stateMatches':parsed.get('state',[None])[0]==state,'consentShown':consent,'verifier':verifier,'clientId':client_id,'redirect':redirect,'nonce':nonce,'headers':h}
    def exchange(self,port,authorization,verifier=None):
        values={'grant_type':'authorization_code','client_id':authorization['clientId'],'code':authorization['code'],'redirect_uri':authorization['redirect'],'code_verifier':authorization['verifier'] if verifier is None else verifier}
        s,d,h=self.request(port,'/oauth2/token','POST',urllib.parse.urlencode(values),bearer=False)
        if isinstance(d,dict):
            for key in ('access_token','id_token','refresh_token'):
                if d.get(key):self.environment.additional_secrets.append(d[key])
        return s,d,h

def claims(token,index=1):
    data=token.split('.')[index]
    return json.loads(base64.urlsafe_b64decode(data+'='*((4-len(data)%4)%4)))
