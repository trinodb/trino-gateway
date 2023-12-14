import { IconHeart, IconIntro, IconPopover, IconScrollList, IconToast } from "@douyinfe/semi-icons-lab";
import { NavItemProps, NavItemPropsWithItems, SubNavProps } from "@douyinfe/semi-ui/lib/es/navigation";
import styles from './components/layout.module.scss';
import { RouteProps } from "react-router-dom";

import Locale from "./locales";

import { Dashboard } from './components/dashboard';
import { Cluster } from './components/cluster';
import { History } from './components/history';
import { Selector } from "./components/selector";
import { ResourceGroup } from "./components/resource-group";


export interface SubItemItem extends NavItemPropsWithItems {
  routeProps: RouteProps,
}

export interface SubRouterItem extends SubNavProps {
  items: (SubItemItem)[],
  routeProps?: RouteProps,
}

export interface RouterItem extends NavItemProps {
  items?: (SubItemItem)[],
  routeProps: RouteProps,
}

export type RouterItems = (RouterItem | SubRouterItem)[]

export const routers: RouterItems = [
  {
    itemKey: '',
    text: Locale.Menu.Sider.Dashboard,
    icon: <IconIntro className={styles.icon} />,
    routeProps: {
      path: '/',
      element: < Dashboard />
    },
  },
  {
    itemKey: 'cluster',
    text: Locale.Menu.Sider.Cluster,
    icon: <IconToast className={styles.icon} />,
    routeProps: {
      path: '/cluster',
      element: < Cluster />
    },
  },
  {
    itemKey: 'resource_group',
    text: Locale.Menu.Sider.ResourceGroup,
    icon: <IconPopover className={styles.icon} />,
    routeProps: {
      path: '/resource-group',
      element: < ResourceGroup />
    },
  },
  {
    itemKey: 'selector',
    text: Locale.Menu.Sider.Selector,
    icon: <IconScrollList className={styles.icon} />,
    routeProps: {
      path: '/selector',
      element: < Selector />
    },
  },
  {
    itemKey: 'history',
    text: Locale.Menu.Sider.History,
    icon: <IconHeart className={styles.icon} />,
    routeProps: {
      path: '/history',
      element: < History />
    },
  }
]