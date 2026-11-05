import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Descriptions, Spin } from '@arco-design/web-react';
import { useLocation } from 'react-router-dom';
import useLocale from '@/utils/useLocale';
import useActiveEnterprise from '@/pages/data-scopes/useActiveEnterprise';
import { getCustomerInsights, listEnterprisePlugins, PluginApiError, PluginInsights } from '../api';

const pluginPath = '/plugins/customer-insights';

/** A shipped local page. Server metadata never selects a component or import. */
export default function CustomerInsightsPage() {
  const t = useLocale();
  const tenant = useActiveEnterprise();
  const { pathname } = useLocation();
  const currentRequest = useRef(0);
  const [loading, setLoading] = useState(false);
  const [unavailable, setUnavailable] = useState(false);
  const [error, setError] = useState<PluginApiError | null>(null);
  const [result, setResult] = useState<{ enterpriseId: string; insights: PluginInsights } | null>(null);

  const load = useCallback(async () => {
    const request = ++currentRequest.current;
    setResult(null);
    setError(null);
    setUnavailable(false);
    if (!tenant.ready || !tenant.enterpriseId || pathname !== pluginPath) {
      setUnavailable(true);
      return;
    }
    setLoading(true);
    try {
      const manifest = await listEnterprisePlugins(tenant.enterpriseId);
      if (request !== currentRequest.current) return;
      const allowed = manifest.items.some((plugin) =>
        plugin.pluginId === 'customer-insights' && plugin.trusted && plugin.status === 'ENABLED' &&
        plugin.menus.some((menu) => menu.menuId === 'customer-insights' && menu.path === pluginPath)
      );
      if (!allowed) {
        setUnavailable(true);
        return;
      }
      const insights = await getCustomerInsights(tenant.enterpriseId);
      if (request === currentRequest.current) setResult({ enterpriseId: tenant.enterpriseId, insights });
    } catch (failure) {
      if (request === currentRequest.current) setError(failure as PluginApiError);
    } finally {
      if (request === currentRequest.current) setLoading(false);
    }
  }, [tenant.ready, tenant.enterpriseId, pathname]);

  useEffect(() => {
    if (tenant.ready) load();
    return () => { currentRequest.current += 1; };
  }, [tenant.ready, load]);

  if (!tenant.ready) return <Spin />;
  if (pathname !== pluginPath || !tenant.enterpriseId) return <Alert type="error" content={t['plugins.unavailable']} />;
  const insights = result?.enterpriseId === tenant.enterpriseId ? result.insights : null;
  return <Card title={t['plugins.exampleTitle']} extra={<Button loading={loading} onClick={load}>{t['plugins.refresh']}</Button>}>
    {error && <Alert type="error" content={`${error.message || t['plugins.error']}${error.requestId ? ` (${error.requestId})` : ''}`} style={{ marginBottom: 16 }} />}
    {unavailable && <Alert type="warning" content={t['plugins.unavailable']} />}
    {loading && <Spin />}
    {insights && <Descriptions column={1} data={[
      { label: t['plugins.enterprise'], value: tenant.enterpriseId },
      { label: t['plugins.version'], value: insights.version },
      { label: t['plugins.activeCustomers'], value: insights.activeCustomerCount },
      { label: t['plugins.requestId'], value: insights.requestId },
      { label: t['plugins.dictionary'], value: `${insights.dictionary.code} (${insights.dictionary.items.length})` },
      { label: t['plugins.configuration'], value: insights.configuration.map((item) => String(item.key)).join(', ') },
    ]} />}
  </Card>;
}
