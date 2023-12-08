import "./styles/globals.scss";
import { ErrorBoundary } from './components/error';
import {
  HashRouter as Router,
  Routes,
  Route
} from "react-router-dom";
import { Login } from './components/login';
import { RootLayout as Layout } from './components/layout';
import { routers } from './router';
import { LocaleProvider } from '@douyinfe/semi-ui';
import { getSemiLang } from './locales';
import { useAccessStore, useConfigStore } from './store';
import { useEffect } from 'react';
import { getCSSVar } from './utils/utils';


function Screen() {
  useSwitchTheme()
  const access = useAccessStore();
  return (
    <>
      {access.isAuthorized() ? (
        <Layout>
          <Routes>
            {routers.flatMap((router) => {
              if (router.items) {
                return router.items.map((subRouter) => {
                  return <Route {...subRouter.routeProps} key={subRouter.itemKey} />
                })
              } else {
                return [<Route {...router.routeProps} key={router.itemKey} />]
              }
            })}
          </Routes>
        </Layout>
      ) : (
        <>
          <Login />
        </>
      )}
    </>
  );
}

function App() {
  return (
    <>
      <ErrorBoundary>
        <LocaleProvider locale={getSemiLang()}>
          <Router>
            <Screen />
          </Router>
        </LocaleProvider >
      </ErrorBoundary>
    </>
  )
}

function useSwitchTheme() {
  const config = useConfigStore();

  useEffect(() => {
    document.body.classList.remove("light");
    document.body.classList.remove("dark");

    if (config.theme === "dark") {
      document.body.classList.add("dark");
      document.body.setAttribute("theme-mode", "dark");
    } else if (config.theme === "light") {
      document.body.classList.add("light");
      document.body.removeAttribute("theme-mode");
    }

    const metaDescriptionDark = document.querySelector(
      'meta[name="theme-color"][media*="dark"]',
    );
    const metaDescriptionLight = document.querySelector(
      'meta[name="theme-color"][media*="light"]',
    );

    if (config.theme === "auto") {
      metaDescriptionDark?.setAttribute("content", "#151515");
      metaDescriptionLight?.setAttribute("content", "#fafafa");

      if (window.matchMedia("(prefers-color-scheme: dark)").matches) {
        document.body.setAttribute("theme-mode", "dark");
      } else {
        document.body.removeAttribute("theme-mode");
      }
    } else {
      const themeColor = getCSSVar("--theme-color");
      metaDescriptionDark?.setAttribute("content", themeColor);
      metaDescriptionLight?.setAttribute("content", themeColor);
    }
  }, [config.theme]);
}

export default App
