import {
  useAccessStore,
} from "../store";
import Locale, { getServerLang } from "../locales";
import { Toast } from "@douyinfe/semi-ui";

export class ClientApi {

  async get(url: string, params: Record<string, any> = {}) {
    let queryString = "";
    if (Object.keys(params).length > 0) {
      queryString = "?" + new URLSearchParams(params).toString();
    }
    const res: Response = await fetch(this.path(url + queryString), {
      headers: {
        ...getHeaders(),
      },
      method: "GET",
    });
    if (res.status !== 200) {
      Toast.error({
        content: Locale.Error.Network,
        duration: 5,
        theme: "light",
      });
      throw new Error(Locale.Error.Network);
    }
    const resJson = await res.json();
    if (resJson.code === 401) {
      Toast.error({
        content: Locale.Auth.Expiration,
        duration: 5,
        theme: "light",
      });
      const accessStore = useAccessStore.getState();
      accessStore.updateToken("");
      throw new Error(Locale.Auth.Expiration);
    } else if (resJson.code !== 200) {
      Toast.error({
        content: resJson.msg,
        duration: 5,
        theme: "light",
      });
      throw new Error(resJson.msg);
    }
    return resJson.data;
  }

  async post(url: string, body: Record<string, any> = {}) {
    const res: Response = await fetch(this.path(url), {
      body: JSON.stringify(body),
      headers: {
        ...getHeaders(),
      },
      method: "POST",
    });
    if (res.status !== 200) {
      Toast.error({
        content: Locale.Error.Network,
        duration: 5,
        theme: "light",
      });
      throw new Error(Locale.Error.Network);
    }
    const resJson = await res.json();
    if (resJson.code === 401) {
      Toast.error({
        content: Locale.Auth.Expiration,
        duration: 5,
        theme: "light",
      });
      const accessStore = useAccessStore.getState();
      accessStore.updateToken("");
      throw new Error(Locale.Auth.Expiration);
    } else if (resJson.code !== 200) {
      Toast.error({
        content: resJson.msg,
        duration: 5,
        theme: "light",
      });
      throw new Error(resJson.msg);
    }
    return resJson.data;
  }

  async postForm(url: string, formData: FormData = new FormData()) {
    const headers = getHeaders();
    delete headers["Content-Type"];
    const res: Response = await fetch(this.path(url), {
      body: formData,
      headers: headers,
      method: "POST",
      redirect: "follow",
    });
    if (res.status !== 200) {
      Toast.error({
        content: Locale.Error.Network,
        duration: 5,
        theme: "light",
      });
      throw new Error(Locale.Error.Network);
    }
    const resJson = await res.json();
    if (resJson.code !== 200) {
      Toast.error({
        content: resJson.msg,
        duration: 5,
        theme: "light",
      });
      throw new Error(resJson.msg);
    }
    return resJson.data;
  }

  path(path: string): string {
    let baseUrl = import.meta.env.BASE_URL;
    if (baseUrl.endsWith("/")) {
      baseUrl = baseUrl.slice(0, baseUrl.length - 1);
    }
    return [baseUrl, path].join("");
  }
}

export const api = new ClientApi();

export function getHeaders() {
  const accessStore = useAccessStore.getState();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
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
