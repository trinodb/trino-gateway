export interface HistoryDetail {
  queryId: string;
  queryText: string;
  user: string;
  source: string;
  backendUrl: string;
  captureTime: number;
}

export interface HistoryData {
  total: number;
  rows: HistoryDetail[];
}
