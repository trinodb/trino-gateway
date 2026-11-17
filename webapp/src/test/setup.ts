import { afterEach, vi } from "vitest";
import { cleanup } from "@testing-library/react";

// Mock localStorage for zustand persist middleware (must run before module collection)
const localStorageMock: Storage = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => {
      store[key] = value;
    },
    removeItem: (key: string) => {
      delete store[key];
    },
    clear: () => {
      store = {};
    },
    get length() {
      return Object.keys(store).length;
    },
    key: (index: number) => Object.keys(store)[index] ?? null,
  };
})();

vi.stubGlobal("localStorage", localStorageMock);

afterEach(() => {
  cleanup();
  localStorageMock.clear();
});
