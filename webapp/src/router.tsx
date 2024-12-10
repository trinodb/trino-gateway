import {IconIntro, IconPopover, IconScrollList, IconTree} from "@douyinfe/semi-icons-lab";
import {NavItemProps, NavItemPropsWithItems, SubNavProps} from "@douyinfe/semi-ui/lib/es/navigation";
import styles from './components/layout.module.scss';
import {RouteProps} from "react-router-dom";
import Locale from "./locales";
import {Dashboard} from './components/dashboard';
import {Cluster} from './components/cluster';
import {History} from './components/history';
import {Selector} from "./components/selector";
import {ResourceGroup} from "./components/resource-group";
import {AccessControlStore, Role} from "./store";
import {IconHistory, IconList} from "@douyinfe/semi-icons";
import {RoutingRules} from "./components/routing-rules.tsx";

export interface SubItemItem extends NavItemPropsWithItems {
  routeProps: RouteProps,
  roles: Role[]
}

export interface SubRouterItem extends SubNavProps {
  itemKey?: string;
  items: (SubItemItem)[],
  routeProps?: RouteProps,
  roles: Role[]
}

export interface RouterItem extends NavItemProps {
  itemKey?: string;
  items?: (SubItemItem)[],
  routeProps: RouteProps,
  roles: Role[]
}

export type RouterItems = (RouterItem | SubRouterItem)[]

export const routers: RouterItems = [
  {
    itemKey: 'dashboard',
    text: Locale.Menu.Sider.Dashboard,
    icon: <IconIntro className={styles.icon} />,
    // Role.****
    roles: [],
    routeProps: {
      path: '/dashboard',
      element: < Dashboard />
    },
  },
  {
    itemKey: 'cluster',
    text: Locale.Menu.Sider.Cluster,
    icon: <IconTree className={styles.icon} />,
    roles: [],
    routeProps: {
      path: '/cluster',
      element: < Cluster />
    },
  },
  {
    itemKey: 'resource-group',
    text: Locale.Menu.Sider.ResourceGroup,
    icon: <IconPopover className={styles.icon} />,
    roles: [],
    routeProps: {
      path: '/resource-group',
      element: < ResourceGroup />
    },
  },
  {
    itemKey: 'selector',
    text: Locale.Menu.Sider.Selector,
    icon: <IconScrollList className={styles.icon} />,
    roles: [],
    routeProps: {
      path: '/selector',
      element: < Selector />
    },
  },
  {
    itemKey: 'history',
    text: Locale.Menu.Sider.History,
    icon: <IconHistory className={styles.icon} />,
    roles: [],
    routeProps: {
      path: '/history',
      element: < History />
    },
  },
  {
    itemKey: 'routing-rules',
    text: Locale.Menu.Sider.RoutingRules,
    icon: <IconList className={styles.icon}/>,
    roles: [],
    routeProps: {
      path: '/routing-rules',
      element: < RoutingRules />
    },
   }
]

export const routersMapper: Record<string | number, RouterItem | SubRouterItem> = routers.reduce((mapper, item) => {
  if (item.itemKey && item.routeProps && item.routeProps.path) {
    mapper[item.itemKey] = item;
    mapper[item.routeProps.path] = item;
  }
  return mapper;
}, {} as Record<string | number, RouterItem | SubRouterItem>);

export function hasPagePermission(router: RouterItem | SubRouterItem, access: AccessControlStore): boolean {
  let parentHasPermission = true;
  if (router.items == undefined) {
    // First level menu
    if (router.roles.length != 0) {
      parentHasPermission = router.roles.some(role => access.hasRole(role));
    }
    if (parentHasPermission && router.itemKey != undefined) {
      parentHasPermission = access.hasPermission(router.itemKey.toString());
    }
  } else {
    // Second level menu
    router.items = router.items.filter(item => {
      let chilnHasPermission = true;
      if (item.roles.length != 0) {
        chilnHasPermission = item.roles.some(role => access.hasRole(role));
      }
      if (chilnHasPermission && item.itemKey != undefined) {
        chilnHasPermission = access.hasPermission(item.itemKey.toString());
      }
      return chilnHasPermission;
    });
    parentHasPermission = router.items.length != 0;
  }
  return parentHasPermission;
}
