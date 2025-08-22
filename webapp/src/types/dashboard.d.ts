export interface  DistributionDetail {
  totalBackendCount: number;
  offlineBackendCount: number;
  onlineBackendCount: number;
  healthyBackendCount: number;
  unhealthyBackendCount: number;
  totalQueryCount: number;
  averageQueryCountMinute: number;
  averageQueryCountSecond: number;
  distributionChart: DistributionChartData[];
  lineChart: Record<string, LineChartData[]>;
  startTime: string;
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
