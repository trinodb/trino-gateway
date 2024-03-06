import { SelectorData } from "../../types/selector";
import { api } from "../base";

export async function selectorsApi(body: Record<string, any>): Promise<SelectorData[]> {
  return api.post('/webapp/findSelector', body)
}

export async function selectorGetApi(body: Record<string, any>): Promise<any> {
  return api.post('/webapp/getSelector', body)
}

export async function selectorSaveApi(body: Record<string, any>): Promise<any> {
  return api.post('/webapp/saveSelector', body)
}

export async function selectorUpdateApi(body: Record<string, any>): Promise<any> {
  return api.post('/webapp/updateSelector', body)
}

export async function selectorDeleteApi(body: Record<string, any>): Promise<any> {
  return api.post('/webapp/deleteSelector', body)
}
