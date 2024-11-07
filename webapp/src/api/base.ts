import { useAccessStore } from "../store";
import Locale, { getServerLang } from "../locales";
import { Toast } from "@douyinfe/semi-ui";

export class ClientApi {
  async get(url: string, params: Record<string, any> = {}): Promise<any> {
    let queryString = "";
    if (Object.keys(params).length > 0) {
      queryString = "?" + new URLSearchParams(params).toString();
    }
    const res: Response = await fetch(
      this.path(url + queryString),
      {
        headers: getHeaders(),
        method: "GET"
      });
    if (res.status === 401 || res.status === 403) {
      this.authErrorHandler()
    }
    else if (res.status !== 200) {
      Toast.error({
        content: Locale.Error.Network,
        duration: 5,
        theme: "light"
      });
      throw new Error(Locale.Error.Network);
    }
    const resJson = await res.json();
    if (resJson.code === 401 || resJson.code === 403) {
      this.authErrorHandler()
    } else if (resJson.code !== 200) {
      Toast.error({
        content: resJson.msg,
        duration: 5,
        theme: "light"
      });
      throw new Error(resJson.msg);
    }
    return resJson.data;
  }

  async post(url: string, body: Record<string, any> = {}): Promise<any> {
    const res: Response = await fetch(
      this.path(url),
      {
        body: JSON.stringify(body),
        headers: {
          "Content-Type": "application/json",
          ...getHeaders()
        },
        method: "POST"
      });
    if (res.status === 401 || res.status === 403) {
      this.authErrorHandler()
    }
    else if (res.status !== 200) {
      Toast.error({
        content: Locale.Error.Network,
        duration: 5,
        theme: "light"
      });
      throw new Error(Locale.Error.Network);
    }
    const resJson = await res.json();
    if (resJson.code === 401 || resJson.code === 403) {
      this.authErrorHandler()
    } else if (resJson.code !== 200) {
      Toast.error({
        content: resJson.msg,
        duration: 5,
        theme: "light"
      });
      throw new Error(resJson.msg);
    }
    return resJson.data;
  }

  async postForm(url: string, formData: FormData = new FormData()): Promise<any> {
    const res: Response = await fetch(
      this.path(url),
      {
        body: formData,
        headers: getHeaders(),
        method: "POST",
        redirect: "follow"
      });
    if (res.status !== 200) {
      Toast.error({
        content: Locale.Error.Network,
        duration: 5,
        theme: "light"
      });
      throw new Error(Locale.Error.Network);
    }
    const resJson = await res.json();
    if (resJson.code !== 200) {
      Toast.error({
        content: resJson.msg,
        duration: 5,
        theme: "light"
      });
      throw new Error(resJson.msg);
    }
    return resJson.data;
  }

  path(path: string): string {
    const proxyPath = import.meta.env.VITE_PROXY_PATH;
    return [proxyPath, path].join("");
  }

  authErrorHandler(): void {
    Toast.error({
      content: Locale.Auth.Expiration,
      duration: 5,
      theme: "light"
    });
    const accessStore = useAccessStore.getState();
    accessStore.updateToken("");
    throw new Error(Locale.Auth.Expiration);
  }
}

export const api = new ClientApi();

export function getHeaders(): Record<string, string> {
  const accessStore = useAccessStore.getState();
  const headers: Record<string, string> = {
    "x-requested-with": "XMLHttpRequest",
    "Content-Language": getServerLang(),
  };

  const makeBearer = (token: string) => `Bearer ${token.trim()}`;
  const validString = (x: string) => x && x.length > 0;

  if (validString(accessStore.token)) {
    headers.Authorization = makeBearer(accessStore.token);
  }

  return headers;
}
