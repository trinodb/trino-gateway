export interface ResourceGroupData {
  resourceGroupId: number;
  name: string;
  parent: number;
  jmxExport: boolean;
  schedulingPolicy: string;
  schedulingWeight: number;
  softMemoryLimit: string;
  maxQueued: number;
  hardConcurrencyLimit: number;
  softConcurrencyLimit: number;
  softCpuLimit: string;
  hardCpuLimit: string;
  environment: string;
}
