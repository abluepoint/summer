import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import {
  CatalogDifference,
  CatalogHistory,
  InterfaceResource,
  InterfaceResourceApiError,
  InterfaceResourceCatalog,
  listInterfaceResources,
  listInterfaceResourceHistory,
  getInterfaceResourceRevision,
  compareInterfaceResourceRevisions,
} from './api';
import locale from './locale';

type Change = {
  kind: string;
  resource: InterfaceResource;
  before?: InterfaceResource;
  after?: InterfaceResource;
};
const emptyHistory: CatalogHistory = { items: [], total: 0, page: 0, size: 20 };
export default function InterfaceResourcesPage() {
  const t = { ...useLocale(), ...useLocale(locale) };
  const [catalog, setCatalog] = useState<InterfaceResourceCatalog | null>(null);
  const [history, setHistory] = useState<CatalogHistory>(emptyHistory);
  const [snapshot, setSnapshot] = useState<InterfaceResourceCatalog | null>(
    null
  );
  const [difference, setDifference] = useState<CatalogDifference | null>(null);
  const [error, setError] = useState<InterfaceResourceApiError | null>(null);
  const [loading, setLoading] = useState(false);
  const [comparing, setComparing] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [viewing, setViewing] = useState<number | null>(null);
  const [from, setFrom] = useState<number>();
  const [to, setTo] = useState<number>();
  const generation = useRef(0),
    historyGeneration = useRef(0),
    compareGeneration = useRef(0);
  const comparePending = useRef(false),
    viewPending = useRef(false);
  const fail = useCallback((failure: unknown) => {
    const next =
      failure instanceof InterfaceResourceApiError
        ? failure
        : new InterfaceResourceApiError(0, '', 'Catalog request failed', '');
    setError(next);
    if (next.status === 401 || next.status === 403) {
      setCatalog(null);
      setHistory(emptyHistory);
      setDifference(null);
      setSnapshot(null);
    }
  }, []);
  const load = useCallback(async () => {
    const current = ++generation.current;
    historyGeneration.current++;
    compareGeneration.current++;
    setLoading(true);
    setError(null);
    try {
      const [next, revisions] = await Promise.all([
        listInterfaceResources(),
        listInterfaceResourceHistory(),
      ]);
      if (current !== generation.current) return;
      setCatalog(next);
      setHistory(revisions);
      setSnapshot(null);
      setDifference(null);
      setTo(next.revision);
      setFrom(
        revisions.items.find((row) => row.revision !== next.revision)
          ?.revision || next.revision
      );
    } catch (failure) {
      if (current === generation.current) fail(failure);
    } finally {
      if (current === generation.current) setLoading(false);
    }
  }, [fail]);
  useEffect(() => {
    void load();
    return () => {
      generation.current++;
      historyGeneration.current++;
      compareGeneration.current++;
    };
  }, [load]);
  const compare = async () => {
    if (comparePending.current || from == null || to == null || from === to)
      return;
    comparePending.current = true;
    setComparing(true);
    setError(null);
    const current = ++compareGeneration.current;
    try {
      const next = await compareInterfaceResourceRevisions(from, to);
      if (current === compareGeneration.current) setDifference(next);
    } catch (failure) {
      if (current === compareGeneration.current) fail(failure);
    } finally {
      comparePending.current = false;
      setComparing(false);
    }
  };
  const showRevision = async (revision: number) => {
    if (viewPending.current) return;
    viewPending.current = true;
    setViewing(revision);
    setError(null);
    const current = generation.current;
    try {
      const next = await getInterfaceResourceRevision(revision);
      if (current === generation.current) setSnapshot(next);
    } catch (failure) {
      if (current === generation.current) fail(failure);
    } finally {
      viewPending.current = false;
      setViewing(null);
    }
  };
  const historyPage = async (page: number) => {
    const current = ++historyGeneration.current;
    setHistoryLoading(true);
    try {
      const next = await listInterfaceResourceHistory(page - 1);
      if (current === historyGeneration.current) setHistory(next);
    } catch (failure) {
      if (current === historyGeneration.current) fail(failure);
    } finally {
      if (current === historyGeneration.current) setHistoryLoading(false);
    }
  };
  const incomplete = (value: InterfaceResourceCatalog) =>
    value.complete === false && (
      <Alert
        type="warning"
        title={t['catalog.incomplete']}
        content={
          <>
            <p>{t['catalog.incompleteDetail']}</p>
            {value.issues.map((issue, index) => (
              <p key={index}>
                {issue.contextId}: {issue.source} — {issue.code}
              </p>
            ))}
          </>
        }
        style={{ marginBottom: 16 }}
      />
    );
  const resourceTable = (items: InterfaceResource[]) => (
    <Table<InterfaceResource>
      rowKey={(row) =>
        `${row.contextId}:${row.httpMethod}:${
          row.normalizedPath
        }:${JSON.stringify(row.conditions)}:${row.handler}`
      }
      data={items}
      pagination={{ pageSize: 10 }}
      scroll={{ x: 1500 }}
      columns={[
        {
          title: t['interfaceResources.method'],
          dataIndex: 'httpMethod',
          width: 80,
        },
        {
          title: t['interfaceResources.path'],
          dataIndex: 'normalizedPath',
          width: 240,
        },
        { title: t['catalog.context'], dataIndex: 'contextId', width: 120 },
        {
          title: t['interfaceResources.type'],
          dataIndex: 'resourceType',
          width: 130,
        },
        {
          title: t['interfaceResources.handler'],
          dataIndex: 'handler',
          width: 260,
          ellipsis: true,
        },
        {
          title: t['interfaceResources.module'],
          dataIndex: 'module',
          width: 100,
        },
        {
          title: t['interfaceResources.permission'],
          dataIndex: 'permissionCode',
          width: 240,
          ellipsis: true,
        },
        {
          title: t['interfaceResources.sensitivity'],
          dataIndex: 'sensitiveLevel',
          width: 110,
        },
        {
          title: t['catalog.conditions'],
          render: (_, row) => JSON.stringify(row.conditions || {}),
          width: 180,
        },
        {
          title: t['catalog.protection'],
          render: (_, row) =>
            t[`catalog.${row.configurationPolicy}`] || row.configurationPolicy,
          width: 180,
        },
        {
          title: t['interfaceResources.configurable'],
          render: (_, row) =>
            t[
              row.configurable
                ? 'interfaceResources.yes'
                : 'interfaceResources.no'
            ],
          width: 90,
        },
      ]}
    />
  );
  const changes: Change[] = difference
    ? [
        ...difference.added.map((resource) => ({
          kind: 'ADDED',
          resource,
          after: resource,
        })),
        ...difference.removed.map((resource) => ({
          kind: 'REMOVED',
          resource,
          before: resource,
        })),
        ...difference.changed.map((row) => ({
          kind: row.kind,
          resource: row.after,
          before: row.before,
          after: row.after,
        })),
      ]
    : [];
  const revisionNumbers = Array.from(
    new Set(
      [...history.items.map((row) => row.revision), from, to].filter(
        (value) => value != null
      )
    )
  );
  return (
    <Card
      title={t['interfaceResources.title']}
      extra={
        <Button size="small" loading={loading} onClick={() => void load()}>
          {t['interfaceResources.refresh']}
        </Button>
      }
    >
      {error && (
        <Alert
          type="error"
          title={
            t[
              error.status === 403
                ? 'interfaceResources.forbidden'
                : 'interfaceResources.error'
            ]
          }
          content={`${error.message}${
            error.requestId ? ` (${error.requestId})` : ''
          }`}
          style={{ marginBottom: 16 }}
        />
      )}
      {catalog && (
        <>
          {incomplete(catalog)}
          <Typography.Paragraph>
            {t['catalog.current']}: {catalog.revision}
          </Typography.Paragraph>
          {resourceTable(catalog.items)}
        </>
      )}
      {catalog && (
        <>
          <Typography.Title heading={5}>
            {t['catalog.history']}
          </Typography.Title>
          <Typography.Paragraph>
            {t['catalog.replacementNote']}
          </Typography.Paragraph>
          <Space style={{ marginBottom: 16 }} wrap>
            <Select
              aria-label={t['catalog.from']}
              style={{ width: 180 }}
              value={from}
              options={revisionNumbers.map((value) => ({
                label: `${t['catalog.revision']} ${value}`,
                value,
              }))}
              onChange={(value: number) => {
                compareGeneration.current++;
                setFrom(value);
                setDifference(null);
              }}
            />
            <Select
              aria-label={t['catalog.to']}
              style={{ width: 180 }}
              value={to}
              options={revisionNumbers.map((value) => ({
                label: `${t['catalog.revision']} ${value}`,
                value,
              }))}
              onChange={(value: number) => {
                compareGeneration.current++;
                setTo(value);
                setDifference(null);
              }}
            />
            <Button
              loading={comparing}
              disabled={from == null || to == null || from === to}
              onClick={() => void compare()}
            >
              {t['catalog.compare']}
            </Button>
          </Space>
          {difference && (
            <Table<Change>
              rowKey={(row) =>
                `${row.kind}:${row.resource.contextId}:${
                  row.resource.httpMethod
                }:${row.resource.normalizedPath}:${JSON.stringify(
                  row.resource.conditions
                )}`
              }
              data={changes}
              pagination={false}
              scroll={{ x: 1100 }}
              columns={[
                {
                  title: t['catalog.change'],
                  render: (_, row) => (
                    <Tag
                      color={
                        row.kind === 'REMOVED'
                          ? 'red'
                          : row.kind === 'REPLACED'
                          ? 'orange'
                          : 'blue'
                      }
                    >
                      {t[`catalog.${row.kind}`]}
                    </Tag>
                  ),
                },
                {
                  title: t['interfaceResources.method'],
                  render: (_, row) => row.resource.httpMethod,
                },
                {
                  title: t['interfaceResources.path'],
                  render: (_, row) => row.resource.normalizedPath,
                },
                {
                  title: t['catalog.before'],
                  render: (_, row) => row.before?.handler || '—',
                },
                {
                  title: t['catalog.after'],
                  render: (_, row) => row.after?.handler || '—',
                },
                {
                  title: t['catalog.protection'],
                  render: (_, row) =>
                    t[`catalog.${row.resource.configurationPolicy}`] ||
                    row.resource.configurationPolicy,
                },
              ]}
            />
          )}
          <Table
            rowKey="revision"
            data={history.items}
            loading={historyLoading}
            pagination={{
              current: (history.page || 0) + 1,
              pageSize: history.size || 20,
              total: history.total ?? history.items.length,
              onChange: (page) => void historyPage(page),
            }}
            columns={[
              { title: t['catalog.revision'], dataIndex: 'revision' },
              { title: t['catalog.time'], dataIndex: 'createdAt' },
              { title: t['catalog.count'], dataIndex: 'resourceCount' },
              {
                title: t['catalog.action'],
                render: (_, row) => (
                  <Button
                    size="small"
                    aria-label={`${t['catalog.view']} ${row.revision}`}
                    loading={viewing === row.revision}
                    onClick={() => void showRevision(row.revision)}
                  >
                    {t['catalog.view']} {row.revision}
                  </Button>
                ),
              },
            ]}
          />
        </>
      )}
      <Modal
        title={`${t['catalog.view']} ${snapshot?.revision || ''}`}
        visible={!!snapshot}
        footer={null}
        onCancel={() => setSnapshot(null)}
        style={{ width: '90vw' }}
      >
        {snapshot && (
          <>
            {incomplete(snapshot)}
            {resourceTable(snapshot.items)}
          </>
        )}
      </Modal>
    </Card>
  );
}
