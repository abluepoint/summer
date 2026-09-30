import React, { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Message,
  Modal,
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
  DRAFT_KEY,
  getAuthenticatedUser,
  isAuthRedirecting,
  readCustomerDraft,
  registerCustomerDraftProvider,
} from '@/auth/oidc';
import {
  Customer,
  CustomerPage,
  SummerApiError,
  deleteCustomer,
  getCustomer,
  listCustomers,
  updateCustomer,
} from './api';
import {
  detailPath,
  detailUsername,
  canConfirmDeletion,
  listSearch,
  pageAfterDeletion,
  readListState,
} from './contract.mjs';
import CustomerForm, { CustomerDraft } from './CustomerForm';
import { pageFormSearch } from './page/return-contract.mjs';
import {
  customerDraft,
  editRecovery,
  emptyCustomerDraft,
  isEditDirty,
} from './form-contract.mjs';

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
  const [canWrite, setCanWrite] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);
  const [typedUsername, setTypedUsername] = useState('');
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteError, setDeleteError] = useState<SummerApiError | null>(null);
  const [actionError, setActionError] = useState<SummerApiError | null>(null);
  const [actionBusy, setActionBusy] = useState(false);
  const focusTimer = useRef<number | null>(null);
  const recovered = useRef(
    mode === 'drawer' || mode === 'modal'
      ? readCustomerDraft(sessionStorage)
      : undefined
  );
  const recoveredEdit = useRef(editRecovery(recovered.current));
  const [draft, setDraft] = useState<CustomerDraft>(() =>
    mode !== 'page' && !recoveredEdit.current
      ? customerDraft(recovered.current)
      : emptyCustomerDraft()
  );
  const [createOpen, setCreateOpen] = useState(
    () =>
      mode !== 'page' &&
      !recoveredEdit.current &&
      Object.values(draft).some(Boolean)
  );
  const [createBusy, setCreateBusy] = useState(false);
  const [editOriginal, setEditOriginal] = useState<CustomerDraft | null>(
    () => recoveredEdit.current?.original || null
  );
  const [editDraft, setEditDraft] = useState<CustomerDraft>(
    () => recoveredEdit.current?.draft || emptyCustomerDraft()
  );
  const [editBusy, setEditBusy] = useState(false);

  useEffect(() => {
    let live = true;
    getAuthenticatedUser()
      .then((user) => {
        if (live) setCanWrite(Boolean(user?.scopes.includes('customer.write')));
      })
      .catch(() => {
        if (live) setCanWrite(false);
      });
    return () => {
      live = false;
    };
  }, [location.pathname, revision]);

  useEffect(() => {
    if (!createOpen) return undefined;
    return registerCustomerDraftProvider(() => draft);
  }, [createOpen, draft]);

  useEffect(() => {
    if (!editOriginal) return undefined;
    return registerCustomerDraftProvider(() => ({
      ...editDraft,
      editUsername: editOriginal.username,
      originalEmail: editOriginal.email,
      originalDisplayName: editOriginal.displayName,
    }));
  }, [editOriginal, editDraft]);

  useEffect(() => {
    if (!editOriginal || !isEditDirty(editOriginal, editDraft))
      return undefined;
    const unblock = history.block(() =>
      isAuthRedirecting() ? undefined : t['customer.edit.leave']
    );
    const beforeUnload = (event: BeforeUnloadEvent) => {
      if (isAuthRedirecting()) return;
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', beforeUnload);
    return () => {
      unblock();
      window.removeEventListener('beforeunload', beforeUnload);
    };
  }, [editOriginal, editDraft, history, t]);

  useEffect(() => {
    if (!createOpen || !Object.values(draft).some(Boolean)) return undefined;
    const unblock = history.block(() =>
      isAuthRedirecting() ? undefined : t['customer.create.leave']
    );
    const beforeUnload = (event: BeforeUnloadEvent) => {
      if (isAuthRedirecting()) return;
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', beforeUnload);
    return () => {
      unblock();
      window.removeEventListener('beforeunload', beforeUnload);
    };
  }, [createOpen, draft, history, t]);

  useEffect(
    () => () => {
      if (focusTimer.current !== null) window.clearTimeout(focusTimer.current);
    },
    []
  );

  function openCreate() {
    if (focusTimer.current !== null) window.clearTimeout(focusTimer.current);
    focusTimer.current = null;
    setEditOriginal(null);
    setCreateOpen(true);
  }

  function openEdit(record: Customer) {
    if (mode === 'page') {
      history.push({
        pathname: `/customer/page/edit/${encodeURIComponent(record.username)}`,
        search: pageFormSearch(
          `/customer/page${location.search}${location.hash}`
        ),
      });
      return;
    }
    if (focusTimer.current !== null) window.clearTimeout(focusTimer.current);
    focusTimer.current = null;
    setCreateOpen(false);
    const original = customerDraft(record);
    setEditOriginal(original);
    setEditDraft(original);
  }

  function closeEdit() {
    const username = editOriginal?.username;
    setEditOriginal(null);
    setEditDraft(emptyCustomerDraft());
    sessionStorage.removeItem(DRAFT_KEY);
    focusTimer.current = window.setTimeout(() => {
      (
        document.getElementById(`customer-edit-trigger-${username}`) ||
        document.getElementById('customer-create-trigger')
      )?.focus();
      focusTimer.current = null;
    }, 500);
  }

  function requestCloseEdit() {
    if (editBusy) return;
    if (editOriginal && isEditDirty(editOriginal, editDraft)) {
      Modal.confirm({
        title: t['customer.edit.discardTitle'],
        content: t['customer.edit.discardMessage'],
        okText: t['customer.create.discard'],
        cancelText: t['customer.create.continue'],
        onOk: closeEdit,
      });
    } else closeEdit();
  }

  function edited(updated?: Customer) {
    closeEdit();
    if (updated && customer?.username === updated.username)
      setCustomer(updated);
    setRevision((value) => value + 1);
    if (updated) Message.success(t['customer.edit.success']);
  }

  function closeCreate() {
    setCreateOpen(false);
    setDraft(emptyCustomerDraft());
    sessionStorage.removeItem(DRAFT_KEY);
    // Arco's focus lock finishes its close transition after React commits.
    focusTimer.current = window.setTimeout(() => {
      document.getElementById('customer-create-trigger')?.focus();
      focusTimer.current = null;
    }, 500);
  }

  function requestCloseCreate() {
    if (createBusy) return;
    if (Object.values(draft).some(Boolean)) {
      Modal.confirm({
        title: t['customer.create.discardTitle'],
        content: t['customer.create.discardMessage'],
        okText: t['customer.create.discard'],
        cancelText: t['customer.create.continue'],
        onOk: closeCreate,
      });
    } else closeCreate();
  }

  function created() {
    closeCreate();
    setRevision((value) => value + 1);
    Message.success(t['customer.create.success']);
  }

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

  function beginDelete(username: string) {
    setDeleteTarget(username);
    setTypedUsername('');
    setDeleteError(null);
  }

  function closeDelete() {
    if (deleteBusy) return;
    setDeleteTarget(null);
    setTypedUsername('');
    setDeleteError(null);
  }

  async function confirmDelete() {
    if (
      !deleteTarget ||
      !canConfirmDeletion(typedUsername, deleteTarget) ||
      deleteBusy
    )
      return;
    setDeleteBusy(true);
    setDeleteError(null);
    try {
      await deleteCustomer(deleteTarget);
      Message.success(t['customer.delete.success']);
      setDeleteTarget(null);
      setTypedUsername('');
      setCustomer(null);
      if (editOriginal?.username === deleteTarget) closeEdit();
      if (isDetailRoute) {
        history.replace(`/customer/${mode}${location.search}`);
      } else {
        const nextPage = pageAfterDeletion(
          state.page,
          state.size,
          Math.max(0, (page?.totalElements || 0) - 1)
        );
        if (nextPage !== state.page) changeState({ page: nextPage });
        else setRevision((value) => value + 1);
      }
    } catch (failure) {
      setDeleteError(
        failure instanceof SummerApiError
          ? failure
          : new SummerApiError({
              status: 0,
              code: '',
              message: '',
              violations: [],
              requestId: '',
              timestamp: '',
            })
      );
    } finally {
      setDeleteBusy(false);
    }
  }

  function requestActiveChange(record: Customer) {
    if (!canWrite || actionBusy) return;
    Modal.confirm({
      title:
        t[
          record.active
            ? 'customer.deactivate.title'
            : 'customer.activate.title'
        ],
      content:
        t[
          record.active
            ? 'customer.deactivate.message'
            : 'customer.activate.message'
        ],
      okText: t[record.active ? 'customer.deactivate' : 'customer.activate'],
      cancelText: t['customer.create.cancel'],
      onOk: async () => {
        setActionBusy(true);
        setActionError(null);
        try {
          const updated = await updateCustomer(record.username, {
            active: !record.active,
          });
          if (customer?.username === updated.username) setCustomer(updated);
          setRevision((value) => value + 1);
          Message.success(t['customer.active.success']);
        } catch (failure) {
          setActionError(
            failure instanceof SummerApiError
              ? failure
              : new SummerApiError({
                  status: 0,
                  code: '',
                  message: '',
                  violations: [],
                  requestId: '',
                  timestamp: '',
                })
          );
        } finally {
          setActionBusy(false);
        }
      },
    });
  }

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

  function openPageCreate() {
    history.push({
      pathname: '/customer/page/create',
      search: pageFormSearch(
        `${location.pathname}${location.search}${location.hash}`
      ),
    });
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
    ...(canWrite
      ? [
          {
            title: t['customer.actions'],
            dataIndex: 'username',
            render: (username: string, record: Customer) => (
              <Space>
                <Button
                  id={`customer-edit-trigger-${username}`}
                  type="text"
                  onClick={() => openEdit(record)}
                >
                  {t['customer.edit']}
                </Button>
                <Button
                  status="danger"
                  type="text"
                  onClick={() => beginDelete(username)}
                >
                  {t['customer.delete']}
                </Button>
              </Space>
            ),
          },
        ]
      : []),
  ];

  return (
    <Card title={detail ? t['customer.detail'] : t['customer.list']}>
      {mode === 'modal' && (
        <Alert
          type="info"
          style={{ marginBottom: 16 }}
          content={
            <Space direction="vertical">
              <Typography.Text>{t['customer.modal.guide']}</Typography.Text>
              <Space wrap>
                <Button
                  size="mini"
                  onClick={() =>
                    history.push(`/customer/drawer${location.search}`)
                  }
                >
                  {t['menu.customer.drawer']}
                </Button>
                <Button
                  size="mini"
                  onClick={() =>
                    history.push(`/customer/page${location.search}`)
                  }
                >
                  {t['menu.customer.page']}
                </Button>
              </Space>
            </Space>
          }
        />
      )}
      {mode === 'page' && !isDetailRoute && (
        <Alert
          type="info"
          style={{ marginBottom: 16 }}
          title={t['customer.page.descriptionTitle']}
          content={
            <Space direction="vertical">
              <Typography.Text>
                {t['customer.page.description']}
              </Typography.Text>
              <Space wrap>
                <Button
                  type="text"
                  onClick={() =>
                    history.push(`/customer/drawer${location.search}`)
                  }
                >
                  {t['menu.customer.drawer']}
                </Button>
                <Button
                  type="text"
                  onClick={() =>
                    history.push(`/customer/modal${location.search}`)
                  }
                >
                  {t['menu.customer.modal']}
                </Button>
              </Space>
            </Space>
          }
        />
      )}
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
      {actionError && (
        <Alert
          type="error"
          title={
            actionError.status === 403
              ? t['auth.forbidden']
              : actionError.status === 404
              ? t['customer.error.404']
              : actionError.status === 0
              ? t['customer.error.network']
              : t['customer.active.failed']
          }
          content={
            <Space direction="vertical">
              {actionError.message && (
                <Typography.Text>{actionError.message}</Typography.Text>
              )}
              {actionError.requestId && (
                <Space>
                  <Typography.Text>
                    requestId: {actionError.requestId}
                  </Typography.Text>
                  <Button
                    size="mini"
                    onClick={() =>
                      clipboard(actionError.requestId).catch(() =>
                        Message.error(t['customer.error.copyFailed'])
                      )
                    }
                  >
                    {t['customer.error.copyId']}
                  </Button>
                </Space>
              )}
              <Button size="mini" onClick={() => setActionError(null)}>
                {t['customer.error.dismiss']}
              </Button>
            </Space>
          }
        />
      )}
      {!loading && !error && detail && customer && (
        <>
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
          {canWrite && (
            <Space style={{ marginTop: 16 }}>
              <Button
                id={`customer-edit-trigger-${customer.username}`}
                onClick={() => openEdit(customer)}
              >
                {t['customer.edit']}
              </Button>
              <Button
                disabled={actionBusy}
                onClick={() => requestActiveChange(customer)}
              >
                {
                  t[
                    customer.active
                      ? 'customer.deactivate'
                      : 'customer.activate'
                  ]
                }
              </Button>
              <Button
                status="danger"
                onClick={() => beginDelete(customer.username)}
              >
                {t['customer.delete']}
              </Button>
            </Space>
          )}
        </>
      )}
      {!isDetailRoute && !error && (
        <>
          {mode !== 'page' && canWrite && (
            <Button
              id="customer-create-trigger"
              type="primary"
              onClick={openCreate}
              style={{ marginBottom: 16 }}
            >
              {t['customer.create.open']}
            </Button>
          )}
          {mode === 'page' && canWrite && (
            <Button
              id="customer-create-trigger"
              type="primary"
              onClick={openPageCreate}
              style={{ marginBottom: 16 }}
            >
              {t['customer.create.open']}
            </Button>
          )}
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
                      ...(canWrite
                        ? [
                            {
                              label: t['customer.actions'],
                              value: (
                                <Space>
                                  <Button
                                    id={`customer-edit-trigger-${item.username}`}
                                    type="text"
                                    onClick={() => openEdit(item)}
                                  >
                                    {t['customer.edit']}
                                  </Button>
                                  <Button
                                    status="danger"
                                    type="text"
                                    onClick={() => beginDelete(item.username)}
                                  >
                                    {t['customer.delete']}
                                  </Button>
                                </Space>
                              ),
                            },
                          ]
                        : []),
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
              scroll={{ x: 820 }}
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
      <Modal
        visible={deleteTarget !== null}
        title={t['customer.delete.title']}
        onCancel={closeDelete}
        maskClosable={false}
        style={{
          width: compact ? 'calc(100vw - 32px)' : 520,
          maxWidth: 'calc(100vw - 32px)',
        }}
        footer={
          <Space>
            <Button onClick={closeDelete} disabled={deleteBusy}>
              {t['customer.delete.cancel']}
            </Button>
            <Button
              status="danger"
              type="primary"
              loading={deleteBusy}
              disabled={
                !canWrite ||
                !canConfirmDeletion(typedUsername, deleteTarget) ||
                [401, 403, 404].includes(deleteError?.status || 0)
              }
              onClick={confirmDelete}
            >
              {t['customer.delete.confirm']}
            </Button>
          </Space>
        }
      >
        <Alert
          type="warning"
          content={
            <span style={{ whiteSpace: 'normal', overflowWrap: 'anywhere' }}>
              {t['customer.delete.warning']}
            </span>
          }
        />
        <Typography.Paragraph style={{ marginTop: 16, marginBottom: 4 }}>
          {t['customer.delete.instruction']}
        </Typography.Paragraph>
        <Typography.Text
          bold
          style={{
            display: 'block',
            marginBottom: 12,
            overflowWrap: 'anywhere',
          }}
        >
          {deleteTarget}
        </Typography.Text>
        <Input
          autoFocus
          value={typedUsername}
          onChange={(value) => setTypedUsername(value)}
          placeholder={t['customer.delete.placeholder']}
          disabled={deleteBusy}
        />
        {deleteError && (
          <Alert
            style={{ marginTop: 16 }}
            type="error"
            content={
              <Space direction="vertical">
                <Typography.Text>
                  {deleteError.status === 403
                    ? t['customer.delete.forbidden']
                    : deleteError.status === 404
                    ? t['customer.delete.missing']
                    : deleteError.status === 401
                    ? t['customer.error.401']
                    : t['customer.delete.failed']}
                </Typography.Text>
                {deleteError.message && (
                  <Typography.Text style={{ overflowWrap: 'anywhere' }}>
                    {deleteError.message}
                  </Typography.Text>
                )}
                {deleteError.requestId && (
                  <Space>
                    <Typography.Text>
                      requestId: {deleteError.requestId}
                    </Typography.Text>
                    <Button
                      size="mini"
                      onClick={() =>
                        clipboard(deleteError.requestId).catch(() =>
                          Message.error(t['customer.error.copyFailed'])
                        )
                      }
                    >
                      {t['customer.error.copyId']}
                    </Button>
                  </Space>
                )}
              </Space>
            }
          />
        )}
      </Modal>
      {mode === 'drawer' && (
        <Drawer
          title={t['customer.edit.title']}
          visible={editOriginal !== null}
          width={compact ? '100%' : 440}
          footer={null}
          unmountOnExit
          onCancel={requestCloseEdit}
        >
          {editOriginal && (
            <CustomerForm
              value={editDraft}
              original={editOriginal}
              onChange={setEditDraft}
              onCancel={requestCloseEdit}
              onBusyChange={setEditBusy}
              onSuccess={edited}
              canWrite={canWrite}
            />
          )}
        </Drawer>
      )}
      {mode === 'modal' && (
        <Modal
          title={t['customer.edit.title']}
          visible={editOriginal !== null}
          footer={null}
          unmountOnExit
          focusLock
          autoFocus
          maskClosable
          escToExit
          onCancel={requestCloseEdit}
          style={{
            width: compact ? 'calc(100vw - 32px)' : 520,
            maxWidth: 'calc(100vw - 32px)',
          }}
        >
          {editOriginal && (
            <CustomerForm
              value={editDraft}
              original={editOriginal}
              onChange={setEditDraft}
              onCancel={requestCloseEdit}
              onBusyChange={setEditBusy}
              onSuccess={edited}
              canWrite={canWrite}
            />
          )}
        </Modal>
      )}
      {mode === 'drawer' && (
        <Drawer
          title={t['customer.create.title']}
          visible={createOpen}
          width={compact ? '100%' : 440}
          footer={null}
          unmountOnExit
          onCancel={requestCloseCreate}
        >
          {createOpen && (
            <CustomerForm
              value={draft}
              onChange={setDraft}
              onCancel={requestCloseCreate}
              onBusyChange={setCreateBusy}
              onSuccess={created}
              canWrite={canWrite}
            />
          )}
        </Drawer>
      )}
      {mode === 'modal' && (
        <Modal
          title={t['customer.create.title']}
          visible={createOpen}
          footer={null}
          unmountOnExit
          focusLock
          autoFocus
          maskClosable
          escToExit
          onCancel={requestCloseCreate}
          style={{
            width: compact ? 'calc(100vw - 32px)' : 520,
            maxWidth: 'calc(100vw - 32px)',
          }}
        >
          {createOpen && (
            <CustomerForm
              value={draft}
              onChange={setDraft}
              onCancel={requestCloseCreate}
              onBusyChange={setCreateBusy}
              onSuccess={created}
              canWrite={canWrite}
            />
          )}
        </Modal>
      )}
    </Card>
  );
}
