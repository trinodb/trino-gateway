
export interface  DistributionDetail {
  totalBackendCount: number;
  offlineBackendCount: number;
  onlineBackendCount: number;
  totalQueryCount: number;
  averageQueryCountMinute: number;
  averageQueryCountSecond: number;
  distributionChart: DistributionChartData[];
  lineChart: Record<string, LineChartData[]>;
}

export interface  DistributionChartData {
  backendUrl: string;
  queryCount: number;
  name: string;
}

export interface  LineChartData {
  minute: string;
  backendUrl: string;
  queryCount: number;
  name: string;
}
