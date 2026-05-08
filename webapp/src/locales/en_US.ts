const en_US = {
  Error: {
    Network: "The network has wandered off, try again later",
  },
  Dashboard: {
    QPH: "QPH",
    QPHTip: "The number of queries in the past hour",
    QPS: "Average QPS",
    QPSTip: "Average number of queries per second in the past hour",
    QPM: "Average QPM",
    QPMTip: "Average number of queries per minute in the past hour",
    Backends: "Backends",
    BackendsOffline: "Backends offline",
    BackendsOnline: "Backends online",
    BackendsHealthy: "Backends healthy",
    BackendsUnhealthy: "Backends unhealthy",
    StartTime: "Started at",
    Summary: "Summary",
    QueryDistribution: "Query distribution in last hour",
    QueryCount: "Query count",
    TimeZone: "Time Zone",
  },
  History: {
    RoutedToTip: "Default all",
    QueryIdTip: "Default all",
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
      RoutingRules: "Routing rules"
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
    OAuth2: "Sign in with external authentication",
    NoneAuthTip: "Password not allowed",
    LoginSuccess: "Login success",
    Expiration: "Login has expired, please log in again",
    LogoutSuccess: "Logout success",
  },
  Copy: {
    Success: "Copied to clipboard",
    Failed: "Copy failed, grant permission to access the clipboard",
  },
  UI: {
    Confirm: "Confirm",
    Cancel: "Cancel",
    Close: "Close",
    Create: "Create",
    Edit: "Edit",
    Delete: "Delete",
    DeleteTitle: "Are you sure you want to delete?",
    DeleteContent: "Once deleted, it cannot be recovered",
    Query: "Query",
  },
  Cluster: {
      Create: "Cluster created successfully",
      Update: "Cluster updated successfully",
      Delete: "Cluster deleted successfully",
      ErrorCreate: "Failed to create cluster",
      ErrorUpdate: "Failed to update cluster",
      ErrorDelete: "Failed to delete cluster",
  },
  RoutingRules: {
      Update: "Routing rule updated successfully",
      ErrorUpdate: "Failed to update routing rule",
      ErrorFetch: "Failed to fetch routing rules"
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
