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
import AppLoading from '@/components/AppLoading';
import {
  CatalogDifference,
  CatalogHistory,
  InterfaceResource,
  InterfaceResourceApiError,
  InterfaceResourceCatalog,
  InterfaceResourcePage,
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
  const [catalog, setCatalog] = useState<InterfaceResourcePage | null>(null);
  const [history, setHistory] = useState<CatalogHistory>(emptyHistory);
  const [snapshot, setSnapshot] = useState<InterfaceResourcePage | null>(null);
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
    compareGeneration = useRef(0),
    snapshotGeneration = useRef(0);
  const comparePending = useRef(false);
  const catalogRevision = useRef<number>();
  const historyValue = useRef<CatalogHistory>();
  const fail = useCallback((failure: unknown) => {
    const next =
      failure instanceof InterfaceResourceApiError
        ? failure
        : new InterfaceResourceApiError(0, '', 'Catalog request failed', '');
    setError(next);
    if (next.status === 401 || next.status === 403) {
      generation.current++;
      historyGeneration.current++;
      compareGeneration.current++;
      snapshotGeneration.current++;
      catalogRevision.current = undefined;
      historyValue.current = undefined;
      setLoading(false);
      setHistoryLoading(false);
      setComparing(false);
      setViewing(null);
      setCatalog(null);
      setHistory(emptyHistory);
      setDifference(null);
      setSnapshot(null);
    }
  }, []);
  const load = useCallback(
    async (page = 0, size = 10, refresh = false) => {
      const current = ++generation.current;
      setLoading(true);
      setError(null);
      try {
        const next = await listInterfaceResources(
          page,
          size,
          refresh ? { refresh: true } : { revision: catalogRevision.current }
        );
        if (current !== generation.current) return;
        const initialize = catalogRevision.current == null || refresh;
        catalogRevision.current = next.revision;
        setCatalog(next);
        if (initialize) {
          compareGeneration.current++;
          snapshotGeneration.current++;
          setViewing(null);
          setSnapshot(null);
          setDifference(null);
          setTo(next.revision);
          setFrom(
            historyValue.current
              ? historyValue.current.items.find(
                  (row) => row.revision !== next.revision
                )?.revision || next.revision
              : undefined
          );
        }
      } catch (failure) {
        if (current === generation.current) fail(failure);
      } finally {
        if (current === generation.current) setLoading(false);
      }
    },
    [fail]
  );
  const historyPage = useCallback(
    async (page = 1, size = 20) => {
      const current = ++historyGeneration.current;
      setHistoryLoading(true);
      try {
        const next = await listInterfaceResourceHistory(page - 1, size);
        if (current !== historyGeneration.current) return;
        historyValue.current = next;
        setHistory(next);
        if (catalogRevision.current != null)
          setFrom(
            (previous) =>
              previous ??
              next.items.find((row) => row.revision !== catalogRevision.current)
                ?.revision ??
              catalogRevision.current
          );
      } catch (failure) {
        if (current === historyGeneration.current) fail(failure);
      } finally {
        if (current === historyGeneration.current) setHistoryLoading(false);
      }
    },
    [fail]
  );
  useEffect(() => {
    const requests = [
      generation,
      historyGeneration,
      compareGeneration,
      snapshotGeneration,
    ];
    void load();
    void historyPage();
    return () => {
      requests.forEach((request) => {
        request.current++;
      });
    };
  }, [load, historyPage]);
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
  const showRevision = async (revision: number, page = 0, size = 10) => {
    setViewing(revision);
    setError(null);
    const current = ++snapshotGeneration.current;
    try {
      const next = await getInterfaceResourceRevision(revision, page, size);
      if (current === snapshotGeneration.current) setSnapshot(next);
    } catch (failure) {
      if (current === snapshotGeneration.current) fail(failure);
    } finally {
      if (current === snapshotGeneration.current) setViewing(null);
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
  const resourceTable = (
    value: InterfaceResourcePage,
    onChange: (page: number, size: number) => void,
    busy: boolean
  ) => (
    <Table<InterfaceResource>
      rowKey={(row) =>
        `${row.contextId}:${row.httpMethod}:${
          row.normalizedPath
        }:${JSON.stringify(row.conditions)}:${row.handler}`
      }
      data={value.items}
      loading={busy}
      pagination={{
        current: (value.page || 0) + 1,
        pageSize: value.size || 10,
        total: value.total ?? value.items.length,
        showTotal: true,
        sizeCanChange: true,
        sizeOptions: [10, 20, 50, 100],
        onChange,
      }}
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
        <Button
          size="small"
          loading={loading}
          onClick={() => {
            void load(0, catalog?.size || 10, true);
            void historyPage();
          }}
        >
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
          {resourceTable(
            catalog,
            (page, size) => void load(page - 1, size),
            loading
          )}
        </>
      )}
      {!catalog && loading && <AppLoading fullScreen={false} />}
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
              onChange: (page, size) => void historyPage(page, size),
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
        onCancel={() => {
          snapshotGeneration.current++;
          setSnapshot(null);
          setViewing(null);
        }}
        style={{ width: '90vw' }}
      >
        {snapshot && (
          <>
            {incomplete(snapshot)}
            {resourceTable(
              snapshot,
              (page, size) =>
                void showRevision(snapshot.revision, page - 1, size),
              viewing != null
            )}
          </>
        )}
      </Modal>
    </Card>
  );
}
