"""Generate an editable, two-page draw.io ERD from schema-only metadata."""
from pathlib import Path
import collections
import html
import json
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
DATA = json.loads((HERE / 'schema-metadata.json').read_text())
PREFIX = 'summer_'
TITLES = {
    'dictionary_type': '字典定义 · 草稿控制',
    'dictionary_item': '字典项',
    'dictionary_group': '字典分组 · 项的子集',
    'dictionary_group_member': '分组成员 · 多对多关联',
    'dictionary_version': '不可变发布版本',
    'dictionary_outbox': '发布事务事件 · Outbox',
    'dictionary_notification_event': '通知事件 · 增量游标',
    'configuration_override': '企业 / 组织配置覆盖',
    'configuration_override_revision': '覆盖配置 · 不可变历史',
}
COLORS = {
    'draft': ('#dae8fc', '#6c8ebf'),
    'published': ('#d5e8d4', '#82b366'),
    'override': ('#e1d5e7', '#9673a6'),
    'event': ('#ffe6cc', '#d79b00'),
    'neutral': ('#f5f5f5', '#666666'),
}
DESCRIPTIONS = {
    'id': '记录标识', 'tenant_id': '企业隔离标识',
    'organization_id': '组织归属；可空', 'created_by': '创建人', 'owner_id': '数据所有者',
    'code': '业务编码', 'name': '显示名称', 'published': '当前是否处于发布状态',
    'public_readable': '是否允许公开读取', 'enabled': '启用标记',
    'published_version': '当前生效的基础发布版本号；回滚可指向旧版本',
    'draft_revision': '草稿修订号；编辑并发控制',
    'effective_revision': '生效状态修订号；发布、停用、回滚并发控制',
    'dictionary_code': '字典业务编码；须结合 tenant_id 使用',
    'item_value': '供业务数据引用的字典值；曾发布后不可更改',
    'label': '字典项显示文本', 'sort_order': '排序；成员表内为组内独立排序',
    'description': '字典项说明', 'ever_published': '是否曾发布；身份不可变约束依据',
    'group_id': '字典分组 ID', 'item_id': '字典项 ID',
    'version_id': '发布版本记录 ID', 'version_number': '该字典发布版本号',
    'snapshot_json': '字典属性、字典项、分组及成员顺序的完整不可变 JSON 快照',
    'status': '状态；版本表为 PUBLISHED，Outbox 为 PENDING / PUBLISHED',
    'request_id': '请求追踪标识', 'created_at': '记录创建时间',
    'event_id': '事件主键；Outbox 为 UUID 字符串，通知表为自增 bigint',
    'event_type': '业务事件类型', 'payload_json': '事件载荷 JSON',
    'idempotency_key': '幂等键；允许空值', 'request_fingerprint': '请求语义指纹',
    'result_json': '幂等重放的结果回执 JSON', 'resource': '配置资源类型；字典为 dictionaries',
    'config_key': '配置业务键；字典场景为字典 code',
    'scope_id': '_tenant 表示企业覆盖；其他值为组织 ID',
    'value_json': '覆盖配置值；字典场景为字典项数组',
    'version': 'JPA 乐观锁版本；区别于历史修订号',
    'latest_revision': '最新覆盖修订号', 'published_revision': '当前已发布覆盖修订号',
    'updated_at': '最近更新时间', 'override_id': '所属覆盖配置 ID',
    'revision': '覆盖历史修订号', 'state': 'DRAFT / PUBLISHED / DISABLED / ROLLED_BACK',
    'principal': '操作者',
}


def groups(rows, predicate):
    result = collections.OrderedDict()
    for row in rows:
        if predicate(row):
            result.setdefault(row[1], []).append(row)
    return result


FK = groups(DATA['keys'], lambda row: bool(row[4]))
assert len(FK) == 5, 'Composite foreign keys must remain grouped.'
assert len(DATA['columns']) == 93


def columns(table):
    return [row for row in DATA['columns'] if row[0] == table]


def indexes(table):
    return groups(DATA['indexes'], lambda row: row[0] == table and row[2] == '0')


def provenance(table):
    return {'path': 'schema.sql', 'source': 'MySQL information_schema + SHOW CREATE TABLE',
            'table': table, 'commit': DATA['source_commit']}


