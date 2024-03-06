import { api } from "../base";

export async function globalPropertiesApi(body: Record<string, any>): Promise<any> {
  return api.post('/webapp/findGlobalProperty', body)
}

export async function globalPropertyGetApi(body: Record<string, any>): Promise<any> {
  return api.post('/webapp/getGlobalProperty', body)
}

export async function globalPropertySaveApi(body: Record<string, any>): Promise<any> {
  return api.post('/webapp/saveGlobalProperty', body)
}

export async function globalPropertyUpdateApi(body: Record<string, any>): Promise<any> {
  return api.post('/webapp/updateGlobalProperty', body)
}

export async function globalPropertyDeleteApi(body: Record<string, any>): Promise<any> {
  return api.post('/webapp/deleteGlobalProperty', body)
}
