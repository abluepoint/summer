import uuid

def unwrap(data):return data.get('data',data) if isinstance(data,dict) else data

CAPABILITIES='''enterprise.roles.read enterprise.roles.write enterprise.capabilities.read enterprise.capabilities.write enterprise.members.read enterprise.members.write enterprise.organization.read enterprise.organization.write enterprise.invitation.read enterprise.invitation.write enterprise.tasks.read platform.dictionary.read platform.enterprise.read platform.enterprise.write enterprise.audit.read enterprise.menu.read enterprise.data-scope.read enterprise.data-scope.write platform.menu.write platform.capabilities.read platform.capabilities.write'''.split()
ROUTES=[('GET','/api/users'),('POST','/api/users'),('PATCH','/api/users/{username}'),('GET','/api/audit-events'),('GET','/api/audit-events/{eventId}'),('POST','/api/v1/enterprises/{enterpriseId}/roles'),('GET','/api/v1/enterprises/{enterpriseId}/roles'),('PUT','/api/v1/enterprises/{enterpriseId}/users/{username}/roles'),('GET','/api/v1/enterprises/{enterpriseId}/users/{username}/roles'),('POST','/api/v1/enterprises/{enterpriseId}/invitations'),('GET','/api/v1/enterprises/{enterpriseId}/invitations/{invitationId}'),('GET','/api/v1/enterprises/{enterpriseId}/invitations/{invitationId}/audit'),('GET','/api/v1/enterprises/{enterpriseId}/tasks')]

def create_role(client,tenant,codes,name='Live acceptance role'):
    status,data,_=client.request(21081,f'/api/v1/enterprises/{tenant}/roles','POST',{'name':name,'code':'live-'+uuid.uuid4().hex[:12],'capabilityIds':codes},headers={'Idempotency-Key':uuid.uuid4().hex})
    assert status==201,'real_role_create_status_'+str(status)
    return unwrap(data)['roleId']

def bind(client,tenant,username,roles):
    status,data,_=client.request(21081,f'/api/v1/enterprises/{tenant}/users/{username}/roles','PUT',{'roleIds':roles,'deniedCapabilityCodes':[]},headers={'If-Match':'"1"','Idempotency-Key':uuid.uuid4().hex})
    assert status==200,'real_role_bind_status_'+str(status)
    return unwrap(data)

def bootstrap(client,username):
    platform_codes=['platform.enterprise.read','platform.enterprise.write']
    platform_ids=[]
    for code in platform_codes:
        status,data,_=client.request(21081,'/api/v1/enterprises/ent-acme/capabilities','POST',{'code':code,'resource':'enterprise','action':code.rsplit('.',1)[1],'riskLevel':'HIGH','scope':'PLATFORM'},headers={'Idempotency-Key':uuid.uuid4().hex})
        assert status==201,'real_platform_capability_create_status_'+str(status)
        platform_ids.append(unwrap(data)['capabilityId'])
    role=create_role(client,'ent-acme',[code for code in CAPABILITIES if code not in platform_codes]+platform_ids+[method+':'+path for method,path in ROUTES])
    bind(client,'ent-acme',username,[role]);return role
