import { create } from "zustand";

export const DEFAULT_UI_CONFIGURATION = {
  disabledPages: [] as string[],
  allowNonAdminToViewAllQueryHistory: false,
};

export type UIConfiguration = typeof DEFAULT_UI_CONFIGURATION;

export type UIConfigurationState = UIConfiguration & {
  update: (patch: Partial<UIConfiguration>) => void;
  reset: () => void;
};

export const useUIConfigurationStore = create<UIConfigurationState>()((set) => ({
  ...DEFAULT_UI_CONFIGURATION,

  update(patch) {
    set((state) => ({ ...state, ...patch }));
  },

  reset() {
    set(() => ({
      ...DEFAULT_UI_CONFIGURATION,
    }));
  },
}));
