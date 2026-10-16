"""Exercise the checked-in encrypted archive CLI on owned schemas only."""
import hashlib,json,os,secrets,subprocess
from environment import ROOT,OUT

TABLES=['summer_security_keys','summer_authorization_user_binding','summer_authorization_role','summer_authorization_capability','summer_auth_user','summer_enterprise_member','summer_system_parameter','oauth2_registered_client','oauth2_authorization','flyway_schema_history']

def backup_restore(env,evidence,client,candidate,parameter_path,tables=TABLES):
    adapter=OUT/'mysql-cli';adapter.mkdir()
    body='''#!/usr/bin/env python3
import os,sys,subprocess
from pathlib import Path
program=Path(sys.argv[0]).name
payload=os.environ['MYSQL_PWD'].encode()+b'\\n'+sys.stdin.buffer.read()
r=subprocess.run(['kubectl','--context','docker-desktop','exec','-i','-n','local-base','mysql-0','--','sh','-c','IFS= read -r MYSQL_PWD; export MYSQL_PWD; exec "$@"','sh',program,*sys.argv[1:]],input=payload,capture_output=True)
sys.stdout.buffer.write(r.stdout)
if r.returncode:sys.stderr.write('owned-schema mysql adapter failed\\n')
sys.exit(r.returncode)
'''
    for name in ['mysql','mysqldump']:
        path=adapter/name;path.write_text(body);path.chmod(0o700)
    archive=OUT/'recovery.sql.enc';passphrase=secrets.token_urlsafe(40)
    options=os.environ.copy();options.update({'PATH':str(adapter)+os.pathsep+options['PATH'],'T36_DB_HOST':'127.0.0.1','T36_DB_PORT':'3306','T36_DB_NAME':env.schema,'T36_DB_USER':'root','T36_DB_PASSWORD':env.mysql,'T36_BACKUP_PASSPHRASE':passphrase,'T36_BACKUP_FILE':str(archive),'T36_REPORT_DIR':str(OUT),'T36_ALLOW_RESTORE':'1','T36_RESTORE_DATABASE':env.restore})
    evidence.check('restore target is new',env.sql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='"+env.restore+"'")=='0')
    for mode in ['backup','restore']:
        result=subprocess.run(['bash','scripts/t36-upgrade-gate.sh',mode],cwd=ROOT,env=options,capture_output=True)
        # Cleanup ownership must be set even if the CLI failed after CREATE DATABASE.
        if mode=='restore':env.restore_created=env.sql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='"+env.restore+"'")=='1'
        evidence.check('encrypted archive '+mode,result.returncode==0,exitCode=result.returncode)
    encrypted=archive.read_bytes();evidence.check('backup is encrypted and private',encrypted.startswith(b'Salted__')and b'CREATE TABLE'not in encrypted and archive.stat().st_mode&0o777==0o600)
    (OUT/'encrypted-backup.json').write_text(json.dumps({'sha256':hashlib.sha256(encrypted).hexdigest(),'bytes':len(encrypted),'plaintextWritten':False},indent=2));del encrypted
    counts={table:[int(env.sql('SELECT COUNT(*) FROM '+table,db))for db in [env.schema,env.restore]]for table in tables}
    evidence.check('key data and permission table counts restored',all(a==b for a,b in counts.values()),tables=counts)
    result=subprocess.run(['bash','scripts/t36-upgrade-gate.sh','restore'],cwd=ROOT,env=options,capture_output=True)
    evidence.check('existing restore target cannot be overwritten',result.returncode!=0)
    # Make a visible post-backup change only through the source API.
    before,_=evidence.call(client,parameter_path)
    evidence.call(client,parameter_path,'PATCH',{'value':'post-backup','version':before['version']})
    env.sql("GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,DROP,REFERENCES ON `"+env.restore+"`.* TO '"+env.db_user+"'@'%'")
    env.extra={'spring.data.redis.database':14,'spring.session.data.redis.namespace':'summer:t36:'+env.run+':restore-servlet','summer.sample.session-index.namespace':'summer:t36:'+env.run+':restore-index'}
    try:env.start(23083,jar=candidate,database=env.restore)
    finally:env.extra={}
    restored,_=evidence.call(client,parameter_path,port=23083)
    evidence.check('restored key data differs from later source mutation',restored['value']==before['value']and restored['value']!='post-backup')
    return archive
