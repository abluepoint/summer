import { SummerApiError } from '@/api/errors';
import AppLoading from '@/components/AppLoading';
import React, { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Message,
  Modal,
  Space,
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
import CustomerForm, { CustomerDraft } from '../CustomerForm';
import { Customer, getCustomer } from '../api';
import {
  customerDraft,
  editRecovery,
  emptyCustomerDraft,
  isEditDirty,
} from '../form-contract.mjs';
import CustomerScreen from '../CustomerScreen';
import { pageFormRoute, pageListReturn } from './return-contract.mjs';

function CustomerRouteForm() {
  const t = useLocale();
  const history = useHistory();
  const location = useLocation();
  const route = pageFormRoute(location.pathname);
  const action = route?.action;
  const from = pageListReturn(new URLSearchParams(location.search).get('from'));
  const recovered = useRef(readCustomerDraft(sessionStorage));
  const recovery = useRef(editRecovery(recovered.current));
  const [draft, setDraft] = useState<CustomerDraft>(() =>
    route?.action === 'create' && !recovery.current
      ? customerDraft(recovered.current)
      : route?.action === 'edit' &&
        recovery.current?.original.username === route.username
      ? recovery.current.draft
      : emptyCustomerDraft()
  );
  const [original, setOriginal] = useState<CustomerDraft | null>(() =>
    route?.action === 'edit' &&
    recovery.current?.original.username === route.username
      ? recovery.current.original
      : null
  );
  const [loading, setLoading] = useState(route?.action === 'edit');
  const [error, setError] = useState<SummerApiError | null>(null);
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState(false);
  const [canWrite, setCanWrite] = useState(false);
  const leaveWithoutPrompt = useRef(false);
  const dirty =
    route?.action === 'create'
      ? Object.values(draft).some(Boolean)
      : Boolean(original && isEditDirty(original, draft));

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
  }, []);

  useEffect(() => {
    if (route?.action !== 'edit') return undefined;
    let live = true;
    setLoading(true);
    setError(null);
    getCustomer(route.username)
      .then((result: Customer) => {
        if (!live) return;
        if (
          !recovery.current ||
          recovery.current.original.username !== route.username
        ) {
          const baseline = customerDraft(result);
          setOriginal(baseline);
          setDraft(baseline);
        }
      })
      .catch((failure) => {
        if (live)
          setError(
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
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [route?.action, route?.username, revision]);

  useEffect(() => {
    if (!action) return undefined;
    return registerCustomerDraftProvider(() =>
      action === 'edit' && original
        ? {
            ...draft,
            editUsername: original.username,
            originalEmail: original.email,
            originalDisplayName: original.displayName,
          }
        : draft
    );
  }, [action, original, draft]);

  useEffect(() => {
    if (!dirty && !busy) return undefined;
    const unblock = history.block(() => {
      if (leaveWithoutPrompt.current || isAuthRedirecting()) return undefined;
      if (busy) return false;
      return t[
        route?.action === 'edit' ? 'customer.edit.leave' : 'customer.page.leave'
      ];
    });
    const beforeUnload = (event: BeforeUnloadEvent) => {
      if (leaveWithoutPrompt.current || isAuthRedirecting()) return;
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', beforeUnload);
    return () => {
      unblock();
      window.removeEventListener('beforeunload', beforeUnload);
    };
  }, [dirty, busy, history, route?.action, t]);

  function returnToList() {
    leaveWithoutPrompt.current = true;
    sessionStorage.removeItem(DRAFT_KEY);
    history.push(from);
  }

  function cancel() {
    if (busy) return;
    if (!dirty) {
      returnToList();
      return;
    }
    Modal.confirm({
      title:
        t[
          route?.action === 'edit'
            ? 'customer.edit.discardTitle'
            : 'customer.create.discardTitle'
        ],
      content:
        t[
          route?.action === 'edit'
            ? 'customer.edit.discardMessage'
            : 'customer.create.discardMessage'
        ],
      okText: t['customer.create.discard'],
      cancelText: t['customer.create.continue'],
      onOk: returnToList,
    });
  }

  function saved() {
    Message.success(
      t[
        route?.action === 'edit'
          ? 'customer.edit.success'
          : 'customer.create.success'
      ]
    );
    returnToList();
  }

  if (!route)
    return <Alert type="error" content={t['customer.lookup.invalid']} />;
  return (
    <Card
      title={
        t[
          route.action === 'edit'
            ? 'customer.edit.title'
            : 'customer.create.title'
        ]
      }
      style={{ maxWidth: 760, width: '100%', margin: '0 auto' }}
    >
      <Space direction="vertical" style={{ width: '100%' }}>
        {loading && <AppLoading fullScreen={false} />}
        {error && (
          <Alert
            type="error"
            title={
              t[
                error.status === 404
                  ? 'customer.error.404'
                  : error.status === 403
                  ? 'customer.error.403'
                  : error.status === 0
                  ? 'customer.error.network'
                  : 'customer.error.server'
              ]
            }
            content={
              <Space direction="vertical">
                {error.message && (
                  <Typography.Text>{error.message}</Typography.Text>
                )}
                {error.requestId && (
                  <Space>
                    <Typography.Text>
                      requestId: {error.requestId}
                    </Typography.Text>
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
                  <Button
                    size="mini"
                    onClick={() => setRevision((value) => value + 1)}
                  >
                    {t['customer.retry']}
                  </Button>
                )}
              </Space>
            }
          />
        )}
        {!loading &&
          (!error || (original && ![400, 403].includes(error.status))) && (
            <CustomerForm
              value={draft}
              original={
                route.action === 'edit' ? original || undefined : undefined
              }
              onChange={setDraft}
              onCancel={cancel}
              onBusyChange={setBusy}
              onSuccess={saved}
              canWrite={canWrite}
            />
          )}
        {error && !original && (
          <Button onClick={cancel}>{t['customer.back']}</Button>
        )}
      </Space>
    </Card>
  );
}

export default function CustomerPage() {
  const location = useLocation();
  return pageFormRoute(location.pathname) ? (
    <CustomerRouteForm key={location.pathname} />
  ) : (
    <CustomerScreen mode="page" />
  );
}
