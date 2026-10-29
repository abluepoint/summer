export type DictionaryOption = {
  value: string;
  label: string;
  sortOrder: number;
  enabled: boolean;
};

export type PublicDictionary = {
  code: string;
  items: DictionaryOption[];
  version?: number;
};

export type DictionaryNotification = {
  id: number;
  dictionaryCode: string;
  eventType: string;
  version: number;
  requestId?: string;
};

export type DictionaryStreamState = 'CONNECTING' | 'OPEN' | 'RECONNECTING' | 'CLOSED';
