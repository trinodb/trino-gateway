import en_US from "./en_US";
import { merge } from "../utils/merge";
import type { LocaleType } from "./en_US";
export type { LocaleType, PartialLocaleType } from "./en_US";
import { Locale } from '@douyinfe/semi-ui/lib/es/locale/interface';

import semi_en_US from '@douyinfe/semi-ui/lib/es/locale/source/en_US';

const ALL_LANGS = {
  en_US,
};

export type Lang = keyof typeof ALL_LANGS;

export const AllLangs = Object.keys(ALL_LANGS) as Lang[];

export const ALL_LANG_OPTIONS: Record<Lang, string> = {
  en_US: "English",
};

export const SERVER_LAND_MAPPER: Record<Lang, string> = {
  en_US: "en_US",
};

export const SEMI_LAND_MAPPER: Record<Lang, Locale> = {
  en_US: semi_en_US,
};

const LANG_KEY = "lang";
const DEFAULT_LANG = "en_US";

const fallbackLang = en_US;
const targetLang = ALL_LANGS[getLang()] as LocaleType;

// if target lang missing some fields, it will use fallback lang string
merge(fallbackLang, targetLang);

export default fallbackLang as LocaleType;

function getItem(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function setItem(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    console.error('error')
  }
}

function getLanguage(): string {
  try {
    return navigator.language.toLowerCase();
  } catch {
    return DEFAULT_LANG;
  }
}

export function getLang(): Lang {
  const savedLang = getItem(LANG_KEY);

  if (AllLangs.includes((savedLang ?? "") as Lang)) {
    return savedLang as Lang;
  }

  const lang = getLanguage();

  for (const option of AllLangs) {
    if (lang.includes(option)) {
      return option;
    }
  }

  return DEFAULT_LANG;
}

export function getServerLang(): string {
  return SERVER_LAND_MAPPER[getLang()]
}

export function getSemiLang(): Locale {
  return SEMI_LAND_MAPPER[getLang()]
}

export function changeLang(lang: Lang): void {
  setItem(LANG_KEY, lang);
  location.reload();
}
