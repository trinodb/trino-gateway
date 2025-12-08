import moment from "moment";
import {getTimeZones} from "@vvo/tzdb";

export const formatTime = (
  time: number,
  options: { showMilliseconds?: boolean; extra?: number },
) => {
  options = {
    showMilliseconds: false,
    ...options,
  };

  const durationFormatted = options.extra
    ? `  (${format(options.extra, options)})`
    : "";

  return `${format(time, options)}${durationFormatted}`;
};

const format = (
  time: number,
  { showMilliseconds }: { showMilliseconds?: boolean } = {},
) => {
  const formatString = `${time >= 60 * 60 ? "hh:m" : ""}m:ss${showMilliseconds ? ".SS" : ""
    }`;

  return moment()
    .startOf("day")
    .millisecond(time * 1000)
    .format(formatString);
};

const formatDateComponents = (date: Date): string => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const seconds = String(date.getSeconds()).padStart(2, '0');
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}

export const formatTimestamp = (timestamp: number): string => {
  const date = new Date(timestamp);
  return formatDateComponents(date);
}

export const formatZonedTimestamp = (timestamp: number, timezone: string): string => {
    const date = new Date(timestamp);
    const formatter = new Intl.DateTimeFormat('en-US', {
        timeZone: timezone, hour12: false, hour: '2-digit', minute: '2-digit',
    });
    return formatter.format(date);
}

export const formatZonedDateTime = (zonedDateTime: string, timeZone: string): string => {
    const date = new Date(zonedDateTime);
    const zonedDate  = new Date(date.toLocaleString("en-US", { timeZone }));
    return formatDateComponents(zonedDate);
}

export const getTimeDisplayZoneOptions = (): string[] => {
    const timeZones = getTimeZones({ includeUtc: true });
    return timeZones.map(tz => tz.name).sort();
};
