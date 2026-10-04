import axios from 'axios';
import { readEnvelope, readFailure } from '../customer/contract.mjs';

export type RealtimeMessage = {
  messageId: string; enterpriseId: string; clientMessageId: string; sender: string;
  recipient?: string; payload: Record<string, unknown>; sequence: number; status: string;
  createdAt: string; acknowledgedAt?: string; duplicate: boolean;
};
export type RealtimeError = { status: number; code: string; message: string; requestId: string };
const base = (enterpriseId: string) => `/api/v1/enterprises/${encodeURIComponent(enterpriseId)}/messages`;

async function request<T>(call: () => Promise<{ data: unknown; status: number }>): Promise<T> {
  try { return readEnvelope((await call()).data, 200) as T; }
  catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    throw readFailure(error.response?.data, error.response?.status || 0,
      String(error.response?.headers?.['x-request-id'] || '')) as RealtimeError;
  }
}

export const listMessages = (enterpriseId: string, after?: number) =>
  request<{ items: RealtimeMessage[]; latestSequence: number; hasMore: boolean }>(() =>
    axios.get(base(enterpriseId), { params: after ? { after } : undefined }));
export const sendMessage = (enterpriseId: string, payload: Record<string, unknown>) =>
  request<RealtimeMessage>(() => axios.post(base(enterpriseId), payload));
export const acknowledgeMessage = (enterpriseId: string, messageId: string) =>
  request<RealtimeMessage>(() => axios.post(`${base(enterpriseId)}/${encodeURIComponent(messageId)}:ack`));
export const connectionStatus = (enterpriseId: string) =>
  request<{ enterpriseId: string; connections: number; maxConnections: number; status: string }>(() => axios.get(`${base(enterpriseId)}/connections`));

export type RealtimeSocketEvent = { type: string; message?: RealtimeMessage; messageId?: string; status?: string; code?: string; sequence?: number };
export type RealtimeSocket = { send: (payload: Record<string, unknown>) => void; close: () => void };

export function connectRealtime(enterpriseId: string, after: number, onEvent: (event: RealtimeSocketEvent) => void,
                                onState: (state: 'CONNECTING' | 'OPEN' | 'RECONNECTING' | 'CLOSED') => void): RealtimeSocket {
  let socket: WebSocket | undefined;
  let closed = false;
  let retry = 0;
  let cursor = after;
  const open = () => {
    if (closed) return;
    onState(retry ? 'RECONNECTING' : 'CONNECTING');
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    socket = new WebSocket(`${protocol}//${window.location.host}/ws/v1/messages?enterpriseId=${encodeURIComponent(enterpriseId)}&after=${cursor}`);
    socket.onopen = () => { retry = 0; onState('OPEN'); };
    socket.onmessage = (event) => {
      try {
        const parsed = JSON.parse(event.data) as RealtimeSocketEvent;
        if (parsed.message?.sequence) cursor = Math.max(cursor, parsed.message.sequence);
        if (parsed.sequence) cursor = Math.max(cursor, parsed.sequence);
        onEvent(parsed);
      } catch { onEvent({ type: 'error', code: 'SUMMER_MESSAGE_INVALID' }); }
    };
    socket.onclose = () => {
      if (closed) { onState('CLOSED'); return; }
      retry += 1;
      window.setTimeout(open, Math.min(8000, 250 * 2 ** Math.min(retry - 1, 5)));
    };
    socket.onerror = () => socket?.close();
  };
  open();
  return {
    send: (payload) => { if (socket?.readyState === WebSocket.OPEN) socket.send(JSON.stringify(payload)); },
    close: () => { closed = true; socket?.close(); },
  };
}
