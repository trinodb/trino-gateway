import { ResourceGroupData } from "../../types/resource-group";
import { api } from "../base";

export async function resourceGroupsApi(body: Record<string, any>): Promise<ResourceGroupData[]> {
  return api.post('/webapp/findResourceGroup', body)
}

export async function resourceGroupGetApi(body: Record<string, any>): Promise<any> {
  return api.post('/webapp/getResourceGroup', body)
}

export async function resourceGroupSaveApi(body: Record<string, any>): Promise<any> {
  return api.post('/webapp/saveResourceGroup', body)
}

export async function resourceGroupUpdateApi(body: Record<string, any>): Promise<any> {
  return api.post('/webapp/updateResourceGroup', body)
}

export async function resourceGroupDeleteApi(body: Record<string, any>): Promise<any> {
  return api.post('/webapp/deleteResourceGroup', body)
}
