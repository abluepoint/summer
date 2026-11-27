import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert,
  Input,
  Button,
  Card,
  Descriptions,
  Modal,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from '@arco-design/web-react';
import useLocale from '@/utils/useLocale';
import AppLoading from '@/components/AppLoading';
import {
  CatalogDifference,
  CatalogFilters,
  refreshInterfaceService,
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
  const [detail, setDetail] = useState<InterfaceResource>();
  const detailTrigger = useRef<HTMLButtonElement | null>(null);
  const [draft, setDraft] = useState<CatalogFilters>({});
  const filters = useRef<CatalogFilters>({});
  const [serviceCode, setServiceCode] = useState('');
  const [refreshOpen, setRefreshOpen] = useState(false);
  const [refreshError, setRefreshError] = useState('');
  const [saving, setSaving] = useState(false);
  const saveGeneration = useRef(0);
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
      saveGeneration.current++;
      setRefreshOpen(false);
      setSaving(false);
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
      setDetail(undefined);
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
        const next = await listInterfaceResources(page, size, {
          directory: true,
          revision: refresh ? undefined : catalogRevision.current,
          ...filters.current,
        });
        if (current !== generation.current) return;
        const initialize = catalogRevision.current == null || refresh;
        catalogRevision.current = next.revision;
        setCatalog(next);
        if (initialize) {
          compareGeneration.current++;
          snapshotGeneration.current++;
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
      saveGeneration,
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
  const refreshService = async () => {
    if (saving || !serviceCode) return;
    const save = ++saveGeneration.current;
    setRefreshError('');
    setSaving(true);
    try {
      const result = await refreshInterfaceService(serviceCode);
      if (save !== saveGeneration.current) return;
      filters.current = { serviceCode: result.serviceCode };
      setDraft(filters.current);
      setRefreshOpen(false);
      await Promise.all([load(0, catalog?.size || 10, true), historyPage()]);
    } catch (failure) {
      if (save !== saveGeneration.current) return;
      if (
        failure instanceof InterfaceResourceApiError &&
        failure.status === 401
      )
        fail(failure);
      else if (failure instanceof InterfaceResourceApiError) {
        setRefreshError(
          [
            failure.status === 403
              ? t['catalog.refreshForbidden']
              : failure.message,
            failure.requestId,
          ]
            .filter(Boolean)
            .join(' · ')
        );
      } else setRefreshError(t['catalog.refreshFailed']);
    } finally {
      if (save === saveGeneration.current) setSaving(false);
    }
  };
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
    busy: boolean,
    showService = false
  ) => (
    <Table<InterfaceResource>
      rowKey={(row) =>
        `${JSON.stringify(row.serviceCode)}:${row.contextId}:${
          row.httpMethod
        }:${row.normalizedPath}:${JSON.stringify(row.conditions)}:${
          row.handler
        }`
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
      scroll={{ x: showService ? 1310 : 900 }}
      columns={[
        ...(showService
          ? [
              {
                title: t['catalog.serviceName'],
                dataIndex: 'serviceName',
                width: 150,
                render: (_, row: InterfaceResource) => row.serviceName || '—',
              },
              {
                title: t['catalog.revision'],
                dataIndex: 'revision',
                width: 80,
                render: (_, row: InterfaceResource) => row.revision ?? '—',
              },
              {
                title: t['catalog.lastSyncedAt'],
                dataIndex: 'lastSyncedAt',
                width: 180,
                render: (_, row: InterfaceResource) =>
                  row.lastSyncedAt ? (
                    <time dateTime={row.lastSyncedAt}>
                      {new Date(row.lastSyncedAt).toLocaleString()}
                    </time>
                  ) : (
                    '—'
                  ),
              },
            ]
          : []),
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
        {
          title: t['interfaceResources.type'],
          dataIndex: 'resourceType',
          width: 130,
        },
        {
          title: t['interfaceResources.module'],
          dataIndex: 'module',
          width: 100,
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
        {
          title: t['catalog.action'],
          width: 80,
          fixed: 'right',
          render: (_, row) => (
            <Button
              type="text"
              size="small"
              onClick={(event) => {
                detailTrigger.current =
                  event.currentTarget as HTMLButtonElement;
                setDetail(row);
              }}
            >
              {t['catalog.details']}
            </Button>
          ),
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
  const conditions = Object.entries(detail?.conditions || {});
  const closeDetail = () => {
    setDetail(undefined);
    detailTrigger.current?.focus();
  };
  return (
    <Card
      title={t['interfaceResources.title']}
      extra={
        <Button
          size="small"
          loading={loading}
          disabled={!catalog}
          onClick={() => {
            setServiceCode(filters.current.serviceCode || '');
            setRefreshError('');
            setRefreshOpen(true);
            void load(catalog.page, catalog.size);
          }}
        >
          {t['catalog.sync']}
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
          action={
            !catalog && (
              <Button
                size="small"
                onClick={() => {
                  void load();
                  void historyPage();
                }}
              >
                {t['catalog.retry']}
              </Button>
            )
          }
          style={{ marginBottom: 16 }}
        />
      )}
      {!catalog && loading && <AppLoading fullScreen={false} />}
      {catalog && (
        <Tabs defaultActiveTab="current">
          <Tabs.TabPane key="current" title={t['catalog.current']}>
            {incomplete(catalog)}
            <form
              onSubmit={(event) => {
                event.preventDefault();
                filters.current = draft;
                void load(0, catalog.size);
              }}
            >
              <Space wrap style={{ marginBottom: 16 }}>
                <Space>
                  <label htmlFor="catalog-path">
                    {t['interfaceResources.path']}
                  </label>
                  <Input
                    id="catalog-path"
                    style={{ width: 220 }}
                    allowClear
                    value={draft.path || ''}
                    onChange={(path) => setDraft({ ...draft, path })}
                  />
                </Space>
                <Space>
                  <span>{t['catalog.protection']}</span>
                  <Select
                    aria-label={t['catalog.protection']}
                    style={{ width: 180 }}
                    allowClear
                    placeholder={t['catalog.all']}
                    value={draft.policy}
                    options={(catalog.policies || []).map((value) => ({
                      value,
                      label: t['catalog.' + value] || value,
                    }))}
                    onChange={(policy) => setDraft({ ...draft, policy })}
                  />
                </Space>
                <Space>
                  <span>{t['catalog.serviceName']}</span>
                  <Select
                    aria-label={t['catalog.serviceName']}
                    style={{ width: 180 }}
                    allowClear
                    showSearch
                    placeholder={t['catalog.all']}
                    value={draft.serviceCode}
                    options={catalog.services || []}
                    onChange={(serviceCode) =>
                      setDraft({ ...draft, serviceCode })
                    }
                  />
                </Space>
                <Button htmlType="submit">{t['catalog.filter']}</Button>
              </Space>
            </form>
            {resourceTable(
              catalog,
              (page, size) => void load(page - 1, size),
              loading,
              true
            )}
          </Tabs.TabPane>
          <Tabs.TabPane key="history" title={t['catalog.history']}>
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
          </Tabs.TabPane>
        </Tabs>
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
      {detail && (
        <Modal
          visible
          focusLock
          autoFocus
          title={t['catalog.detailTitle']}
          style={{ width: 680, maxWidth: 'calc(100vw - 32px)' }}
          onCancel={closeDetail}
          footer={<Button onClick={closeDetail}>{t['catalog.close']}</Button>}
        >
          <Descriptions
            border
            column={1}
            labelStyle={{ width: 130 }}
            valueStyle={{ overflowWrap: 'anywhere', whiteSpace: 'pre-wrap' }}
            data={[
              {
                label: t['interfaceResources.method'],
                value: detail.httpMethod,
              },
              {
                label: t['interfaceResources.path'],
                value: detail.normalizedPath,
              },
              {
                label: t['catalog.serviceName'],
                value: detail.serviceName || '—',
              },
              {
                label: t['interfaceResources.type'],
                value: detail.resourceType,
              },
              { label: t['interfaceResources.module'], value: detail.module },
              {
                label: t['catalog.protection'],
                value:
                  t['catalog.' + detail.configurationPolicy] ||
                  detail.configurationPolicy,
              },
              {
                label: t['interfaceResources.configurable'],
                value:
                  t[
                    detail.configurable
                      ? 'interfaceResources.yes'
                      : 'interfaceResources.no'
                  ],
              },
              { label: t['catalog.context'], value: detail.contextId },
              { label: t['interfaceResources.handler'], value: detail.handler },
              {
                label: t['interfaceResources.sensitivity'],
                value: detail.sensitiveLevel,
              },
              {
                label: t['catalog.conditions'],
                value: conditions.length
                  ? conditions.map(([key, values]) => (
                      <div key={key}>
                        {key}: {values.join(', ')}
                      </div>
                    ))
                  : t['catalog.noConditions'],
              },
            ]}
          />
        </Modal>
      )}
      {refreshOpen && (
        <Modal
          visible
          title={t['catalog.sync']}
          okText={t['catalog.syncConfirm']}
          confirmLoading={saving}
          okButtonProps={{
            disabled: !catalog?.services?.some(
              (item) => item.value === serviceCode && item.enabled
            ),
          }}
          onOk={() => void refreshService()}
          onCancel={() => {
            if (!saving) setRefreshOpen(false);
          }}
        >
          {refreshError && (
            <Alert
              type="error"
              content={refreshError}
              style={{ marginBottom: 16 }}
            />
          )}
          <label htmlFor="catalog-service-name">
            {t['catalog.serviceName']}
          </label>
          <Select
            id="catalog-service-name"
            aria-describedby="catalog-service-help"
            disabled={saving}
            aria-label={t['catalog.serviceName']}
            style={{ width: '100%' }}
            showSearch
            placeholder={t['catalog.chooseService']}
            options={(catalog?.services || []).filter((item) => item.enabled)}
            value={serviceCode || undefined}
            onChange={setServiceCode}
          />
          <Typography.Paragraph id="catalog-service-help" type="secondary">
            {t['catalog.serviceHelp']}
          </Typography.Paragraph>
        </Modal>
      )}
    </Card>
  );
}
