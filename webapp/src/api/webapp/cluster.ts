import { BackendData } from "../../types/cluster";
import { api } from "../base";

export async function backendsApi(body: Record<string, any>): Promise<BackendData[]> {
  return api.post('/webapp/getAllBackends', body)
}

export async function backendSaveApi(body: Record<string, any>): Promise<any> {
  return api.post('/webapp/saveBackend', body)
}

export async function backendUpdateApi(body: Record<string, any>): Promise<any> {
  return api.post('/webapp/updateBackend', body)
}

export async function backendDeleteApi(body: Record<string, any>): Promise<boolean> {
  return api.post('/webapp/deleteBackend', body)
}