def write_ir():
    # The SQL importer emits one edge per FK column. Normalize them into the
    # five actual composite constraints before retaining the semantic model.
    nodes = []
    for table in DATA['tables']:
        nodes.append({'id': table, 'label': TITLES[table.removeprefix(PREFIX)],
                      'kind': 'database', 'provenance': provenance(table),
                      'properties': {'table': table, 'columns': columns(table),
                                     'unique_indexes': list(indexes(table))}})
    edges = []
    for name, rows in FK.items():
        edges.append({'id': name, 'source': rows[0][4], 'target': rows[0][0],
                      'kind': 'foreign-key', 'label': name,
                      'properties': {'cardinality': '1 : 0..N',
                                     'columns': [r[2] for r in rows],
                                     'referenced_columns': [r[5] for r in rows]}})
    for suffix, target, note in [
        ('versions', 'dictionary_version', 'tenant_id + code；逻辑版本归属'),
        ('overrides', 'configuration_override', 'resource=dictionaries；tenant_id + config_key=code'),
        ('outbox', 'dictionary_outbox', 'tenant_id + dictionary_code；版本操作写入'),
        ('notifications', 'dictionary_notification_event', 'tenant_id + dictionary_code；版本操作或覆盖事件写入'),
    ]:
        edges.append({'id': 'logical-' + suffix, 'source': PREFIX + 'dictionary_type',
                      'target': PREFIX + target, 'kind': 'logical-reference', 'label': note})
    model = {'schema': 'drawio-skill/diagram-ir/v1',
             'metadata': {'title': '字典管理数据模型', 'commit': DATA['source_commit'],
                          'source': 'schema-only metadata; no business rows'},
             'nodes': nodes, 'edges': edges,
             'views': [{'id': 'core', 'title': '字典核心模型'},
                       {'id': 'supporting', 'title': '覆盖配置与发布事件'}]}
    (HERE / 'dictionary.ir.json').write_text(json.dumps(model, ensure_ascii=False, indent=2) + '\n')


FONT = 'fontFamily=Helvetica;fontColor=#172b4d;'
MX = ET.Element('mxfile', host='drawio', version='32.5.5', agent='Codex')


def cell(root, ident, value, style, x, y, w, h, parent='1', **extra):
    c = ET.SubElement(root, 'mxCell', id=ident, value=value,
                      style=style + FONT, vertex='1', parent=parent, **extra)
    ET.SubElement(c, 'mxGeometry', x=str(x), y=str(y), width=str(w), height=str(h), **{'as': 'geometry'})
    return c


def text(root, ident, value, x, y, w, h, size=16, bold=False, color='#172b4d'):
    return cell(root, ident, value,
                f'text;html=1;whiteSpace=wrap;align=left;verticalAlign=middle;fontSize={size};'
                f'fontStyle={1 if bold else 0};strokeColor=none;fillColor=none;spacing=0;fontColor={color};',
                x, y, w, h)


def page(ident, title, subtitle, number):
    diagram = ET.SubElement(MX, 'diagram', id=ident, name=title)
    graph = ET.SubElement(diagram, 'mxGraphModel', dx='1760', dy='1490', grid='1', gridSize='10',
                          page='1', pageScale='1', pageWidth='1760', pageHeight='1490',
                          background='#ffffff', math='0', shadow='0')
    root = ET.SubElement(graph, 'root')
    ET.SubElement(root, 'mxCell', id='0')
    ET.SubElement(root, 'mxCell', id='1', parent='0')
    text(root, ident + '-title', f'{number:02d}　{title}', 60, 30, 1600, 44, 30, True)
    text(root, ident + '-subtitle', subtitle, 60, 86, 1600, 30, 16)
    legend(root, ident)
    text(root, ident + '-source',
         '来源：summer-next-4 @ ' + DATA['source_commit'][:12] +
         ' · summer_next4_dev 实际表结构与 Java 实现 · 2026-11-15 · 仅模型，无业务数据',
         60, 1446, 1640, 22, 13)
    return root


