import {Nav, Avatar, Layout, Dropdown, Button, Toast, Select} from '@douyinfe/semi-ui';
import { IconGithubLogo, IconDoubleChevronRight, IconDoubleChevronLeft, IconMoon, IconSun, IconMark } from '@douyinfe/semi-icons';
import styles from './layout.module.scss';
import { useState } from 'react';
import { Link } from "react-router-dom";
import { routers } from '../router';
import { Theme, useAccessStore, useConfigStore } from '../store';
import { logoutApi } from '../api/webapp/login';
import Locale, { ALL_LANG_OPTIONS, AllLangs, Lang, changeLang, getLang } from "../locales";

// import LogoIcon from "../assets/logo.svg?react";


export const RootLayout = (props: {
  children: React.ReactNode
}) => {
  const access = useAccessStore();
  const config = useConfigStore();

  const { Header, Sider, Content } = Layout;
  const [collapsed, setCollapsed] = useState(false)
  const [selectedKey, setSelectedKey] = useState('')

  const lonout = () => {
    logoutApi({}).then(data => {
      console.log(data)
      access.updateToken("");
      Toast.success(Locale.Auth.LogoutSuccess);
    }).catch(() => { });
  }

  const theme = config.theme;
  function nextTheme() {
    const themes = [Theme.Auto, Theme.Light, Theme.Dark];
    const themeIndex = themes.indexOf(theme);
    const nextIndex = (themeIndex + 1) % themes.length;
    const nextTheme = themes[nextIndex];
    config.update((config) => (config.theme = nextTheme));
  }

  return (
    <Layout>
      <Header className={styles.header}>
        <Nav
          mode="horizontal"
          header={{
            logo: (
              <div className={styles.navigationHeaderLogo}>
                {/* <LogoIcon /> */}
                {/* <Icon svg={<LogoIcon />} className={styles.semiIconsSemiLogo} /> */}
              </div>
            ),
            text: "Trino Gateway",
          }}
          footer={
            <div className={styles.dIV}>
              <Select defaultValue={getLang()} onSelect={(e) => (
                changeLang(e as Lang)
              )}>
                {AllLangs.map((lang) => (
                  <Select.Option value={lang} key={lang}>{ALL_LANG_OPTIONS[lang]}</Select.Option>
                ))}
              </Select>

              <Button icon={
                theme === Theme.Auto ? (
                  <IconMark className={styles.semiIconsBell} />
                ) : theme === Theme.Light ? (
                  <IconSun className={styles.semiIconsBell} />
                ) : theme === Theme.Dark ? (
                  <IconMoon className={styles.semiIconsBell} />
                ) : null}
                aria-label="Switch Theme"
                onClick={nextTheme} />

              <Button icon={<IconGithubLogo className={styles.semiIconsBell} />}
                aria-label="GitHub"
                onClick={() => window.open('https://github.com/ytwp/semi-admin-template', '_blank')} />

              <Dropdown
                position={'bottomRight'}
                render={
                  <Dropdown.Menu>
                    <Dropdown.Item>{Locale.Menu.Header.PersonalCenter}</Dropdown.Item>
                    <Dropdown.Item onClick={lonout}>{Locale.Menu.Header.Logout}</Dropdown.Item>
                  </Dropdown.Menu>
                }
              >
                <Avatar
                  size="small"
                  src={access.avatar || config.avatar}
                  color="blue"
                  className={styles.avatar}
                >
                  {access.nickName}
                </Avatar>
              </Dropdown>
            </div>
          }
          className={styles.nav}
        >
        </Nav>
      </Header>
      <Layout hasSider={true}>
        <Sider className={styles.sider}>
          <Nav
            defaultSelectedKeys={[selectedKey]}
            selectedKeys={[selectedKey]}
            onSelect={(e) => { setSelectedKey(e.itemKey.toString()) }}
            mode="vertical"
            className={styles.nav}
            isCollapsed={collapsed}
            onCollapseChange={(e) => { console.log(e) }}
            renderWrapper={({ itemElement, props }) => {
              // eslint-disable-next-line @typescript-eslint/ban-ts-comment
              // @ts-ignore
              if (props.routeProps) {
                return (
                  <Link
                    style={{ textDecoration: "none" }}
                    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
                    // @ts-ignore
                    to={props.routeProps.path}
                  >
                    {itemElement}
                  </Link>
                );
              } else {
                return itemElement
              }
            }}
            items={routers}
          >
            <Nav.Footer style={{ padding: 0 }}>
              {collapsed ? (
                <div className={styles.collapsed} style={{ justifyContent: 'center' }}>
                  <IconDoubleChevronRight onClick={() => setCollapsed(false)} />
                </div>
              ) : (
                <div className={styles.collapsed} style={{ justifyContent: 'flex-end' }}>
                  <IconDoubleChevronLeft onClick={() => setCollapsed(true)} style={{ paddingRight: '12px' }} />
                </div>
              )}
            </Nav.Footer>
          </Nav>
        </Sider>
        <Content className={styles.content} style={{ marginLeft: collapsed ? '60px' : '240px' }}>
          {props.children}
        </Content>
      </Layout>
    </Layout >
  );
}
