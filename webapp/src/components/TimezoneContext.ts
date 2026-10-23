import { createContext } from "react";

export const TimezoneContext = createContext<{
  timezone: string;
  changeTimezone: (tz: string) => void;
}>({ timezone: 'UTC', changeTimezone: () => {} });