def legend(root, prefix):
    labels = [('draft', '草稿实体'), ('published', '发布快照'),
              ('override', '覆盖配置'), ('event', '事件记录')]
    for i, (role, label) in enumerate(labels):
        fill, stroke = COLORS[role]
        cell(root, f'{prefix}-legend-{role}', label,
             f'rounded=1;html=1;fontSize=13;fillColor={fill};strokeColor={stroke};',
             60 + i * 142, 1398, 128, 28)
    text(root, prefix + '-legend-keys', 'PK 主键　FK 外键列　? 可空　UK 唯一约束（字段组合）',
         650, 1397, 1020, 30, 14)


def table(root, short, x, y, role, foot, footer=86, subset=None, context=False):
    name = PREFIX + short
    rows = columns(name)
    if subset is not None:
        rows = [r for r in rows if r[1] in subset]
    w = 450
    h = 64 + 27 * len(rows) + footer
    fill, stroke = COLORS[role]
    title = TITLES[short] + ('（上下文引用）' if context else '')
    val = f'<b>{html.escape(title)}</b><br><font style="font-size:13px">{name}</font>'
    cell(root, name, val,
         'shape=table;startSize=64;container=1;collapsible=0;childLayout=tableLayout;'
         'fixedRows=1;rowLines=0;html=1;whiteSpace=wrap;align=left;verticalAlign=top;'
         f'spacingTop=10;spacingLeft=14;fontSize=19;fontStyle=0;fillColor={fill};strokeColor={stroke};',
         x, y, w, h, **{'data-model-id': name, 'data-source': 'schema.sql', 'data-role': role})
    pk = {r[2] for r in DATA['keys'] if r[0] == name and r[1] == 'PRIMARY'}
    fk = {r[2] for r in DATA['keys'] if r[0] == name and r[4]}
    for i, row in enumerate(rows):
        field, typ, nullable = row[1], row[2], row[3] == 'YES'
        rid = name + '-row-' + field
        cell(root, rid, '',
             'shape=tableRow;horizontal=0;startSize=0;swimlaneHead=0;swimlaneBody=0;'
             'fillColor=none;strokeColor=none;collapsible=0;dropTarget=0;'
             'points=[[0,0.5],[1,0.5]];portConstraint=eastwest;',
             0, 64 + i * 27, w, 27, parent=name)
        marker = ('PK' if field in pk else '') + (' FK' if field in fk else '')
        for j, (value, cx, cw, align) in enumerate([
            (marker.strip(), 0, 48, 'center'), (field, 48, 270, 'left'),
            (typ + (' ?' if nullable else ''), 318, 132, 'right')]):
            cell(root, rid + '-cell-' + str(j), html.escape(value),
                 f'shape=partialRectangle;connectable=0;html=1;whiteSpace=wrap;align={align};'
                 f'verticalAlign=middle;fontSize={12 if j == 0 else 14};'
                 f'fontStyle={1 if field in pk else 0};fillColor=#ffffff;'
                 'strokeColor=#e8edf3;top=0;left=0;right=0;bottom=1;spacingLeft=5;spacingRight=10;',
                 cx, 0, cw, 27, parent=rid)
    note_id = name + '-constraints'
    cell(root, note_id, '',
         'shape=tableRow;horizontal=0;startSize=0;html=1;whiteSpace=wrap;'
         f'fillColor={fill};strokeColor={stroke};align=left;verticalAlign=middle;'
         'spacingLeft=13;spacingRight=12;fontSize=13;',
         0, 64 + len(rows) * 27, w, footer, parent=name)
    cell(root, note_id + '-text', foot,
         f'shape=partialRectangle;horizontal=1;html=1;whiteSpace=wrap;fillColor={fill};'
         f'strokeColor={stroke};top=1;left=0;right=0;bottom=0;align=left;'
         'verticalAlign=middle;spacingLeft=13;spacingRight=12;fontSize=13;',
         0, 0, w, footer, parent=note_id)
    return (x, y, w, h)


def note(root, ident, title, lines, x, y, w, h, size=16):
    label = '<b>' + html.escape(title) + '</b><br><br>' + '<br><br>'.join(lines)
    cell(root, ident, label,
         'rounded=1;arcSize=8;html=1;whiteSpace=wrap;fillColor=#f5f5f5;strokeColor=#d1d9e3;'
         f'align=left;verticalAlign=top;spacing=18;fontSize={size};', x, y, w, h)


