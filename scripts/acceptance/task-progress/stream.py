"""Actual HTTP event-stream reader. Cookie credentials stay in memory only."""
import http.client,json,socket,threading,time,urllib.request,uuid

class Stream:
    def __init__(self,client,path,last=None,read=True,receive_buffer=None):
        assert '?' not in path,'credentials_must_not_be_in_url'
        req=urllib.request.Request('http://127.0.0.1:23081'+path)
        client.cookies.add_cookie_header(req)
        headers={'Host':'127.0.0.1:23573','X-Forwarded-Host':'127.0.0.1:23573','X-Forwarded-Proto':'http','Accept':'text/event-stream','X-Request-Id':'t29-'+uuid.uuid4().hex}
        cookie=req.get_header('Cookie')
        if cookie:headers['Cookie']=cookie
        if last is not None:headers['Last-Event-ID']=str(last)
        self.cookie_present=bool(cookie);self.bearer_present=False;self.path=path;self.events=[];self.comments=[];self.closed=threading.Event();self.thread=None;self.error=None
        self.connection=http.client.HTTPConnection('127.0.0.1',23081,timeout=25)
        self.connection.connect()
        if receive_buffer:self.connection.sock.setsockopt(socket.SOL_SOCKET,socket.SO_RCVBUF,receive_buffer)
        self.socket=self.connection.sock
        self.connection.request('GET',path,headers=headers);self.response=self.connection.getresponse();self.status=self.response.status
        self.content_type=self.response.getheader('Content-Type','');self.request_id=self.response.getheader('X-Request-Id');self.correlated=self.request_id==headers['X-Request-Id']
        self.error_code=None
        if self.status!=200:
            try:
                data=json.loads(self.response.read());self.error_code=(data.get('error') or {}).get('code');self.correlated=self.correlated and data.get('requestId')==self.request_id
            except (ValueError,AttributeError):pass
            self.close()
        elif read:
            self.thread=threading.Thread(target=self._read,daemon=True);self.thread.start()
    def _read(self):
        event={};data=[]
        try:
            while True:
                raw=self.response.readline()
                if not raw:break
                line=raw.decode().rstrip('\r\n')
                if line.startswith(':'):self.comments.append({'at':time.monotonic(),'comment':line[1:].strip()})
                elif not line:
                    if event or data:
                        try:payload=json.loads('\n'.join(data)) if data else {}
                        except ValueError:payload={}
                        self.events.append({**event,'at':time.monotonic(),'data':payload})
                    event={};data=[]
                elif line.startswith('event:'):event['type']=line[6:].strip()
                elif line.startswith('id:'):event['id']=int(line[3:].strip())
                elif line.startswith('data:'):data.append(line[5:].lstrip())
        except Exception as error:self.error=type(error).__name__
        finally:self.closed.set()
    def wait(self,predicate,timeout=20):
        end=time.monotonic()+timeout
        while time.monotonic()<end:
            if predicate(self):return True
            if self.closed.is_set():return predicate(self)
            time.sleep(.05)
        return predicate(self)
    def close(self):
        try:self.socket.shutdown(socket.SHUT_RDWR)
        except OSError:pass
        self.connection.close()
        if self.thread:self.thread.join(timeout=2)
        self.response.close();self.closed.set()
    def metadata(self):
        return {'status':self.status,'contentType':self.content_type,'cookiePresent':self.cookie_present,'bearerPresent':False,'urlHasQuery':False,'requestId':self.request_id,'correlated':self.correlated,'errorCode':self.error_code}
