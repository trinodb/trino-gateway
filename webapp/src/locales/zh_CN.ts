
const zh_CN = {
  Error: {
    Unauthorized: "当前未登录。",
    MustAuthorized: "当前未登录，请登录后使用。",
    Network: "网络开小差了，请稍后重试！",
    LocalCache: "哎呀，出了问题！",
    LocalCacheTry: "请清理缓存后重试！",
    LocalCacheClean: "清除所有缓存",
  },
  Menu: {
    Header: {
      PersonalCenter: "个人中心",
      Logout: "退出登陆"
    },
    Sider: {
      Home: "首页",
      Dashboard: "控制台",
      Settings: "设置",
      Settings1: "设置1",
      Settings2: "设置2"
    }
  },
  Auth: {
    LoginTitle: "欢迎回来",
    tips: {
      tip1: "登录",
      tip2: " Semi Design ",
      tip3: "账户"
    },
    RegisterTitle: "注册账号",
    Tips: " 首次登录将在验证后生成新账号",
    Email: "输入邮箱",
    Password: "密码",
    Username: "账号",
    PasswordTip: "输入密码",
    UsernameTip: "输入账号",
    Code: "验证码",
    Send: "发送",
    Login: "登录",
    Register: "注册",
    Later: "稍后再说",
    SendSuccess: "发送成功",
    LoginSuccess: "登录成功",
    RegisterSuccess: "注册成功",
    Expiration: "登陆已失效，请重新登陆",
    LogoutSuccess: "退出登录",
  },
  Settings: {
    Title: "设置",
    SubTitle: "所有设置选项",
    Lang: {
      Name: "Language",
      All: "所有语言",
    },
    FontSize: {
      Title: "字体大小",
      SubTitle: "内容的字体大小",
    },
    Update: {
      Version: (x: string) => `当前版本：${x}`,
      IsLatest: "已是最新版本",
      CheckUpdate: "检查更新",
      IsChecking: "正在检查更新...",
      FoundUpdate: (x: string) => `发现新版本：${x}`,
      GoToUpdate: "前往更新",
    },
    Theme: "主题",
    TightBorder: "无边框模式",
  },
  Store: {
    Error: "出错了，稍后重试吧",
  },
  Copy: {
    Success: "已写入剪切板",
    Failed: "复制失败，请赋予剪切板权限",
  },
  UI: {
    Confirm: "确认",
    Cancel: "取消",
    Close: "关闭",
    Create: "新建",
    Edit: "编辑",
  },
};

type DeepPartial<T> = T extends object
  ? {
    [P in keyof T]?: DeepPartial<T[P]>;
  }
  : T;

export type LocaleType = typeof zh_CN;
export type PartialLocaleType = DeepPartial<typeof zh_CN>;

export default zh_CN;
