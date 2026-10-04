import axios from 'axios';
import { readEnvelope, readFailure } from '../customer/contract.mjs';

export type DictionaryOption = {
  value: string;
  label: string;
  sortOrder: number;
  enabled: boolean;
};

export type PublicDictionary = {
  code: string;
  items: DictionaryOption[];
};

export async function getPublicDictionary(code: string): Promise<PublicDictionary> {
  try {
    const response = await axios.get(`/public/dictionaries/${encodeURIComponent(code)}`);
    return readEnvelope(response.data, response.status) as PublicDictionary;
  } catch (error) {
    if (!axios.isAxiosError(error)) throw error;
    const status = error.response?.status || 0;
    const failure = readFailure(
      error.response?.data,
      status,
      String(error.response?.headers?.['x-request-id'] || '')
    );
    throw new Error(failure.message || `HTTP ${status || 'network'}`);
  }
}
