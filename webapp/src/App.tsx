import "./styles/globals.scss";
import { ErrorBoundary } from './components/error';
import {
  HashRouter as Router,
  Routes,
  Route,
  Navigate
} from "react-router-dom";
import { Login } from './components/login';
import { RootLayout as Layout } from './components/layout';
import { hasPagePermission, routers } from './router';
import { Empty, LocaleProvider } from '@douyinfe/semi-ui';
import { getSemiLang } from './locales';
import { useAccessStore, useConfigStore } from './store';
import { useEffect } from 'react';
import { getCSSVar } from './utils/utils';
import { IllustrationIdle, IllustrationIdleDark } from '@douyinfe/semi-illustrations';
import Cookies from 'js-cookie';
import { TimezoneProvider } from "./components/TimezoneContext";

function App() {
  return (
    <>
      <ErrorBoundary>
        <LocaleProvider locale={getSemiLang()}>
          <TimezoneProvider>
            <Router>
              <Screen />
            </Router>
          </TimezoneProvider>
        </LocaleProvider >
      </ErrorBoundary>
    </>
  )
}

function Screen() {
  useSwitchTheme()
  const access = useAccessStore();
  useEffect(() => {
    const token = Cookies.get('token');
    if (token) {
      access.updateToken(token);
      Cookies.remove('token');
    }
  }, [])
  return (
    <>
      {access.isAuthorized() ? (
        <Layout>
          <Routes>
            {routers.flatMap(router => {
              return hasPagePermission(router, access) ? [<Route {...router.routeProps} key={router.itemKey} />] : [];
            })}
            {/* Landing page */}
            <Route path="/" element={<Navigate to="/dashboard" />} />
            {/* Default page */}
            <Route path="*" element={<Home />} key={"*"} />
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

function Home() {
  const access = useAccessStore();
  return (
    <div style={{ margin: '100px' }}>
      <Empty
        image={<IllustrationIdle style={{ width: 250, height: 250 }} />}
        darkModeImage={<IllustrationIdleDark style={{ width: 250, height: 250 }} />}
        description={`Welcome, ${access.userName} 🌻🌻🌻`}
        style={{ fontSize: '22px' }}
      />
    </div>
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
