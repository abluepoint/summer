"""Real management API bootstrap. No SQL business grants or synthetic principals."""
import uuid

BASE='/api/v1/enterprises/ent-acme'
ADMIN_CODES=['enterprise.roles.read','enterprise.roles.write','enterprise.capabilities.read','enterprise.capabilities.write','enterprise.members.read','enterprise.members.write','enterprise.menu.read','enterprise.menu.write','menu.menuManifest.read','customer.read','customer.write','GET:/api/customers','POST:/api/customers','GET:/api/customers/{username}','platform.interface-resources.read','platform.menu.write','platform.capabilities.read','platform.capabilities.write','GET:/api/users','POST:/api/users','PATCH:/api/users/{username}']

def headers(version=None):
    h={'Idempotency-Key':uuid.uuid4().hex,'X-Request-Id':str(uuid.uuid4())}
    if version is not None:h['If-Match']='"'+str(version)+'"'
    return h

def data(body):return body['data']

def bootstrap(admin,username):
    status,body,_=admin.request(23081,BASE+'/roles','POST',{'name':'T16 managed administrator','code':'t16-admin-role','capabilityIds':ADMIN_CODES},headers=headers())
    assert status==201,'bootstrap_role_status_'+str(status)
    role=data(body)
    status,body,_=admin.request(23081,BASE+'/users/'+username+'/roles','PUT',{'roleIds':[role['roleId']],'deniedCapabilityCodes':[]},headers=headers(1))
    assert status==200,'bootstrap_binding_status_'+str(status)
    return role
