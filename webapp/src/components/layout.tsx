import { Nav, Avatar, Layout, Dropdown, Button, Toast, Modal, Tag } from '@douyinfe/semi-ui';
import { IconDoubleChevronRight, IconDoubleChevronLeft, IconMoon, IconSun, IconMark, IconIdCard, IconUserSetting, IconUser } from '@douyinfe/semi-icons';
import styles from './layout.module.scss';
import { useEffect, useState } from 'react';
import { Link, useLocation } from "react-router-dom";
import { hasPagePermission, routers, routersMapper } from '../router';
import { Theme, useAccessStore, useConfigStore } from '../store';
import { getUIConfiguration, logoutApi } from '../api/webapp/login';
import Locale from "../locales";
import { TimezoneDropdown } from "./TimezoneContext";

export const RootLayout = (props: {
  children: React.ReactNode
}) => {
  const access = useAccessStore();
  const config = useConfigStore();
  const location = useLocation();
  const { Header, Sider, Content } = Layout;
  const [collapsed, setCollapsed] = useState(false);
  const [selectedKey, setSelectedKey] = useState(location.pathname.substring(location.pathname.lastIndexOf('/') + 1));
  const [userProfile, setUserProfile] = useState(false);
  const [disabledPages, setDisabledPages] = useState<string[]>(['']);
  const [filteredRouters, setFilteredRouters] = useState(routers);

  useEffect(() => {
      getUIConfiguration().then((res) => {
          if (Object.keys(res).length == 0) {
              setDisabledPages(res)
          } else {
              setDisabledPages(res.disablePages)
          }
      })
  }, []);

    useEffect(() => {
        const routerFilters = disabledPages.length > 0 ?
                routers
                        .filter(router => router.itemKey && !disabledPages.includes(router.itemKey))
                        .filter(router => hasPagePermission(router, access))
                : routers
                        .filter(router => hasPagePermission(router, access))
        setFilteredRouters(routerFilters);
    }, [disabledPages, access]);

  useEffect(() => {
    const router = routersMapper[location.pathname];
    if (router && router.itemKey != null && selectedKey !== router.itemKey) {
      setSelectedKey(router.itemKey);
    }
  }, [location]);

  const logout = () => {
    logoutApi({}).then(() => {
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
    <>
      <Layout>
        <Header className={styles.header}>
          <Nav
            mode="horizontal"
            header={{
              logo: (
                <img
                  src="/trino-gateway/logo.svg"
                  className={styles.navigationHeaderLogo}
                />
              ),
              text: (
                <Link to="/" style={{ textDecoration: "none" }}>
                  Trino Gateway
                </Link>
              ),
            }}
            footer={
              <div className={styles.dIV}>
                <TimezoneDropdown />
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

                <Dropdown
                  position={'bottomRight'}
                  render={
                    <Dropdown.Menu>
                      <Dropdown.Item onClick={() => { setUserProfile(true) }}>{Locale.Menu.Header.PersonalCenter}</Dropdown.Item>
                      <Dropdown.Item onClick={logout}>{Locale.Menu.Header.Logout}</Dropdown.Item>
                    </Dropdown.Menu>
                  }
                >
                {access.roles.includes('ADMIN') ? (
                    <Button icon={<IconUserSetting
                        size="extra-large"
                        color="orange"
                        className={styles.semiIconsBell} />}>
                    </Button>
                    ) : (
                    <Button icon={<IconUser
                        size="extra-large"
                        color="blue"
                        className={styles.semiIconsBell} />}>
                    </Button>
                 )}
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
              items={filteredRouters}
            >
              <Nav.Footer style={{ padding: 0 }}>
                {collapsed ? (
                  <div className={styles.collapsed} style={{ justifyContent: 'center' }} onClick={() => setCollapsed(false)}>
                    <IconDoubleChevronRight />
                  </div>
                ) : (
                  <div className={styles.collapsed} style={{ justifyContent: 'flex-end' }} onClick={() => setCollapsed(true)}>
                    <IconDoubleChevronLeft style={{ paddingRight: '12px' }} />
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

      <Modal
        visible={userProfile}
        hasCancel={false}
        onCancel={() => setUserProfile(false)}
        width={400}
        height={400}
        closable={false}
        footer={<></>}
      >
        <div className={styles.userProfile}>
          <div className={styles.banner}>
            <div className={styles.frame4159}>
              <Avatar
                size="large"
                src={access.avatar || config.avatar}
                color="blue"
                className={styles.avatar}
              >
                {access.userName}
              </Avatar>
            </div>
            <div className={styles.name}>
              <p className={styles.richardHendricks}>{access.userName}</p>
            </div>
          </div>
          <div className={styles.main}>
            <div className={styles.descriptions}>
              <div className={styles.frame4152}>
                <IconIdCard className={styles.semiIconsMapPin} />
                <p className={styles.value}>{access.userId}</p>
              </div>
            </div>
            <div className={styles.tags}>
              {access.roles.map(role => (
                <Tag size="large" key={role} color={role.toUpperCase() == 'ADMIN' ? "orange" : "blue"} className={styles.tag2}>{role}</Tag>
              ))}
            </div>
          </div>
        </div>
      </Modal>
    </>
  );
}
