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
  const formatString = `${time >= 60 * 60 ? "hh:m" : ""}m:ss${
    showMilliseconds ? ".SS" : ""
  }`;

  return moment()
    .startOf("day")
    .millisecond(time * 1000)
    .format(formatString);
};
