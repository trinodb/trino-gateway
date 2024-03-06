import { DistributionDetail } from "../../types/dashboard";
import { api } from "../base";

export async function distributionApi(body: Record<string, any>): Promise<DistributionDetail> {
  return api.post('/webapp/getDistribution', body)
}
