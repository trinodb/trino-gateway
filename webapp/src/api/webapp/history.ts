import { HistoryData } from "../../types/history";
import { api } from "../base";

export async function queryHistoryApi(body: Record<string, any>): Promise<HistoryData> {
  return api.post('/webapp/findQueryHistory', body)
}
