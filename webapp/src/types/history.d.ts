export interface HistoryDetail {
  queryId: string;
  queryText: string;
  user: string;
  source: string;
  backendUrl: string;
  backendName?: string;
  captureTime: number;
  routingGroup: string;
  externalUrl: string;
}

export interface HistoryData {
  total: number;
  rows: HistoryDetail[];
}
