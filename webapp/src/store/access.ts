import { create } from "zustand";
import { getInfoApi } from "../api/webapp/login";

export enum Role {
  ADMIN = "ADMIN",
  API = "API",
  USER = "USER",
}

export interface AccessControlStore {
  token: string;
  status: "loading" | "authenticated" | "unauthenticated";

  userId: string;
  userName: string;
  nickName: string;
  userType: string;
  email: string;
  phonenumber: string;
  sex: string;
  avatar: string;
  permissions: string[];
  roles: string[];

  updateToken: (_: string) => void;
  clearSession: () => void;
  isAuthorized: () => boolean;
  loadUserInfo: () => Promise<void>;
  hasRole: (role: Role) => boolean;
  hasPermission: (permission: string | undefined) => boolean;
}

export const useAccessStore = create<AccessControlStore>()(
    (set, get) => ({
      token: "",
      status: "loading",

      userId: "",
      userName: "",
      nickName: "",
      userType: "",
      email: "",
      phonenumber: "",
      sex: "",
      avatar: "",
      permissions: [],
      roles: [],

      updateToken(token: string) {
        set(() => ({ token: token?.trim(), status: "loading" }));
        if (get().token) {
          void get().loadUserInfo();
        }
      },
      clearSession() {
        set(() => ({
          token: "",
          status: "unauthenticated",
          userId: "",
          userName: "",
          roles: [],
          permissions: [],
        }));
      },
      isAuthorized() {
        return get().status === "authenticated";
      },
      async loadUserInfo() {
        set(() => ({ status: "loading" }));
        try {
          const data = await getInfoApi();
          set(() => ({ ...data, status: "authenticated" }));
        } catch {
          get().clearSession();
        }
      },
      hasRole(role: Role) {
        return get().roles.includes(role);
      },
      hasPermission(permission: string | undefined) {
        const permissions = get().permissions
        return permission == undefined || permissions == null || permissions.length == 0 || permissions.includes(permission);
      },
    }),
);
