import { create } from "zustand";
import { persist } from "zustand/middleware";
import { StoreKey } from "../constant";

export enum Theme {
  Auto = "auto",
  Dark = "dark",
  Light = "light",
}

export const DEFAULT_CONFIG = {
  avatar: "https://sf6-cdn-tos.douyinstatic.com/obj/eden-cn/ptlz_zlp/ljhwZthlaukjlkulzlp/root-web-sites/avatarDemo.jpeg",
  theme: Theme.Auto as Theme,

  fontSize: 14,
  sidebarWidth: 270,
};

export type AppConfig = typeof DEFAULT_CONFIG;

export type AppConfigStore = AppConfig & {
  reset: () => void;
  update: (updater: (config: AppConfig) => void) => void;
};

export const useConfigStore = create<AppConfigStore>()(
  persist(
    (set, get) => ({
      ...DEFAULT_CONFIG,

      reset() {
        set(() => ({ ...DEFAULT_CONFIG }));
      },

      update(updater) {
        const config = { ...get() };
        updater(config);
        set(() => config);
      },
    }),
    {
      name: StoreKey.Config,
      version: 1,
      migrate(persistedState, version) {
        const state = persistedState as AppConfig;

        if (version < 1) {
          // marge your old config
        }

        return state as any;
      },
    },
  ),
);
