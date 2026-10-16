"""Configure isolated acceptance identities exclusively through formal HTTP APIs."""
from environment import Client
from support import mutation
B='/api/v1/enterprises/ent-acme'
CODES='''enterprise.roles.read enterprise.roles.write enterprise.capabilities.read enterprise.capabilities.write enterprise.members.read enterprise.members.write enterprise.data-scope.read enterprise.data-scope.write enterprise.tasks.read enterprise.tasks.write enterprise.messages.read enterprise.messages.write enterprise.authorization-events.read enterprise.authorization-events.write enterprise.audit.read enterprise.menu.read enterprise.menu.write platform.menu.write platform.capabilities.read platform.capabilities.write internal.release.read internal.release.write system.parameter.read system.parameter.write system.parameter.create system.parameter.update system.parameter.publish system.parameter.disable system.parameter.rollback platform.plugins.read platform.plugins.write security.key.read security.key.rotate'''.split()
ROUTES=['GET:/api/users','POST:/api/users','PATCH:/api/users/{username}','GET:/api/security/keys','POST:/api/security/keys:rotate','GET:/api/v1/sessions','DELETE:/api/v1/sessions/{reference}','GET:/api/v1/enterprises/{enterpriseId}/users/{username}/roles','PUT:/api/v1/enterprises/{enterpriseId}/users/{username}/roles','POST:/api/v1/enterprises/{enterpriseId}/members','GET:/api/v1/enterprises/{enterpriseId}/members','POST:/api/v1/enterprises/{enterpriseId}/members/{memberId}:disable','PUT:/api/v1/platform/menu-manifest','GET:/api/v1/platform/menu-manifest','GET:/api/v1/platform/plugins','POST:/api/v1/platform/plugins','POST:/api/v1/platform/plugins/{pluginId}:enable','POST:/api/v1/platform/plugins/{pluginId}:disable','POST:/api/v1/platform/plugins/{pluginId}:upgrade','GET:/api/v1/platform/plugins/{pluginId}/diagnostic']
for method,path in [('GET',''),('GET','/types'),('POST',''),('POST','/{taskId}:trigger'),('GET','/{taskId}/executions'),('GET','/{taskId}/events')]:ROUTES.append(method+':/api/v1/enterprises/{enterpriseId}/tasks'+path)
for method,path in [('GET',''),('POST',''),('GET','/{messageId}'),('GET','/connections'),('POST','/{messageId}:ack')]:ROUTES.append(method+':/api/v1/enterprises/{enterpriseId}/messages'+path)

class Grants:
    def __init__(self,env,evidence):self.env=env;self.ev=evidence
    def seed(self):
        env=self.env;ev=self.ev;admin=Client().login(23081,env.username,env.password)
        catalog,_=ev.call(admin,'/api/v1/platform/capabilities/catalog')
        # Catalog wraps the published capability list.
        by_code={item['code']:item['capabilityId']for item in catalog['capabilities']}
        ids=[]
        for code in CODES:
            if code.startswith(('platform.','internal.','security.')):
                if code not in by_code:
                    d,_=ev.call(admin,B+'/capabilities','POST',{'code':code,'resource':code,'action':code.rsplit('.',1)[1],'scope':'PLATFORM','riskLevel':'HIGH'},201,mutation());by_code[code]=d['capabilityId']
                ids.append(by_code[code])
            else:ids.append(code)
        role,_=ev.call(admin,B+'/roles','POST',{'name':'Release acceptance administrator','code':'release-'+env.run,'capabilityIds':ids+ROUTES},201,mutation());self.role=role['roleId']
        ev.call(admin,B+'/users/'+env.username+'/roles','PUT',{'roleIds':[self.role],'deniedCapabilityCodes':[]},headers=mutation('"1"'))
        self.admin=Client().login(23081,env.username,env.password)
        ev.call(self.admin,'/api/users','POST',{'username':env.denied_username,'password':env.denied_password,'role':'USER'})
        ev.call(self.admin,B+'/members','POST',{'subject':env.denied_username,'displayName':'Release restricted reader','email':'release-reader@example.test'},201,mutation())
        ev.call(self.admin,B+'/users/'+env.denied_username+'/roles','PUT',{'roleIds':[],'deniedCapabilityCodes':[]},headers=mutation('"1"'))
        self.denied=Client().login(23081,env.denied_username,env.denied_password)
        return self
    def bind(self,subject,roles,denies):
        path=B+'/users/'+subject+'/roles';_,h=self.ev.call(self.admin,path)
        return self.ev.call(self.admin,path,'PUT',{'roleIds':roles,'deniedCapabilityCodes':denies},headers=mutation(h['ETag']))
