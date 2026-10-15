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

export type RealtimeSocketEvent = { type: string; message?: RealtimeMessage; messageId?: string; clientMessageId?: string; status?: string; code?: string; sequence?: number };
export type RealtimeSocket = { send: (payload: Record<string, unknown>) => boolean; close: () => void };

export function connectRealtime(enterpriseId: string, after: number, onEvent: (event: RealtimeSocketEvent) => void,
                                onState: (state: 'CONNECTING' | 'OPEN' | 'RECONNECTING' | 'CLOSED') => void): RealtimeSocket {
  let socket: WebSocket | undefined;
  let closed = false;
  let retry = 0;
  let cursor = after;
  let heartbeat: number | undefined;
  const open = () => {
    if (closed) return;
    onState(retry ? 'RECONNECTING' : 'CONNECTING');
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    socket = new WebSocket(`${protocol}//${window.location.host}/ws/v1/messages?enterpriseId=${encodeURIComponent(enterpriseId)}&after=${cursor}`);
    socket.onopen = () => {
      retry = 0; onState('OPEN');
      heartbeat = window.setInterval(() => {
        if (socket?.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type: 'ping' }));
      }, 15000);
    };
    socket.onmessage = (event) => {
      try {
        const parsed = JSON.parse(event.data) as RealtimeSocketEvent;
        if (parsed.message?.sequence) cursor = Math.max(cursor, parsed.message.sequence);
        onEvent(parsed);
      } catch { onEvent({ type: 'error', code: 'SUMMER_MESSAGE_INVALID' }); }
    };
    socket.onclose = (event) => {
      if (heartbeat !== undefined) window.clearInterval(heartbeat);
      if (closed) { onState('CLOSED'); return; }
      onEvent({ type: 'error', code: event.code === 1008 ? 'SUMMER_ACCESS_DENIED' : 'SUMMER_MESSAGE_CONNECTION_LOST' });
      if (event.code === 1008) { closed = true; onState('CLOSED'); return; }
      retry += 1;
      window.setTimeout(open, Math.min(8000, 250 * 2 ** Math.min(retry - 1, 5)));
    };
    socket.onerror = () => socket?.close();
  };
  open();
  return {
    send: (payload) => {
      if (socket?.readyState !== WebSocket.OPEN) return false;
      socket.send(JSON.stringify(payload)); return true;
    },
    close: () => { closed = true; if (heartbeat !== undefined) window.clearInterval(heartbeat); socket?.close(); },
  };
}
