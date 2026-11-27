import hashlib,json,uuid
from environment import Client

def literal(value):return "'"+str(value).replace("\\","\\\\").replace("'","''")+"'"

CAPABILITIES = '''platform.interface-resources.read acceptance.catalog.override enterprise.data-scope.read enterprise.data-scope.write enterprise.tasks.read enterprise.tasks.write enterprise.messages.read enterprise.messages.write enterprise.roles.read enterprise.roles.write enterprise.capabilities.read enterprise.capabilities.write enterprise.members.read enterprise.members.write platform.capabilities.read platform.capabilities.write internal.release.read internal.release.write system.parameter.read system.parameter.write system.parameter.create system.parameter.publish system.parameter.disable system.parameter.update system.parameter.rollback platform.dictionary.read platform.dictionary.write platform.dictionary.create platform.dictionary.publish platform.dictionary.disable platform.dictionary.update platform.dictionary.rollback platform.plugins.read platform.plugins.write platform.menu.write security.key.read security.key.rotate enterprise.audit.read'''.split()

class Fixtures:
    def __init__(self,env):self.env=env;self.ids=[];self.role='t14-role-'+env.run
    def grant(self,code,scope=None):
        ident='t14-cap-'+hashlib.sha256(code.encode()).hexdigest()[:24]
        existing=self.env.sql('select capability_id from summer_authorization_capability where code='+literal(code),self.env.schema)
        if existing:ident=existing
        else:
            scope=scope or ('PLATFORM' if code.startswith(('platform.','internal.','security.','system.')) else 'ENTERPRISE')
            enterprise='NULL' if scope=='PLATFORM' else "'ent-acme'"
            self.env.sql('insert into summer_authorization_capability (capability_id,enterprise_id,scope,code,resource,action,risk_level,status,version,created_at,updated_at) values ('+','.join([literal(ident),enterprise,literal(scope),literal(code),literal(code),"'fixture'","'LOW'","'ACTIVE'","1","CURRENT_TIMESTAMP","CURRENT_TIMESTAMP"])+')',self.env.schema)
        if ident not in self.ids:self.ids.append(ident)
        return ident
    def route(self,method,path):
        self.grant(method+':'+path)
        self.env.sql('update summer_authorization_role set capability_ids='+literal('\n'.join(self.ids))+',version=version+1 where role_id='+literal(self.role),self.env.schema)
    def seed(self):
        routes = [('GET','/api/users'),('POST','/api/users'),('PATCH','/api/users/{username}'),('GET','/api/audit-events')]
        root='/api/v1/enterprises/{enterpriseId}/tasks'
        routes += [('GET',root),('POST',root),('GET',root+'/types'),('PATCH',root+'/{taskId}'),('GET',root+'/{taskId}/executions'),('GET',root+'/{taskId}/events')]
        routes += [('POST',root+'/{taskId}:'+action) for action in ['pause','resume','trigger']]
        for code in CAPABILITIES:self.grant(code)
        for method,path in routes:self.grant(method+':'+path)
        self.env.sql('insert into summer_authorization_role (role_id,enterprise_id,name,code,built_in,status,capability_ids,capability_codes,version,created_at,updated_at) values ('+','.join([literal(self.role),"'ent-acme'","'Closeout isolated fixture'",literal(self.role),'false',"'ACTIVE'",literal('\n'.join(self.ids)),"''",'1','CURRENT_TIMESTAMP','CURRENT_TIMESTAMP'])+')',self.env.schema)
        self.bind(self.env.username,self.role)
        self.admin=Client().login(23081,self.env.username,self.env.password)
        status,_,_=self.admin.request(23081,'/api/users','POST',{'username':self.env.denied_username,'password':self.env.denied_password,'role':'USER'})
        assert status==200,'create_user_status_'+str(status)
        self.bind(self.env.denied_username,'')
        self.denied=Client().login(23081,self.env.denied_username,self.env.denied_password)
        return self
    def bind(self,user,role):
        self.env.sql('insert into summer_authorization_user_binding (binding_id,enterprise_id,username,role_ids,denied_capability_codes,version,created_at,updated_at) values ('+','.join([literal(str(uuid.uuid4())),"'ent-acme'",literal(user),literal(json.dumps([role] if role else [])),literal('[]'),'1','CURRENT_TIMESTAMP','CURRENT_TIMESTAMP'])+')',self.env.schema)
