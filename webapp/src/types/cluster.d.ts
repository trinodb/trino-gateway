export interface BackendData {
  name: string;
  proxyTo: string;
  active: boolean;
  routingGroup: string;
  externalUrl: string;
  queued: number;
  running: number;
}