def edge(root, ident, source, target, label, exitp, entryp, points=None, logical=False):
    style = ('edgeStyle=orthogonalEdgeStyle;rounded=1;orthogonalLoop=1;jettySize=auto;html=1;'
             'fontSize=14;labelBackgroundColor=#ffffff;strokeWidth=1.5;dashed=1;'
             f'exitX={exitp[0]};exitY={exitp[1]};exitDx=0;exitDy=0;'
             f'entryX={entryp[0]};entryY={entryp[1]};entryDx=0;entryDy=0;')
    style += ('strokeColor=#7a869a;dashPattern=2 5;startArrow=none;endArrow=open;endFill=0;'
              if logical else
              'strokeColor=#3b67a3;dashPattern=8 4;startArrow=ERmandOne;startFill=0;endArrow=ERmany;endFill=0;')
    c = ET.SubElement(root, 'mxCell', id=ident, value=label, style=style + FONT,
                      edge='1', parent='1', source=PREFIX + source, target=PREFIX + target,
                      **{'data-model-id': ident, 'data-kind': 'logical-reference' if logical else 'foreign-key'})
    geom = ET.SubElement(c, 'mxGeometry', relative='1', **{'as': 'geometry'})
    if points:
        arr = ET.SubElement(geom, 'Array', **{'as': 'points'})
        for px, py in points:
            ET.SubElement(arr, 'mxPoint', x=str(px), y=str(py))


