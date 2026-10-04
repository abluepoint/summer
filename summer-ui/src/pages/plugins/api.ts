import axios from 'axios';
import { readEnvelope, readFailure } from '../customer/contract.mjs';

export type PluginStatus = 'REGISTERED' | 'ENABLED' | 'DISABLED' | 'INCOMPATIBLE';
export type PluginMenu = { menuId: string; path: string; titleKey: string; capabilityCode: string };
export type PluginConfiguration = { key: string; type: string; required: boolean; publicValue: boolean; configured?: boolean; value?: unknown; status?: string };
export type Plugin = { pluginId: string; displayName: string; version: string; artifactDigest: string; status: PluginStatus; trusted: boolean; diagnostic: string; revision: number; capabilities: string[]; menus: PluginMenu[]; configuration: PluginConfiguration[]; apiBasePath: string; dataResources: string[] };
export type PluginPage = { items: Plugin[]; revision: number };
export type PluginDiagnostic = { pluginId: string; version: string; trusted: boolean; compatible: boolean; status: PluginStatus; diagnostic: string; problems: string[] };
export type PluginInsights = { pluginId: string; version: string; requestId: string; activeCustomerCount: number; configuration: Record<string, unknown>[]; dictionary: { code: string; items: { value: string; label: string; sortOrder: number }[] }; auditRevision: number };
export type PluginApiError = { status: number; code: string; message: string; requestId: string };

async function request<T>(call: () => Promise<{ data: unknown; status: number }>): Promise<T> {
  try { return readEnvelope((await call()).data, 200) as T; }
  catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    const failure = readFailure(error.response?.data, error.response?.status || 0, String(error.response?.headers?.['x-request-id'] || ''));
    throw failure as PluginApiError;
  }
}
const platform = '/api/v1/platform/plugins';
const enterprise = (enterpriseId: string) => `/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/plugins`;
export const listPlugins = () => request<PluginPage>(() => axios.get(platform));
export const registerPlugin = (payload: { pluginId: string; version: string; artifactDigest: string }) => request<Plugin>(() => axios.post(platform, payload));
export const enablePlugin = (pluginId: string) => request<Plugin>(() => axios.post(`${platform}/${encodeURIComponent(pluginId)}:enable`));
export const disablePlugin = (pluginId: string) => request<Plugin>(() => axios.post(`${platform}/${encodeURIComponent(pluginId)}:disable`));
export const upgradePlugin = (pluginId: string, payload: { version: string; artifactDigest: string }) => request<Plugin>(() => axios.post(`${platform}/${encodeURIComponent(pluginId)}:upgrade`, payload));
export const getPluginDiagnostic = (pluginId: string) => request<PluginDiagnostic>(() => axios.get(`${platform}/${encodeURIComponent(pluginId)}/diagnostic`));
export const listEnterprisePlugins = (enterpriseId: string) => request<PluginPage>(() => axios.get(enterprise(enterpriseId)));
export const getCustomerInsights = (enterpriseId: string) => request<PluginInsights>(() => axios.get(`${enterprise(enterpriseId)}/customer-insights/summary`));
