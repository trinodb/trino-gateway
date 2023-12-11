import { api } from "../base";

export async function distributionApi(body: Record<string, any>) {
  return api.post('/webapp/getDistribution', body)
}