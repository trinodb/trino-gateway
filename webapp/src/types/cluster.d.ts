interface BackendData {
  checked: any;
  name: string;
  proxyTo: string;
  active: boolean;
  routingGroup: string;
  externalUrl: string;

  queued: number;
  running: number;
}
