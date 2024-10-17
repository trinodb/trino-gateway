const en_US = {
  Error: {
    Network: "The network has wandered off, please try again later!",
  },
  Dashboard: {
    QPH: "QPH",
    QPHTip: "The number of queries in the past hour.",
    QPS: "Avg. QPS",
    QPSTip: "Average number of queries per second in the past hour.",
    QPM: "Avg. QPM",
    QPMTip: "Average number of queries per minute in the past hour.",
    Backends: "Backends",
    BackendsOffline: "Backends Offline",
    BackendsOnline: "Backends Online",
    StartTime: "Started At",
    Summary: "Summary",
    QueryDistribution: "Query Distribution in last hour.",
    QueryCount: "Query Count",
  },
  History: {
    RoutedToTip: "Default All",
    QueryIdTip: "Default All",
  },
  Menu: {
    Header: {
      PersonalCenter: "Profile",
      Logout: "Logout"
    },
    Sider: {
      Dashboard: "Dashboard",
      Cluster: "Cluster",
      History: "History",
      ResourceGroup: "Resource Group",
      Selector: "Selector",
      RoutingRules: "Routing Rules"
    }
  },
  Auth: {
    LoginTitle: "Welcome",
    tips: {
      tip1: "Sign in",
      tip2: " Trino Gateway ",
      tip3: "Account"
    },
    Username: "Username",
    Password: "Password",
    PasswordTip: "Input password",
    UsernameTip: "Input username",
    Login: "Sign in",
    OAuth2: "Sign in with External Authentication",
    NoneAuthTip: "Password not allowed",
    LoginSuccess: "Login Success",
    Expiration: "Login has expired, please log in again",
    LogoutSuccess: "Logout Success",
  },
  Copy: {
    Success: "Copied to clipboard",
    Failed: "Copy failed, please grant permission to access clipboard",
  },
  UI: {
    Confirm: "Confirm",
    Cancel: "Cancel",
    Close: "Close",
    Create: "Create",
    Edit: "Edit",
    Delete: "Delete",
    DeleteTitle: "Are you sure you want to delete?",
    DeleteContent: "Once deleted, it cannot be recovered!",
    Query: "Query",
  },
};

type DeepPartial<T> = T extends object
  ? {
    [P in keyof T]?: DeepPartial<T[P]>;
  }
  : T;

export type LocaleType = typeof en_US;
export type PartialLocaleType = DeepPartial<typeof en_US>;

export default en_US;
