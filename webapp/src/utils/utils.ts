import { useEffect, useState } from "react";
import Locale from "../locales";
import { Toast } from "@douyinfe/semi-ui";

export function trimTopic(topic: string): string {
  return topic.replace(/[，。！？”“"、,.!?]*$/, "");
}

export async function copyToClipboard(text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text);
    Toast.success(Locale.Copy.Success);
  } catch (error) {
    const textArea = document.createElement("textarea");
    textArea.value = text;
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
    try {
      document.execCommand("copy");
      Toast.success(Locale.Copy.Success);
    } catch (error) {
      Toast.error(Locale.Copy.Failed);
    }
    document.body.removeChild(textArea);
  }
}

export function downloadAs(text: string, filename: string): void {
  const element = document.createElement("a");
  element.setAttribute(
    "href",
    "data:text/plain;charset=utf-8," + encodeURIComponent(text),
  );
  element.setAttribute("download", filename);
  element.style.display = "none";
  document.body.appendChild(element);
  element.click();
  document.body.removeChild(element);
}

export function isIOS(): boolean {
  const userAgent = navigator.userAgent.toLowerCase();
  return /iphone|ipad|ipod/.test(userAgent);
}

export function isIPad(): boolean {
  const userAgent = navigator.userAgent.toLowerCase();
  return /ipad/.test(userAgent);
}

export function isPc(): boolean {
  const userAgent = navigator.userAgent.toLowerCase();
  return /mac|windows/.test(userAgent);
}

export function containsChinese(str: string): boolean {
  const pattern = /[\u4e00-\u9fa5]/;
  return pattern.test(str);
}

export function useWindowSize(): { width: number, height: number } {
  const [size, setSize] = useState({
    width: window.innerWidth,
    height: window.innerHeight,
  });

  useEffect(() => {
    const onResize = () => {
      setSize({
        width: window.innerWidth,
        height: window.innerHeight,
      });
    };
    window.addEventListener("resize", onResize);
    return () => {
      window.removeEventListener("resize", onResize);
    };
  }, []);

  return size;
}

export const MOBILE_MAX_WIDTH = 600;
export function useMobileScreen(): boolean {
  const { width } = useWindowSize();
  return width <= MOBILE_MAX_WIDTH;
}

export const FULL_SCREEN_MAX_WIDTH = 1080;
export function useFullScreen(): boolean {
  const { width } = useWindowSize();
  return width <= FULL_SCREEN_MAX_WIDTH;
}

export function isFirefox(): boolean {
  return (
    typeof navigator !== "undefined" && /firefox/i.test(navigator.userAgent)
  );
}

export function selectOrCopy(_el: HTMLElement, content: string): boolean {
  const currentSelection = window.getSelection();
  if (currentSelection?.type === "Range") {
    return false;
  }
  copyToClipboard(content);
  return true;
}

function getDomContentWidth(dom: HTMLElement): number {
  const style = window.getComputedStyle(dom);
  const paddingWidth = parseFloat(style.paddingLeft) + parseFloat(style.paddingRight);
  const width = dom.clientWidth - paddingWidth;
  return width;
}

function getOrCreateMeasureDom(id: string, init?: (dom: HTMLElement) => void): HTMLElement {
  let dom = document.getElementById(id);
  if (!dom) {
    dom = document.createElement("span");
    dom.style.position = "absolute";
    dom.style.wordBreak = "break-word";
    dom.style.fontSize = "14px";
    dom.style.transform = "translateY(-200vh)";
    dom.style.pointerEvents = "none";
    dom.style.opacity = "0";
    dom.id = id;
    document.body.appendChild(dom);
    init?.(dom);
  }
  return dom!;
}

export function autoGrowTextArea(dom: HTMLTextAreaElement): number {
  const measureDom = getOrCreateMeasureDom("__measure");
  const singleLineDom = getOrCreateMeasureDom("__single_measure", (dom) => {
    dom.innerText = "TEXT_FOR_MEASURE";
  });

  const width = getDomContentWidth(dom);
  measureDom.style.width = width + "px";
  measureDom.innerText = dom.value !== "" ? dom.value : "1";
  measureDom.style.fontSize = dom.style.fontSize;
  const endWithEmptyLine = dom.value.endsWith("\n");
  const height = parseFloat(window.getComputedStyle(measureDom).height);
  const singleLineHeight = parseFloat(
    window.getComputedStyle(singleLineDom).height,
  );

  const rows = Math.round(height / singleLineHeight) + (endWithEmptyLine ? 1 : 0);
  return rows;
}

export function getCSSVar(varName: string): string {
  return getComputedStyle(document.body).getPropertyValue(varName).trim();
}


let isThrottled = false;
// eslint-disable-next-line @typescript-eslint/ban-types
export function throttle(func: Function, throttleTime: number = 1000): void {
  if (isThrottled) {
    return;
  }
  isThrottled = true;
  func();
  setTimeout(() => {
    isThrottled = false;
  }, throttleTime);
}
