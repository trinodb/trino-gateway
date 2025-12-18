import { createContext, useContext, useMemo, useState } from "react";
import { Form } from "@douyinfe/semi-ui";
import Locale from "../locales";
import {getTimeDisplayZoneOptions} from "../utils/time";

export const TimezoneContext = createContext<{
  timezone: string;
  changeTimezone: (tz: string) => void;
}>({ timezone: 'UTC', changeTimezone: () => {} });

export function TimezoneProvider({ children }: { children: React.ReactNode }) {
  const [timezone, setTimezone] = useState<string>(Intl.DateTimeFormat().resolvedOptions().timeZone);
  const changeTimezone = (newTZ: string) => { setTimezone(newTZ) };
  const value = useMemo(() => ({ timezone, changeTimezone }), [timezone, changeTimezone]);

  return (
    <TimezoneContext.Provider value={value}>
      {children}
    </TimezoneContext.Provider>
  );
}

export function TimezoneDropdown() {
  const { timezone, changeTimezone } = useContext(TimezoneContext);
  const timeZones: string[] = getTimeDisplayZoneOptions();

  return (
    <Form<{ timezone: string }>
      initValues={{ timezone }}
      onValueChange={(value) => { changeTimezone(value.timezone); }}
      defaultValue={timezone}
      render={() => (
        <>
          <Form.Select field="timezone" label={Locale.Dashboard.TimeZone} labelPosition="left" style={{ width: 200 }} initValue={timezone} placeholder={timezone} filter>
            {timeZones.map((tz) => (<Form.Select.Option key={tz} value={tz}>{tz}</Form.Select.Option>))}
          </Form.Select>
        </>
      )}
      >
    </Form>
  )
}
