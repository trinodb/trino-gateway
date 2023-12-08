import { create } from "zustand";
import { persist } from "zustand/middleware";
import { StoreKey } from "../constant";
import { getInfoApi } from "../api/user/login";

export interface AccessControlStore {
  token: string;

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
  isAuthorized: () => boolean;
  getUserInfo: (_?: boolean) => void;
}

let fetchState = 0; // 0 not fetch, 1 fetching, 2 done

export const useAccessStore = create<AccessControlStore>()(
  persist(
    (set, get) => ({
      token: "",

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
        set(() => ({ token: token?.trim() }));
        if (get().token) {
          get().getUserInfo(true);
        }
      },
      isAuthorized() {
        get().getUserInfo();
        return (
          !!get().token
        );
      },
      getUserInfo(force: boolean = false) {
        if ((!get().token) || (!force && fetchState > 0)) return;
        fetchState = 1;
        getInfoApi().then((data) => {
          set(() => ({ ...data }));
        }).catch(() => {
          // console.error("[Config] failed to fetch config");
        }).finally(() => {
          fetchState = 2;
        });
      },
    }),
    {
      name: StoreKey.Access,
      version: 1,
      migrate(persistedState, version) {
        const state = persistedState as AccessControlStore;

        if (version < 1) {
          // marge your old config
        }

        return state as any;
      },
    },
  ),
);
