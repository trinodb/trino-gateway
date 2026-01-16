import { createContext, useCallback, useContext, useState } from "react";
import { Typography, Select } from "@douyinfe/semi-ui";
import Locale from "../locales";
import {getTimeDisplayZoneOptions} from "../utils/time";

export const TimezoneContext = createContext<{
  timezone: string;
  changeTimezone: (tz: string) => void;
}>({ timezone: 'UTC', changeTimezone: () => {} });

export function TimezoneProvider({ children }: { children: React.ReactNode }) {
  const [timezone, setTimezone] = useState<string>(() => Intl.DateTimeFormat().resolvedOptions().timeZone ?? 'UTC');
  const changeTimezone = useCallback((newTZ: string) => { setTimezone(newTZ) }, []);
  const value = ({ timezone, changeTimezone });
  return (
    <TimezoneContext.Provider value={value}>
      {children}
    </TimezoneContext.Provider>
  );
}

export function TimezoneDropdown() {
  const { timezone, changeTimezone } = useContext(TimezoneContext);
  const timeZones = getTimeDisplayZoneOptions();
  return (
    <>
      <Typography.Text strong>{Locale.Dashboard.TimeZone}</Typography.Text>
      <Select
        style={{ width: 200 }}
        value={timezone}
        filter
        onChange={(v) => changeTimezone(String(v))}
      >
        {timeZones.map((tz) => (<Select.Option key={tz} value={tz}>{tz}</Select.Option>))}</Select>
    </>
  );
}