def write_drawio():
    root = page('core', '字典核心模型',
                '5 张核心表 · 企业内唯一编码 · 字典项与分组多对多 · 发布整份不可变快照', 1)
    table(root, 'dictionary_type', 60, 210, 'draft',
          'UK (tenant_id, code)<br>draft_revision：草稿修订<br>effective_revision：生效状态修订', 86)
    table(root, 'dictionary_item', 650, 210, 'draft',
          'UK (tenant_id, dictionary_code, item_value)<br>UK (tenant_id, dictionary_code, id)<br>曾发布后的 item_value 不可更改', 86)
    table(root, 'dictionary_version', 1240, 210, 'published',
          'UK (tenant_id, dictionary_code, version_number)<br>status = PUBLISHED；快照不可变<br>版本归属为逻辑关联，无数据库外键', 86)
    table(root, 'dictionary_group', 60, 860, 'draft',
          'UK (tenant_id, dictionary_code, code)<br>UK (tenant_id, dictionary_code, id)<br>分组属于单个字典，code 曾发布后不可更改', 86)
    table(root, 'dictionary_group_member', 650, 860, 'draft',
          'UK (tenant_id, dictionary_code, group_id, item_id)<br>两组复合 FK 限定同企业、同字典<br>组内 sort_order 独立；删除组或项级联删成员', 86)
    edge(root, 'fk_summer_dictionary_tenant_type', 'dictionary_type', 'dictionary_item',
         '1 : 0..N', (1, .35), (0, .35))
    edge(root, 'fk_dictionary_group_type', 'dictionary_type', 'dictionary_group',
         '1 : 0..N　(tenant_id, code → dictionary_code)', (.5, 1), (.5, 0))
    edge(root, 'fk_dictionary_member_group', 'dictionary_group', 'dictionary_group_member',
         '1 : 0..N', (1, .5), (0, .5))
    edge(root, 'fk_dictionary_member_item', 'dictionary_item', 'dictionary_group_member',
         '1 : 0..N　(tenant_id, dictionary_code, id → item_id)', (.5, 1), (.5, 0))
    edge(root, 'logical-versions', 'dictionary_type', 'dictionary_version',
         '逻辑版本归属 1 : 0..N　(tenant_id, code → dictionary_code)', (.7, 0), (.5, 0),
         [(375, 155), (1465, 155)], True)
    note(root, 'snapshot-structure', 'snapshot_json：快照内容', [
        'name / publicReadable / enabled / formatVersion',
        'items[]：value、label、sortOrder、enabled、description',
        'groups[]：id、code、name、enabled、memberValues[]（有序）',
        '字典、项、分组和成员顺序一起发布；没有单独的版本项表或版本分组表。',
    ], 1240, 780, 450, 420)
    note(root, 'core-invariants', '模型规则与读图说明', [
        '分组是字典项的子集；同一项可属于多个分组。字典本身没有 parent_id 层级结构。',
        'published_version 按 (tenant_id, code, version_number) 逻辑定位生效快照；回滚切换该指针，保留当前草稿。',
        '蓝色长虚线 + 乌鸦脚 = 数据库外键（1 : 0..N）；灰色点线 + 箭头 = 逻辑关联，无数据库外键。',
    ], 60, 1235, 1630, 148, size=14)

    root = page('supporting', '覆盖配置与发布事件',
                '4 张支撑表 + 字典定义引用 · 组织覆盖优先于企业覆盖，再读取基础字典 · 事件与快照分离', 2)
    table(root, 'dictionary_type', 60, 210, 'draft',
          '本页只显示关联键与生效指针<br>完整字段见第 1 页', 70,
          subset=['tenant_id', 'code', 'published_version'], context=True)
    table(root, 'configuration_override', 650, 210, 'override',
          "字典切片：resource = 'dictionaries'<br>UK (tenant_id, resource, config_key, scope_id)<br>scope_id = '_tenant' 或组织 ID<br>version 是乐观锁版本，非历史修订号", 102)
    table(root, 'configuration_override_revision', 1240, 210, 'override',
          'UK (override_id, revision)<br>override_id → configuration_override.id<br>latest / published_revision 逻辑引用历史修订', 86)
    table(root, 'dictionary_outbox', 60, 860, 'event',
          'UK (tenant_id, dictionary_code, idempotency_key)<br>status：PENDING / PUBLISHED<br>幂等键可空；指纹 + 回执支持请求重放', 86)
    table(root, 'dictionary_notification_event', 650, 860, 'event',
          'event_id：自增 BIGINT，供增量订阅游标使用<br>与 Outbox 的 UUID 事件主键不同<br>无 outbox_id，也无到 Outbox 的外键', 86)
    edge(root, 'logical-overrides', 'dictionary_type', 'configuration_override',
         '逻辑 1 : 0..N', (1, .35), (0, .15), logical=True)
    edge(root, 'fk_configuration_override_revision', 'configuration_override', 'configuration_override_revision',
         '1 : 0..N<br>id → override_id', (1, .45), (0, .57))
    edge(root, 'logical-outbox', 'dictionary_type', 'dictionary_outbox',
         '逻辑 1 : 0..N<br>tenant_id + dictionary_code<br>版本操作写入', (.5, 1), (.5, 0), logical=True)
    edge(root, 'logical-notifications', 'dictionary_type', 'dictionary_notification_event',
         '逻辑 1 : 0..N', (1, .78), (.5, 0),
         [(580, 379), (580, 790), (875, 790)], True)
    note(root, 'support-invariants', '覆盖与事件的边界', [
        '配置覆盖表供多类配置共享；只有 resource = dictionaries 的记录属于字典模型。',
        '覆盖值保留独立修订历史；优先级：当前组织 → 企业（_tenant）→ 基础发布版本。',
        '发布、停用、回滚操作写入 Outbox 与通知表；二者是并列记录，不是父子表。覆盖事件也可触发通知。',
        '蓝色长虚线 + 乌鸦脚 = 真实 FK；灰色点线 + 箭头 = 逻辑关联。权限、菜单、审计使用通用模块，未展开。',
    ], 1240, 860, 450, 465)
    ET.indent(MX, space='  ')
    ET.ElementTree(MX).write(HERE / 'dictionary-data-model.drawio', encoding='utf-8', xml_declaration=True)


