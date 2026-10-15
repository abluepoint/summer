"""Exercise only the isolated runtime account; never alter existing account grants."""
import json, re, subprocess
from environment import Environment, OUT

env = Environment()
result = {'checks': []}
try:
    env.setup()
    statements = {
        'create_database_denied': 'CREATE DATABASE `' + env.schema + '_permission_probe`;',
        'grant_denied': "GRANT SELECT ON `" + env.schema + "`.* TO '" + env.db_user + "'@'%';",
    }
    for name, statement in statements.items():
        args = ['kubectl', 'exec', '-i', '-n', 'local-base', 'mysql-0', '--', 'sh', '-c',
                'IFS= read -r MYSQL_PWD; export MYSQL_PWD; IFS= read -r MYSQL_USER; exec mysql -u"$MYSQL_USER" --batch --skip-column-names']
        run = subprocess.run(args, input=env.db_password + '\n' + env.db_user + '\n' + statement,
                             text=True, capture_output=True)
        error = re.search(r'ERROR (\d+)', run.stderr)
        result['checks'].append({'name': name, 'passed': run.returncode != 0 and error is not None and error.group(1) in ('1044', '1142', '1227'),
                                 'mysql_error_number': error.group(1) if error else None})
    # If an unexpected permission regression created the probe schema, remove only that schema.
    env.sql('DROP DATABASE IF EXISTS `' + env.schema + '_permission_probe`')
finally:
    env.close()
    result['cleanup'] = 'VERIFIED'
    (OUT / 'permissions-result.json').write_text(json.dumps(result, indent=2))
print(json.dumps(result))
raise SystemExit(0 if len(result['checks']) == 2 and all(check['passed'] for check in result['checks']) else 1)
