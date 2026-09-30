import React, { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  Input,
  Message,
  Pagination,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from '@arco-design/web-react';
import { useHistory, useLocation } from 'react-router-dom';
import useLocale from '@/utils/useLocale';
import clipboard from '@/utils/clipboard';
import {
  Customer,
  CustomerPage,
  SummerApiError,
  getCustomer,
  listCustomers,
} from './api';
import {
  detailPath,
  detailUsername,
  listSearch,
  readListState,
} from './contract.mjs';

type Mode = 'drawer' | 'modal' | 'page';

function ErrorNotice({
  error,
  retry,
}: {
  error: SummerApiError;
  retry: () => void;
}) {
  const t = useLocale();
  const title =
    error.status === 401
      ? t['customer.error.401']
      : error.status === 403
      ? t['customer.error.403']
      : error.status === 404
      ? t['customer.error.404']
      : t['customer.error.server'];
  return (
    <Alert
      type="error"
      title={title}
      content={
        <Space direction="vertical">
          {error.message && <Typography.Text>{error.message}</Typography.Text>}
          {error.requestId && (
            <Space>
              <Typography.Text>requestId: {error.requestId}</Typography.Text>
              <Button
                size="mini"
                onClick={() =>
                  clipboard(error.requestId).catch(() =>
                    Message.error(t['customer.error.copyFailed'])
                  )
                }
              >
                {t['customer.error.copyId']}
              </Button>
            </Space>
          )}
          {error.status !== 401 && (
            <Button size="mini" onClick={retry}>
              {t['customer.retry']}
            </Button>
          )}
        </Space>
      }
    />
  );
}

export default function CustomerScreen({ mode }: { mode: Mode }) {
  const t = useLocale();
  const history = useHistory();
  const location = useLocation();
  const state = readListState(location.search);
  const [compact, setCompact] = useState(window.innerWidth <= 768);
  const detail = detailUsername(location.pathname, mode);
  const isDetailRoute = location.pathname !== `/customer/${mode}`;
  const [lookup, setLookup] = useState('');
  const [lookupError, setLookupError] = useState(false);
  const [page, setPage] = useState<CustomerPage | null>(null);
  const [customer, setCustomer] = useState<Customer | null>(null);
  const [error, setError] = useState<SummerApiError | null>(null);
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    const update = () => setCompact(window.innerWidth <= 768);
    window.addEventListener('resize', update);
    return () => window.removeEventListener('resize', update);
  }, []);

  useEffect(() => {
    const requestedDetail = detailUsername(location.pathname, mode);
    const requestedState = readListState(location.search);
    if (location.pathname !== `/customer/${mode}` && !requestedDetail) {
      setError(
        new SummerApiError({
          status: 404,
          code: '',
          message: '',
          violations: [],
          requestId: '',
          timestamp: '',
        })
      );
      setLoading(false);
      return;
    }
    let live = true;
    setLoading(true);
    setError(null);
    const request = requestedDetail
      ? getCustomer(requestedDetail)
      : listCustomers(requestedState);
    request
      .then((result) => {
        if (!live) return;
        if (requestedDetail) setCustomer(result as Customer);
        else setPage(result as CustomerPage);
      })
      .catch((failure) => {
        if (!live) return;
        const apiError =
          failure instanceof SummerApiError
            ? failure
            : new SummerApiError({
                status: 0,
                code: '',
                message: '',
                violations: [],
                requestId: '',
                timestamp: '',
              });
        setError(apiError);
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [mode, location.pathname, location.search, revision]);

  function changeState(changes: Partial<typeof state>) {
    history.push({
      pathname: `/customer/${mode}`,
      search: listSearch({ ...state, ...changes }),
    });
  }

  function openUsername() {
    const value = lookup.trim();
    try {
      history.push(detailPath(mode, value, location.search));
    } catch {
      setLookupError(true);
    }
  }

  const columns = [
    {
      title: t['customer.username'],
      dataIndex: 'username',
      render: (value: string) => (
        <Button
          type="text"
          onClick={() => history.push(detailPath(mode, value, location.search))}
        >
          {value}
        </Button>
      ),
    },
    { title: t['customer.email'], dataIndex: 'email' },
    { title: t['customer.name'], dataIndex: 'displayName' },
    {
      title: t['customer.active'],
      dataIndex: 'active',
      render: (value: boolean) => (
        <Tag color={value ? 'green' : 'gray'}>
          {value ? t['customer.yes'] : t['customer.no']}
        </Tag>
      ),
    },
  ];

  return (
    <Card title={detail ? t['customer.detail'] : t['customer.list']}>
      {isDetailRoute && (
        <Button
          style={{ marginBottom: 16 }}
          onClick={() => history.push(`/customer/${mode}${location.search}`)}
        >
          {t['customer.back']}
        </Button>
      )}
      {loading && <Spin />}
      {!loading && error && (
        <ErrorNotice
          error={error}
          retry={() => setRevision((value) => value + 1)}
        />
      )}
      {!loading && !error && detail && customer && (
        <Descriptions
          column={1}
          border
          data={[
            { label: t['customer.username'], value: customer.username },
            { label: t['customer.email'], value: customer.email },
            { label: t['customer.name'], value: customer.displayName },
            {
              label: t['customer.active'],
              value: (
                <Tag color={customer.active ? 'green' : 'gray'}>
                  {customer.active ? t['customer.yes'] : t['customer.no']}
                </Tag>
              ),
            },
          ]}
        />
      )}
      {!isDetailRoute && !error && (
        <>
          <Space wrap style={{ marginBottom: 16 }}>
            <Input
              value={lookup}
              onChange={(value) => {
                setLookup(value);
                setLookupError(false);
              }}
              onPressEnter={openUsername}
              placeholder={t['customer.lookup']}
              style={{ width: 190 }}
            />
            <Button onClick={openUsername}>{t['customer.find']}</Button>
            <Select
              value={state.active || 'all'}
              onChange={(active) =>
                changeState({ active: active === 'all' ? '' : active, page: 0 })
              }
              style={{ width: 120 }}
            >
              <Select.Option value="all">{t['customer.all']}</Select.Option>
              <Select.Option value="true">{t['customer.yes']}</Select.Option>
              <Select.Option value="false">{t['customer.no']}</Select.Option>
            </Select>
            <Select
              value={state.sort}
              onChange={(sort) => changeState({ sort, page: 0 })}
              style={{ width: 150 }}
            >
              {['username', 'email', 'displayName', 'active'].map((field) => (
                <Select.Option key={field} value={field}>
                  {t[`customer.${field === 'displayName' ? 'name' : field}`]}
                </Select.Option>
              ))}
            </Select>
            <Select
              value={state.direction}
              onChange={(direction) => changeState({ direction, page: 0 })}
              style={{ width: 120 }}
            >
              <Select.Option value="asc">{t['customer.asc']}</Select.Option>
              <Select.Option value="desc">{t['customer.desc']}</Select.Option>
            </Select>
          </Space>
          {lookupError && (
            <Typography.Paragraph type="error">
              {t['customer.lookup.invalid']}
            </Typography.Paragraph>
          )}
          {compact ? (
            page?.items.length ? (
              <Space direction="vertical" style={{ width: '100%' }}>
                {page.items.map((item) => (
                  <Descriptions
                    key={item.username}
                    column={1}
                    border
                    data={[
                      {
                        label: t['customer.username'],
                        value: (
                          <Button
                            type="text"
                            onClick={() =>
                              history.push(
                                detailPath(mode, item.username, location.search)
                              )
                            }
                          >
                            {item.username}
                          </Button>
                        ),
                      },
                      { label: t['customer.email'], value: item.email },
                      { label: t['customer.name'], value: item.displayName },
                      {
                        label: t['customer.active'],
                        value: (
                          <Tag color={item.active ? 'green' : 'gray'}>
                            {item.active ? t['customer.yes'] : t['customer.no']}
                          </Tag>
                        ),
                      },
                    ]}
                  />
                ))}
              </Space>
            ) : (
              !loading && <Empty />
            )
          ) : (
            <Table
              rowKey="username"
              columns={columns}
              data={page?.items || []}
              loading={loading}
              pagination={false}
              scroll={{ x: 720 }}
            />
          )}
          {page && (
            <Pagination
              style={{ marginTop: 16 }}
              current={state.page + 1}
              pageSize={state.size}
              total={page.totalElements}
              simple={compact}
              sizeCanChange={!compact}
              sizeOptions={[10, 20, 50, 100]}
              showTotal={!compact}
              onChange={(current) => changeState({ page: current - 1 })}
              onPageSizeChange={(size) => changeState({ size, page: 0 })}
            />
          )}
        </>
      )}
    </Card>
  );
}
