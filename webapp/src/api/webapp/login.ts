import { api } from "../base";

export async function loginFormApi(body: Record<string, any>): Promise<any> {
  return api.post('/login', body)
}

export async function logoutApi(body: Record<string, any>): Promise<any> {
  return api.post('/logout', body)
}

export async function loginOAuthApi(body: Record<string, any>): Promise<string> {
  return api.post('/sso', body)
}

export async function getInfoApi(): Promise<any> {
  return api.post('/userinfo', {})
}

export async function loginTypeApi(): Promise<any> {
  return api.post('/loginType', {})
}
