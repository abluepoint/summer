"""Run the original three native migration classes (12 cases), using two complete artifacts.

Build and stop Gradle first. The external resource owner must provision a fresh
schema/account/Redis namespace lease and release it in finally. This runner never
creates those resources or changes authorization mode. No synthetic old classes.
"""
from pathlib import Path
import argparse, hashlib, json, os, re, signal, socket, subprocess, sys, threading, time, zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
HELPERS = ROOT / 'summer-sample/src/test/java/acceptance/perm12'
STANDALONE = ROOT / 'scripts/acceptance/permission-migration/java/acceptance/perm12'
CANONICAL = ROOT / 'summer-sample/src/test/java/com/abluepoint/summer/sample/authorization/migration'
TEST_NAMES = ['AuthorizationMigrationMySqlTest', 'AuthorizationDataMigrationMySqlTest',
              'AuthorizationMigrationRuntimeMySqlTest']
HELPER_NAMES = ['RunSettings.java', 'MvcOnlyFixture.java', 'MigrationArtifactHarness.java']
STANDALONE_NAMES = ['OldBridgeCaptureMain.java', 'HarnessJUnitMain.java']
CASE_IDS = ['com.abluepoint.summer.sample.authorization.migration.AuthorizationMigrationMySqlTest#rejectsFrozenHashFormatPersistentFactAndTargetIncarnationDrift', 'com.abluepoint.summer.sample.authorization.migration.AuthorizationMigrationMySqlTest#readsFrozenInputWithoutReopeningRemovedLegacyTables', 'com.abluepoint.summer.sample.authorization.migration.AuthorizationMigrationMySqlTest#resumesTheSamePersistedFrozenInputWithoutRecapturingItsLegacyCodes', 'com.abluepoint.summer.sample.authorization.migration.AuthorizationMigrationMySqlTest#neverRecreatesLegacyWhenModeStateIsMissingAndManagedFactsSurvive', 'com.abluepoint.summer.sample.authorization.migration.AuthorizationMigrationMySqlTest#inventoriesPlatformSourcesWithoutRelabelingAnEnterpriseNamedPlatform', 'com.abluepoint.summer.sample.authorization.migration.AuthorizationMigrationMySqlTest#persistsAnIdempotentDryRunWithoutChangingEngineMode', 'com.abluepoint.summer.sample.authorization.migration.AuthorizationMigrationMySqlTest#resumesStagingFromDurableStepsAndReplaysWithoutMoreAuditOrVersions', 'com.abluepoint.summer.sample.authorization.migration.AuthorizationDataMigrationMySqlTest#stagesExactDataPermissionsAndRollsBackFactsAuditOutboxAndCheckpointTogether', 'com.abluepoint.summer.sample.authorization.migration.AuthorizationDataMigrationMySqlTest#concurrentSameCheckpointHasOneAtomicWinnerAndOneStaleRevisionConflict', 'com.abluepoint.summer.sample.authorization.migration.AuthorizationDataMigrationMySqlTest#resumesDataVectorReplacementAndReplaysWithoutAdvancingAuthority', 'com.abluepoint.summer.sample.authorization.migration.AuthorizationDataMigrationMySqlTest#preservesImmutableSourceAndUsesSeparateForeignKeyCheckpoint', 'com.abluepoint.summer.sample.authorization.migration.AuthorizationMigrationRuntimeMySqlTest#inventoriesExactInstalledRuntimeAndBlocksRatherThanSynthesizingAGrant']

def sha(path):
    with path.open('rb') as stream: return hashlib.file_digest(stream, 'sha256').hexdigest()

def fail(message): raise RuntimeError(message)

def other_test_java_processes():
    rows = []
    for line in subprocess.check_output(['ps', '-axo', 'pid=,args='], text=True).splitlines():
        parts = line.strip().split(None, 1)
        if len(parts) != 2: continue
        if parts[1].split(None, 1)[0].rsplit('/', 1)[-1] != 'java': continue
        for kind in ['org.gradle.launcher.daemon.bootstrap.GradleDaemon', 'GradleWorkerMain',
                     'acceptance.perm12.HarnessJUnitMain', 'acceptance.perm12.OldBridgeCaptureMain']:
            if kind in parts[1]:
                rows.append({'pid': int(parts[0]), 'kind': kind})
                break
    return rows

