import { api } from "../base";

export async function loginApi(body: Record<string, any>) {
  return api.post('/rest/login', body)
}

export async function logoutApi(body: Record<string, any>) {
  // return api.post('/user/logout', body)
  console.log('logoutApi', body)
  return {}
}

export async function getInfoApi() {
  // return api.get('/user/getInfo')
  console.log('getInfoApi')
  return {
    userId: "1",
    userName: "admin",
    nickName: "admin",
    userType: "0",
    email: "admin@xx.com",
    phonenumber: "",
    sex: "",
    avatar: "",
    permissions: [],
    roles: [],
  }
}

