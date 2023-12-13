import { api } from "../base";

export async function queryHistoryApi(body: Record<string, any>) {
  return api.post('/webapp/getQueryHistory', body)
}
