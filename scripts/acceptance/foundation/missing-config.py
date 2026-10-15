"""Reject missing datasource configuration without printing child process output."""
import json, os, re, subprocess
from environment import Environment, ROOT, OUT, JAVA, immutable_candidate

env = Environment()
env.redis_port = 6379
properties = env.properties(20083)
for key in ('spring.datasource.url', 'spring.datasource.username', 'spring.datasource.password'):
    properties.pop(key)
child = {key: value for key, value in os.environ.items() if not key.startswith(('SPRING_', 'SUMMER_'))}
child['SPRING_APPLICATION_JSON'] = json.dumps(properties)
run = subprocess.run([str(JAVA), '-Xmx256m', '-jar', str(immutable_candidate())],
                     cwd=ROOT, env=child, capture_output=True, text=True, timeout=70)
output = run.stdout + run.stderr
result = {
    'exit': run.returncode,
    'startup_rejected': run.returncode != 0,
    'control': 'normal runtime properties except datasource URL/user/password; no services created',
    'datasource_failure_detected': any(marker in output.lower() for marker in
                                      ('jdbc url', 'jdbcurl', 'datasource', 'spring_datasource')),
    'exception_classes': sorted(set(re.findall(r'((?:org|com|java)\.[\w.$]*(?:Exception|Error)):', output))),
    'raw_output_saved': False,
    'secret_scan_passed': not any(value in output for value in
                                  (env.master, env.redis, env.mysql, env.password, env.db_password)),
}
(OUT / 'missing-datasource-config.json').write_text(json.dumps(result, indent=2))
print(json.dumps(result))
raise SystemExit(0 if result['startup_rejected'] and result['datasource_failure_detected']
                 and result['secret_scan_passed'] else 1)