def layout(artifact, destination):
    destination.mkdir()
    classes, libs = destination / 'classes', destination / 'lib'
    classes.mkdir(); libs.mkdir()
    with zipfile.ZipFile(artifact) as archive:
        for name in archive.namelist():
            if name.endswith('/'): continue
            if name.startswith('BOOT-INF/classes/'):
                target = classes / name.removeprefix('BOOT-INF/classes/')
            elif name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
                target = libs / name.removeprefix('BOOT-INF/lib/')
            else: continue
            if not target.resolve().is_relative_to(destination.resolve()): fail('Unsafe archive entry')
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.read(name))
    return [classes] + sorted(libs.glob('*.jar'))

def process_snapshot():
    lines = subprocess.check_output(['ps', '-axo', 'pid=,ppid=,pgid=,stat=,lstart=,args='],
                                    text=True, env=dict(os.environ, LC_ALL='C')).splitlines()
    rows = {}
    for line in lines:
        fields = line.strip().split(None, 9)
        if len(fields) != 10: fail('Cannot parse process inventory; no signal permitted')
        start = int(time.mktime(time.strptime(' '.join(fields[4:9]), '%a %b %d %H:%M:%S %Y')))
        row = {'pid': int(fields[0]), 'ppid': int(fields[1]), 'pgid': int(fields[2]),
               'stat': fields[3], 'startedEpochSecond': start, 'argv': fields[9]}
        rows[row['pid']] = row
    return rows

def owned_group_members(pid, run, identities, old_prefix, process):
    process.poll()  # Reap our worker/compile leader before requiring actual PID absence.
    snapshot = process_snapshot()
    members = [row for row in snapshot.values() if row['pgid'] == pid]
    if not members: return []
    leader = identities.get(pid)
    if leader is None: fail('Unverified process-group leader; unknown processes preserved')
    for row in members:
        previous = identities.get(row['pid'])
        if previous is None:
            record_files = list(run.rglob('old-process-' + str(row['pid']) + '.json'))
            if len(record_files) > 1: fail('Ambiguous old child process receipt')
            record = json.loads(record_files[0].read_text()) if record_files else {}
            parent_still_owned = row['ppid'] == pid and snapshot.get(pid, {}).get('startedEpochSecond') == leader['startedEpochSecond']
            recorded_child = record.get('parentPid') == pid and record.get('startedEpochSecond') == row['startedEpochSecond']
            if (not row['argv'].startswith(old_prefix) or str(run) not in row['argv']
                    or row['startedEpochSecond'] < leader['startedEpochSecond']
                    or not (parent_still_owned or recorded_child)):
                fail('Unverified process-group member; unknown processes preserved')
            identities[row['pid']] = row.copy()
            previous = identities[row['pid']]
        same_identity = all(previous[key] == row[key] for key in ['pid', 'pgid', 'startedEpochSecond'])
        same_argv = previous['argv'] == row['argv']
        if not same_identity or not same_argv or str(run) not in previous['argv']:
            fail('PID/group identity changed; unknown processes preserved')
    return [row['pid'] for row in members]

def kill_owned(pid, run, identities, old_prefix, process):
    # A fresh complete ownership check occurs immediately before EACH signal.
    def members(): return owned_group_members(pid, run, identities, old_prefix, process)
    def wait_empty(seconds):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            if not members(): return True
            time.sleep(.1)
        return not members()
    if not members(): return
    try: os.killpg(pid, signal.SIGTERM)
    except ProcessLookupError:
        if members(): fail('Process group changed during TERM; unknown processes preserved')
        return
    if wait_empty(2): return
    if not members(): return
    try: os.killpg(pid, signal.SIGKILL)
    except ProcessLookupError:
        if members(): fail('Process group changed during KILL; unknown processes preserved')
    if not wait_empty(5): fail('Owned process group remains after SIGKILL; release failed')
    if members(): fail('Owned process group reappeared; release failed')

