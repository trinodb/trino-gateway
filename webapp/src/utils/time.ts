import moment from "moment";

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

export const formatYYYYMMddHHMMSS = (timestamp: number): string => {
  const date = new Date(timestamp);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const seconds = String(date.getSeconds()).padStart(2, '0');
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}
