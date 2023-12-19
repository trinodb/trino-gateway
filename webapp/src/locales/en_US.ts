import { LocaleType } from "./index";

const en_US: LocaleType = {
  Error: {
    Unauthorized: "Currently not logged in.",
    MustAuthorized: "Currently not logged in, please log in and use.",
    Network: "The network has wandered off, please try again later!",
    LocalCache: "Oops, something went wrong!",
    LocalCacheTry: "Please clean the cache and try again!",
    LocalCacheClean: "Clear All Cache",
  },
  Dashboard: {
    QPH: "QPH",
    QPS: "Avg. QPS",
    QPM: "Avg. QPM",
    Backends: "Backends",
    BackendsOffline: "Backends Offline",
    BackendsOnline: "Backends Online",
    StartTime:"Started at",
    Summary: "Summary",
    QueryDistribution: "Query Distribution/hour.",
    QueryCount: "Query Count",
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
    }
  },
  Auth: {
    LoginTitle: "Welcome",
    tips: {
      tip1: "Sign in",
      tip2: " Trino Gateway ",
      tip3: "Account"
    },
    RegisterTitle: "Sign up account",
    Tips: "The first login will generate a new account after verification",
    Username: "Username",
    Email: "Email",
    Password: "Password",
    PasswordTip: "Input password",
    UsernameTip: "Input username",
    Code: "Code",
    Send: "Send",
    Register: "Sign up",
    Login: "Sign in",
    OAuth2: "Sign in with External Authentication",
    Later: "Later",
    SendSuccess: "Send Success",
    LoginSuccess: "Login Success",
    RegisterSuccess: "Register Success",
    Expiration: "Login has expired, please log in again",
    LogoutSuccess: "Logout Success",
  },
  Settings: {
    Title: "Settings",
    SubTitle: "All Settings",
    Lang: {
      Name: "Language",
      All: "All Languages",
    },
    FontSize: {
      Title: "Font Size",
      SubTitle: "Adjust font size",
    },
    Update: {
      Version: (x: string) => `Version: ${x}`,
      IsLatest: "Latest version",
      CheckUpdate: "Check Update",
      IsChecking: "Checking update...",
      FoundUpdate: (x: string) => `Found new version: ${x}`,
      GoToUpdate: "Update",
    },
    Theme: "Theme",
    TightBorder: "Tight Border",
  },
  Store: {
    Error: "Something went wrong, please try again later.",
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

export default en_US;