def listening_ports(run):
    ports = set()
    for pattern in ['old-context-*.json', 'target-context-*.json']:
        for path in run.rglob(pattern):
            port = json.loads(path.read_text()).get('boundPort')
            if not isinstance(port, int) or not 0 < port < 65536: fail('Invalid actual port evidence')
            ports.add(port)
    remaining = []
    for port in sorted(ports):
        with socket.socket() as connection:
            connection.settimeout(.3)
            try: connection.connect(('127.0.0.1', port))
            except ConnectionRefusedError: continue
            except OSError: fail('Cannot confirm loopback port release')
            remaining.append(port)
    return {'observedPorts': sorted(ports), 'remainingListeners': remaining}

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--execute', action='store_true', required=True)
    p.add_argument('--run-dir', type=Path, required=True)
    p.add_argument('--java', type=Path, required=True)
    p.add_argument('--javac', type=Path, required=True)
    p.add_argument('--lease-record', type=Path, required=True)
    p.add_argument('--old-artifact', type=Path, required=True)
    p.add_argument('--old-sha256', required=True)
    p.add_argument('--current-artifact', type=Path, required=True)
    p.add_argument('--current-sha256', required=True)
    p.add_argument('--test-dependencies', type=Path, required=True)
    p.add_argument('--source-inputs', type=Path, required=True)
    args = p.parse_args()
    artifact_rows = [{'path': str(path.resolve()), 'sha256': digest} for path, digest in [
        (args.old_artifact, args.old_sha256), (args.current_artifact, args.current_sha256)]]
    for row in artifact_rows:
        if not re.fullmatch('[0-9a-f]{64}', row['sha256']): fail('Exact artifact SHA256 is required')
        if sha(Path(row['path'])) != row['sha256']: fail('Pinned artifact bytes changed')
    sources = json.loads(args.source_inputs.read_text())
    source_manifest_sha = sha(args.source_inputs)
    required_sources = [Path(__file__).resolve()] + [HELPERS / name for name in HELPER_NAMES] + [
        STANDALONE / name for name in STANDALONE_NAMES] + [
        CANONICAL / (name + '.java') for name in TEST_NAMES]
    if set(sources) != {str(path.relative_to(ROOT)) for path in required_sources}:
        fail('Source manifest must freeze the runner, five helpers and original three classes')
    for relative, digest in sources.items():
        if sha(ROOT / relative) != digest: fail('Frozen canonical source bytes changed')
    dependencies = json.loads(args.test_dependencies.read_text())
    dependency_manifest_sha = sha(args.test_dependencies)
    expected_libraries = ['junit-jupiter-api', 'junit-jupiter-engine', 'junit-platform-launcher',
                          'junit-platform-commons', 'junit-platform-engine', 'opentest4j',
                          'apiguardian-api', 'assertj-core']
    if len(dependencies) != len(expected_libraries): fail('Exactly eight test-only dependencies required')
    found_libraries = []
    for row in dependencies:
        if sha(Path(row['path'])) != row['sha256']: fail('Pinned supplemental dependency changed')
        names = [name for name in expected_libraries if Path(row['path']).name.startswith(name + '-')]
        if len(names) != 1: fail('Unsupported supplemental dependency')
        found_libraries.extend(names)
        with zipfile.ZipFile(row['path']) as archive:
            if any(name.startswith('com/abluepoint/') for name in archive.namelist()):
                fail('Test-only supplemental dependencies cannot carry application classes')
    if sorted(found_libraries) != sorted(expected_libraries): fail('Duplicate or missing test-only dependency')
    lease = json.loads(args.lease_record.read_text())
    if lease.get('resourceContract') != 'canonical12-method-schema/1':
        fail('A fresh per-method resource contract is required; single-schema leases are rejected')
    owner = lease.get('owner')
    if not isinstance(owner, str) or not owner or lease.get('databaseAndRedisOwned') is not True:
        fail('Existing explicit owner resource lease record is required')
    if lease.get('oldArtifactSha256') != args.old_sha256 or lease.get('currentArtifactSha256') != args.current_sha256:
        fail('New lease must bind both exact artifacts; historical batch grants are not accepted')
    if lease.get('sourceInputsSha256') != source_manifest_sha or lease.get('testDependenciesSha256') != dependency_manifest_sha:
        fail('New lease must bind canonical source and test dependency manifests')
    if lease.get('existingOwnedJavaPids') != [] or lease.get('maximumOwnedJavaProcesses') != 2:
        fail('Owner must release other owned Java processes before using both slots')
    conflicts = other_test_java_processes()
    if conflicts: fail('Gradle/test/bridge JVMs are still present; owner must release them before this independent batch')
    for gate in ['SUMMER_PERM12_MYSQL', 'SUMMER_PERM13_MYSQL']:
        if os.environ.get(gate) != 'true': fail('Both original opt-in gates must be explicitly enabled')
    lease_id = lease['leaseId']
    if not re.fullmatch(r'[a-zA-Z0-9_-]{8,80}', lease_id): fail('Invalid lease ID')
    run = args.run_dir.resolve()
    if str(run) != lease.get('runDirectory') or run.exists(): fail('A fresh run directory bound by the new lease is required')
    prefix = 'summer:perm12-harness:' + lease_id + ':'
    if lease.get('redisDatabase') != 1 or lease.get('redisNamespacePrefix') != prefix:
        fail('Explicit owned Redis DB1 root namespace required')
    case_leases = lease.get('cases', [])
    case_properties = json.loads(os.environ['PERM12_HARNESS_CASE_PROPERTIES'])
    if (len(case_leases) != 12 or [c.get('caseId') for c in case_leases] != CASE_IDS
            or set(case_properties) != set(CASE_IDS)):
        fail('Exactly the twelve original methods must have ordered independent resource leases')
    schemas = set()
    for index, case in enumerate(case_leases, 1):
        if set(case) != {'caseId', 'schema', 'account', 'runDirectory', 'redisNamespacePrefix'}:
            fail('Case lease may contain only the five public resource binding fields')
        schema = case.get('schema', '')
        # Each account is the same 32-character independently generated name as its schema.
        if not re.fullmatch(r'summer_perm12_harness_[0-9a-f]{10}', schema) or case.get('account') != schema:
            fail('Each case requires an exact schema and same-name MySQL account')
        if schema in schemas: fail('A schema/account cannot be shared across methods')
        schemas.add(schema)
        case_dir = run / ('case-' + str(index).zfill(2))
        case_prefix = prefix + 'case-' + str(index).zfill(2) + ':'
        if case.get('runDirectory') != str(case_dir) or case.get('redisNamespacePrefix') != case_prefix:
            fail('Case directory/namespace is not the exact leased binding')
        props = case_properties[case['caseId']]
        jdbc = props.get('spring.datasource.url', '')
        match = re.fullmatch(r'jdbc:mysql://127\.0\.0\.1:3306/(summer_perm12_harness_[0-9a-f]{10})(?:\?.*)?', jdbc)
        if not match or match[1] != schema or props.get('spring.datasource.username') != case['account']:
            fail('Case datasource does not match its exact schema/account lease')
        if props.get('spring.data.redis.database') != 1:
            fail('Every case must use owned Redis DB1')
        for key in ['spring.session.data.redis.namespace', 'summer.sample.session-index.namespace']:
            if not props.get(key, '').startswith(case_prefix): fail('Case namespace mismatch')
    for path in [args.java, args.javac]:
        if not path.is_file() or not os.access(path, os.X_OK): fail('Explicit Java21 java/javac paths are required')
    run.mkdir(parents=True)
    for case in case_leases: Path(case['runDirectory']).mkdir()
    secrets = [str(v) for props in case_properties.values() for k, v in props.items()
               if re.search('password|secret|master-key|token', k, re.I) and str(v)]
    def redact(value):
        for secret in sorted(secrets, key=len, reverse=True): value = value.replace(secret, '[redacted]')
        return re.sub(r'(?i)([?&](?:code|state|access_token|refresh_token|id_token|client_secret)=)[^\s&]+', r'\1[redacted]', value)
    evidence = {'status': 'preparing', 'artifacts': artifact_rows,
                'leaseRecordPath': str(args.lease_record.resolve()), 'leaseRecordSha256': sha(args.lease_record),
                'leaseId': lease_id, 'cases': case_leases, 'resourceOwner': owner,
                'redisDatabase': 1, 'redisNamespacePrefix': prefix,
                'preflightOtherGradleAndTestProcesses': conflicts,
                'heapBudget': {'compileJvmsSequential': 1, 'javacXmxMiB': 512,
                               'runtimeParentXmxMiB': 512, 'runtimeOldChildXmxMiB': 512,
                               'maximumSimultaneousOwnedHeavyJvms': 2},
                'sourceInputs': sources, 'sourceInputsSha256': source_manifest_sha,
                'testDependenciesSha256': dependency_manifest_sha,
                'resourceProvisioningAndCleanupOwner': 'external-resource-owner-wrapper',
                'sourceOrGitWrites': False, 'fullNativeSuiteCredit': False, 'commands': []}
    def save(): (run / 'execution.json').write_text(json.dumps(evidence, indent=2) + '\n')
    save()
    old = layout(Path(artifact_rows[0]['path']), run / 'old-artifact')
    target = layout(Path(artifact_rows[1]['path']), run / 'target-artifact')
    old_helpers, target_tests = run / 'old-helper-classes', run / 'target-test-classes'
    old_helpers.mkdir(); target_tests.mkdir()
    supplemental = [Path(row['path']) for row in dependencies]
    cp = lambda entries: os.pathsep.join(map(str, entries))
    (run / 'classpaths.json').write_text(json.dumps({
        'oldApplication': list(map(str, old)), 'targetApplication': list(map(str, target)),
        'oldHelper': str(old_helpers), 'targetTests': str(target_tests),
        'supplementalTestOnly': dependencies,
        'oldContainsNoTargetMainClasses': True, 'targetContainsNoOldMainClasses': True}, indent=2) + '\n')
    env = dict(os.environ, PERM12_HARNESS_RUN_DIR=str(run), PERM12_HARNESS_LEASE=lease_id,
               PERM12_HARNESS_RUNNER='canonical-12/1', PERM12_HARNESS_RESOURCE_OWNER=owner,
               PERM12_JAVA=str(args.java.resolve()), PERM12_OLD_CLASSPATH=cp([old_helpers] + old),
               PERM12_OLD_ARTIFACT_SHA=artifact_rows[0]['sha256'], PERM12_TARGET_ARTIFACT_SHA=artifact_rows[1]['sha256'])
    java_command, javac_command = str(args.java.resolve()), str(args.javac.resolve())
    old_prefix = ' '.join([java_command, '-Xmx512m', '-cp', env['PERM12_OLD_CLASSPATH'],
                           'acceptance.perm12.OldBridgeCaptureMain']) + ' '
    # Do not pass the batch credential map to Java or old children.
    env.pop('PERM12_HARNESS_CASE_PROPERTIES', None)
    env.pop('SPRING_APPLICATION_JSON', None)
    source_dir = HELPERS
    common = [source_dir / name for name in ['RunSettings.java', 'MvcOnlyFixture.java']]
    compile_jobs = [
        ('compile-old-helper', [javac_command, '-J-Xmx512m', '--release', '21', '-encoding', 'UTF-8', '-cp', cp(old),
                                '-d', str(old_helpers)] + list(map(str, common + [STANDALONE / 'OldBridgeCaptureMain.java']))),
        ('compile-target-tests', [javac_command, '-J-Xmx512m', '--release', '21', '-encoding', 'UTF-8', '-cp', cp(target + supplemental),
                                  '-d', str(target_tests)] + list(map(str, common + [source_dir / 'MigrationArtifactHarness.java',
                                  STANDALONE / 'HarnessJUnitMain.java'] + [CANONICAL / (name + '.java') for name in TEST_NAMES]))),
    ]
    child = None
    primary_failure = None
    failure_context = {'phase': 'prepare-protocol-jobs'}
    identities, processes = {}, {}
    def interrupt(signum, frame): raise KeyboardInterrupt
    signal.signal(signal.SIGTERM, interrupt)
    signal.signal(signal.SIGINT, interrupt)
    try:
        jobs = [(name, command, None) for name, command in compile_jobs]
        jobs += [('junit-case-' + str(index).zfill(2), [java_command, '-Xmx512m', '-cp',
                    cp([target_tests] + target + supplemental), 'acceptance.perm12.HarnessJUnitMain'], case)
                 for index, case in enumerate(case_leases, 1)]
        for name, command, case in jobs:
            failure_context = {'phase': name}
            if case is not None: failure_context['caseId'] = case['caseId']
            worker_env = dict(env)
            if case is not None:
                worker_env.update(SPRING_APPLICATION_JSON=json.dumps(case_properties[case['caseId']]),
                    PERM12_HARNESS_CASE_ID=case['caseId'], PERM12_HARNESS_SCHEMA=case['schema'],
                    PERM12_HARNESS_ACCOUNT=case['account'], PERM12_HARNESS_CASE_PREFIX=case['redisNamespacePrefix'],
                    PERM12_HARNESS_RUN_DIR=case['runDirectory'])
            row = {'phase': name, 'argv': command, 'startedAt': time.time()}
            if case is not None: row['caseId'] = case['caseId']; row['schema'] = case['schema']
            failure_context = row  # Keep the active command's actual exit when observed.
            evidence['commands'].append(row); save()
            child = subprocess.Popen(command, cwd=ROOT, env=worker_env, stdout=subprocess.PIPE,
                                     stderr=subprocess.STDOUT, text=True, start_new_session=True)
            processes[child.pid] = child
            row['pid'] = child.pid; save()
            identity = process_snapshot().get(child.pid)
            if identity is not None:
                if identity['pgid'] != child.pid or identity['argv'] != ' '.join(command):
                    fail('Cannot establish exact owned leader identity')
                identities[child.pid] = identity
                row['identity'] = identity
            elif child.poll() is None:
                fail('Cannot observe owned process leader')
            save()
            log_errors = []
            def drain(process, path):
                try:
                    with path.open('x') as log:
                        for line in process.stdout:
                            log.write(redact(line)); log.flush()
                except Exception as error: log_errors.append(type(error).__name__)
            reader = threading.Thread(target=drain, args=(child, run / (name + '.log')), daemon=True)
            reader.start()
            row['exitCode'] = child.wait(timeout=2700 if case is not None else 180)
            reader.join(timeout=10)
            if reader.is_alive(): fail('Owned stdout reader did not stop')
            if log_errors: fail('Sanitized stdout log writer failed')
            row['stopped'] = True; save()
            # Release and prove the entire worker group empty before the next method starts.
            kill_owned(child.pid, run, identities, old_prefix, child)
            if case is not None:
                row['casePortRelease'] = listening_ports(Path(case['runDirectory']))
                if row['casePortRelease']['remainingListeners']: fail('Case port remains before next method')
                save()
            if case is None and row['exitCode']: fail(name + ' failed; no passing credit')
            child = None
        aggregate = {'phase': 'aggregate-protocol-results', 'step': 'collect-case-results',
                     'currentCaseId': None, 'failedCaseIds': [], 'caseResults': []}
        failure_context = aggregate
        evidence['aggregate'] = aggregate
        worker_results = {row['caseId']: row for row in evidence['commands'] if 'caseId' in row}
        aggregate['caseResults'] = [
            {'caseId': case['caseId'], 'workerExitCode': worker_results[case['caseId']].get('exitCode'),
             'resultStatus': 'not-read', 'xmlStatus': 'not-read'} for case in case_leases]
        aggregate['failedCaseIds'] = [r['caseId'] for r in aggregate['caseResults'] if r['workerExitCode'] != 0]
        save()
        by_class, results = {}, []
        for case, receipt in zip(case_leases, aggregate['caseResults']):
            aggregate['currentCaseId'] = case['caseId']
            aggregate['step'] = 'read-case-result'
            directory = Path(case['runDirectory'])
            result = json.loads((directory / 'case-result.json').read_text())
            receipt['resultStatus'] = 'read'
            if result.get('caseId') != case['caseId'] or result.get('discovered') != 1:
                fail('Missing or mismatched per-method result')
            if (any(type(result.get(key)) is not int or result[key] < 0 for key in ['tests', 'failures', 'skipped'])
                    or type(result.get('caseCompletedWithoutSkip')) is not bool):
                fail('Invalid per-method result counters')
            # Persist only fixed scalar result fields, never raw result/exception contents.
            receipt.update({key: result[key] for key in ['tests', 'failures', 'skipped', 'caseCompletedWithoutSkip']})
            receipt['resultStatus'] = 'validated'
            if (result['failures'] or result['skipped'] or not result['caseCompletedWithoutSkip']):
                aggregate['failedCaseIds'].append(case['caseId'])
            aggregate['step'] = 'read-case-xml'
            reports = list(directory.glob('TEST-*.xml'))
            if len(reports) != 1: fail('Exactly one case XML is required')
            suite = ET.parse(reports[0]).getroot()
            nodes = list(suite.findall('testcase'))
            if len(nodes) != 1 or nodes[0].get('classname') + '#' + nodes[0].get('name') != case['caseId']:
                fail('Per-method XML identity/count mismatch')
            receipt['xmlStatus'] = 'identity-validated'
            by_class.setdefault(nodes[0].get('classname'), []).append(nodes[0])
            results.append(result)
        aggregate['currentCaseId'] = None
        aggregate['step'] = 'summarize-results'
        aggregate['failedCaseIds'] = [case_id for case_id in CASE_IDS if case_id in aggregate['failedCaseIds']]
        failures = sum(r['failures'] for r in results)
        skipped = sum(r['skipped'] for r in results)
        complete = (sum(r['tests'] for r in results) == 12 and failures == 0 and skipped == 0
                    and all(r.get('caseCompletedWithoutSkip') for r in results)
                    and all(r['exitCode'] == 0 for r in evidence['commands']))
        aggregate['step'] = 'write-suite-xml'
        for name, nodes in by_class.items():
            suite = ET.Element('testsuite', name=name, tests=str(len(nodes)), errors='0',
                failures=str(sum(n.find('failure') is not None for n in nodes)),
                skipped=str(sum(n.find('skipped') is not None for n in nodes)))
            suite.extend(nodes)
            with (run / ('TEST-' + name + '.xml')).open('xb') as output:
                ET.ElementTree(suite).write(output, encoding='utf-8', xml_declaration=True)
        result = {'discovered': 12, 'tests': sum(r['tests'] for r in results), 'failures': failures,
                  'skipped': skipped, 'allTwelveCompletedWithoutSkip': complete,
                  'scope': 'canonical-three-classes-twelve-cases', 'fullNativeSuiteCredit': False}
        aggregate['step'] = 'write-junit-result'
        (run / 'junit-result.json').write_text(json.dumps(result, indent=2) + '\n')
        aggregate['step'] = 'validate-completion'
        aggregate['allTwelveCompletedWithoutSkip'] = complete
        if not complete: fail('Missing, failed or skipped original protocol cases; no passing credit')
        evidence['status'] = 'original-three-classes-twelve-protocol-cases-passed'
    except BaseException as error:
        primary_failure = error
        evidence['status'] = 'failed-or-interrupted'
        evidence['failureClass'] = type(error).__name__
        failure = {'class': type(error).__name__, 'phase': failure_context['phase']}
        if failure_context['phase'] == 'aggregate-protocol-results':
            failed_ids = set(aggregate['failedCaseIds'])
            current_id = aggregate['currentCaseId']
            if current_id is not None:
                failed_ids.add(current_id)
                receipt = next(r for r in aggregate['caseResults'] if r['caseId'] == current_id)
                receipt['validationFailureClass'] = type(error).__name__
                receipt['validationFailureStep'] = aggregate['step']
                failure['caseId'] = current_id
            aggregate['failedCaseIds'] = [case_id for case_id in CASE_IDS if case_id in failed_ids]
            failure.update(aggregationStep=aggregate['step'], failedCaseIds=aggregate['failedCaseIds'],
                           failedCases=[r for r in aggregate['caseResults'] if r['caseId'] in failed_ids])
            # Aggregate validation is not a subprocess; it has no observed command exit.
        else:
            failure['observedExitCode'] = failure_context.get('exitCode')
            if 'caseId' in failure_context: failure['caseId'] = failure_context['caseId']
        evidence['primaryFailure'] = failure
        raise
    finally:
        try:
            if child is not None:
                kill_owned(child.pid, run, identities, old_prefix, child)
                child.wait(timeout=10)
            # Even on normal worker exit, inspect any old child in the unique owned group.
            for row in evidence['commands']:
                if 'pid' in row:
                    process = processes[row['pid']]
                    kill_owned(row['pid'], run, identities, old_prefix, process)
                    row['ownedGroupMembersAfterRelease'] = owned_group_members(row['pid'], run, identities, old_prefix, process)
                    if row['ownedGroupMembersAfterRelease']: fail('Remaining owned process group; release failed')
                    row['exitCodeAfterRelease'] = process.poll()
                    row['stopped'] = row['exitCodeAfterRelease'] is not None
        except BaseException as release_error:
            evidence['ownedProcessReleaseFailed'] = True
            evidence['releaseFailureClass'] = type(release_error).__name__
            evidence['status'] = 'failed-or-interrupted'
            if primary_failure is None: raise
        finally:
            evidence['externalResourceCleanupRequired'] = True
            try:
                evidence['portRelease'] = listening_ports(run)
                if evidence['portRelease']['remainingListeners']:
                    evidence['ownedPortReleaseFailed'] = True
                    evidence['status'] = 'failed-or-interrupted'
            except BaseException as port_error:
                evidence['ownedPortReleaseFailed'] = True
                evidence['portInventoryFailureClass'] = type(port_error).__name__
                evidence['status'] = 'failed-or-interrupted'
            try:
                snapshot = process_snapshot()
                evidence['remainingGroupPids'] = sorted(row['pid'] for row in snapshot.values()
                    if row['pgid'] in processes)
                if evidence['remainingGroupPids']:
                    evidence['ownedProcessReleaseFailed'] = True
                    evidence['status'] = 'failed-or-interrupted'
            except BaseException as inventory_error:
                evidence['ownedProcessReleaseFailed'] = True
                evidence['releaseInventoryFailureClass'] = type(inventory_error).__name__
                evidence['status'] = 'failed-or-interrupted'
            def end_sha(path):
                try: return sha(path)
                except OSError as read_error:
                    evidence.setdefault('inputReadFailures', []).append({'path': str(path), 'class': type(read_error).__name__})
                    return None
            for row in artifact_rows:
                row['endSha256'] = end_sha(Path(row['path']))
            evidence['artifactBytesUnchanged'] = all(row['sha256'] == row['endSha256'] for row in artifact_rows)
            evidence['canonicalSourceDelta'] = [relative for relative, digest in sources.items() if end_sha(ROOT / relative) != digest]
            evidence['testDependencyDelta'] = [row['path'] for row in dependencies if end_sha(Path(row['path'])) != row['sha256']]
            evidence['sourceInputsManifestUnchanged'] = end_sha(args.source_inputs) == source_manifest_sha
            evidence['testDependenciesManifestUnchanged'] = end_sha(args.test_dependencies) == dependency_manifest_sha
            if (not evidence['artifactBytesUnchanged'] or evidence['canonicalSourceDelta'] or evidence['testDependencyDelta']
                    or not evidence['sourceInputsManifestUnchanged'] or not evidence['testDependenciesManifestUnchanged']):
                evidence['status'] = 'failed-input-drift'
            save()
    if evidence['status'] != 'original-three-classes-twelve-protocol-cases-passed':
        fail('Protocol inputs or process/port release failed')
    print(json.dumps({'status': evidence['status'], 'runDirectory': str(run), 'fullNativeSuiteCredit': False}))

if __name__ == '__main__': main()