def write_readme():
    lines = ['# 字典管理数据模型', '',
             '基于 `summer-next-4` 分支提交 `' + DATA['source_commit'] + '`，'
             '以及 `summer_next4_dev` 的实际表结构（2026-11-15）。仅提取结构，无业务记录。', '',
             '- 核心：字典定义、字典项、分组、分组成员、发布版本，共 5 表。',
             '- 支撑：配置覆盖、覆盖修订、事务 Outbox、通知事件，共 4 表。',
             '- 共 93 个字段，5 组真实数据库外键；逻辑引用另行标注。', '',
             '## 文件', '',
             '- [可编辑 draw.io 模型图](dictionary-data-model.drawio)',
             '- [核心模型预览](dictionary-core.png)',
             '- [覆盖与事件预览](dictionary-supporting.png)',
             '- `schema.sql`：9 表实际 DDL；`schema-metadata.json`：字段、键、索引；'
             '`dictionary.ir.json`：按复合外键归一化的语义模型。', '',
             '## 关键语义', '',
             '1. 字典编码在企业内唯一；字典项值在企业、字典内唯一。所有成员引用带企业和字典编码，防止跨字典挂接。',
             '2. 分组是单个字典内的项子集，项与分组通过成员表多对多关联；'
             '成员顺序独立于字典默认顺序。成员的组或项被删除时，数据库级联删除相应成员。',
             '3. 已发布过的项值及分组编码不可更改。字典没有父子层级字段；前端联动配置不是字典项树。',
             '4. 字典定义、项、分组、成员表承载可编辑草稿。发布版本在 `snapshot_json` 中保存完整不可变快照，'
             '包括字典属性、items、groups 与成员顺序；不存在单独的版本项或版本分组表。',
             '5. `draft_revision` 控制草稿编辑，`effective_revision` 控制生效状态变更；'
             '`published_version` 逻辑定位当前生效的版本号。回滚切换生效指针并保留草稿。',
             '6. 覆盖配置为共享模型，字典只取 `resource=dictionaries`、`config_key=字典编码` 的切片。'
             '`scope_id=_tenant` 为企业覆盖，其他 scope_id 为组织；有效读取顺序为组织、企业、基础发布版本。',
             '7. `configuration_override.version` 是乐观锁版本；latest_revision / published_revision '
             '是覆盖历史修订号，与基础字典发布版本号不同。',
             '8. 版本操作分别写 Outbox 与通知事件；二者没有直接外键。Outbox 使用 UUID 主键、幂等键和请求回执，'
             '通知事件使用自增 BIGINT 游标。覆盖事件也能产生字典通知。',
             '9. 权限、菜单和审计属于通用模块，本图未展开其数据表。', '',
             '## 实际数据库外键', '',
             '| 外键名 | 子表字段 | 父表字段 |', '|---|---|---|']
    for name, rows in FK.items():
        lines.append(f"| `{name}` | `{rows[0][0]}` ({', '.join(r[2] for r in rows)}) | "
                     f"`{rows[0][4]}` ({', '.join(r[5] for r in rows)}) |")
    lines += ['', '以下逐表列出全部字段。`?` 表示允许 NULL；类型以 MySQL 物理类型为准。', '']
    for table in DATA['tables']:
        lines += ['## ' + TITLES[table.removeprefix(PREFIX)], '', '`' + table + '`', '',
                  '| 字段 | 类型 | 可空 | 键标记 | 含义 |', '|---|---|---|---|---|']
        pk = {r[2] for r in DATA['keys'] if r[0] == table and r[1] == 'PRIMARY'}
        fk = {r[2] for r in DATA['keys'] if r[0] == table and r[4]}
        for row in columns(table):
            field = row[1]
            marker = ', '.join(k for k, yes in [('PK', field in pk), ('FK', field in fk)] if yes)
            lines.append(f"| `{field}` | `{row[2]}` | {'是' if row[3]=='YES' else '否'} | {marker} | {DESCRIPTIONS[field]} |")
        lines += ['', '唯一约束：', '']
        for name, rows in indexes(table).items():
            lines.append('- `' + name + ' (' + ', '.join(r[4] for r in rows) + ')`')
        lines.append('')
    lines += ['## 实现来源', '',
              '- `summer-sample/src/main/java/com/abluepoint/summer/sample/dictionary/` 下实体、DictionaryGroups、DictionaryVersionService、DictionaryNotificationService。',
              '- `summer-sample/src/main/java/com/abluepoint/summer/sample/configuration/` 下覆盖实体与 ConfigurationOverrideService。',
              '- `summer-sample/src/main/resources/db/migration/mysql/` 下 V23、V27、V30、V32、V33、V40、V41、V42 等迁移；最终类型和约束以提取的实际 DDL 为准。', '']
    (HERE / 'README.md').write_text('\n'.join(lines))


if __name__ == '__main__':
    write_ir()
    write_drawio()
    write_readme()
    print('Generated 2 editable pages, 9 tables, 93 columns, 5 composite FK constraints.')
