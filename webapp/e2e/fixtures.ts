import { test as base, expect, Page, Route } from '@playwright/test'

export const backends = [
  {
    name: 'trino-1',
    proxyTo: 'http://trino-1.internal:8080',
    active: true,
    routingGroup: 'adhoc',
    externalUrl: 'https://trino-1.example.com',
    queued: 3,
    running: 7,
    status: 'HEALTHY',
    tags: ['prod'],
  },
  {
    name: 'trino-2',
    proxyTo: 'http://trino-2.internal:8080',
    active: false,
    routingGroup: 'etl',
    externalUrl: 'https://trino-2.example.com',
    queued: 0,
    running: 1,
    status: 'UNHEALTHY',
    tags: [],
  },
]

const HOUR_START = Date.UTC(2026, 0, 15, 10, 0, 0)

export const distribution = {
  totalBackendCount: 2,
  offlineBackendCount: 1,
  onlineBackendCount: 1,
  healthyBackendCount: 1,
  unhealthyBackendCount: 1,
  totalQueryCount: 120,
  averageQueryCountMinute: 2,
  averageQueryCountSecond: 0.0333,
  startTime: '2026-01-15T08:30:00Z',
  distributionChart: [
    { backendUrl: backends[0].proxyTo, queryCount: 90, name: 'trino-1' },
    { backendUrl: backends[1].proxyTo, queryCount: 30, name: 'trino-2' },
  ],
  lineChart: {
    'trino-1': [
      { epochMillis: HOUR_START, backendUrl: backends[0].proxyTo, queryCount: 4, name: 'trino-1' },
      { epochMillis: HOUR_START + 60_000, backendUrl: backends[0].proxyTo, queryCount: 6, name: 'trino-1' },
    ],
    'trino-2': [
      { epochMillis: HOUR_START, backendUrl: backends[1].proxyTo, queryCount: 1, name: 'trino-2' },
    ],
  },
}

export const history = {
  total: 2,
  rows: [
    {
      queryId: '20260115_100000_00001_abcde',
      queryText: 'SELECT count(*) FROM tpch.tiny.nation',
      user: 'admin',
      source: 'trino-cli',
      backendUrl: backends[0].proxyTo,
      captureTime: HOUR_START,
      routingGroup: 'adhoc',
      externalUrl: backends[0].externalUrl,
    },
    {
      queryId: '20260115_100100_00002_abcde',
      queryText: 'SELECT 1',
      user: 'admin',
      source: 'dbt',
      backendUrl: backends[1].proxyTo,
      captureTime: HOUR_START + 60_000,
      routingGroup: 'etl',
      externalUrl: backends[1].externalUrl,
    },
  ],
}

export const routingRules = [
  {
    name: 'airflow',
    description: 'Route airflow queries to etl',
    priority: 1,
    condition: 'request.getHeader("X-Trino-Source") == "airflow"',
    actions: ['result.put("routingGroup", "etl")'],
  },
  {
    name: 'adhoc',
    description: 'Everything else',
    priority: 0,
    condition: 'true',
    actions: ['result.put("routingGroup", "adhoc")'],
  },
]

export const adminUser = {
  userId: 'admin-id',
  userName: 'admin',
  roles: ['ADMIN'],
  permissions: [],
}

export const readOnlyUser = {
  userId: 'user-id',
  userName: 'reader',
  roles: ['USER'],
  permissions: [],
}

export interface ApiCall {
  path: string
  method: string
  body: any
}

export interface MockOptions {
  user?: typeof adminUser
  loginTypes?: string[]
  disablePages?: string[]
  // Per path override, keyed by the API path such as '/webapp/getRoutingRules'.
  overrides?: Record<string, (route: Route) => Promise<void> | void>
}

const ok = (data: unknown) => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify({ code: 200, msg: 'ok', data }),
})

export class MockApi {
  readonly calls: ApiCall[] = []

  constructor(private readonly options: MockOptions = {}) {}

  callsTo(path: string): ApiCall[] {
    return this.calls.filter((call) => call.path === path)
  }

  async install(page: Page) {
    const user = this.options.user ?? adminUser
    const responses: Record<string, unknown> = {
      '/loginType': this.options.loginTypes ?? ['form'],
      '/login': { token: 'test-token' },
      '/logout': {},
      '/userinfo': user,
      '/webapp/getUIConfiguration': { disablePages: this.options.disablePages ?? [] },
      '/webapp/getDistribution': distribution,
      '/webapp/getAllBackends': backends,
      '/webapp/saveBackend': {},
      '/webapp/updateBackend': {},
      '/webapp/deleteBackend': true,
      '/webapp/findQueryHistory': history,
      '/webapp/getRoutingRules': routingRules,
      '/webapp/updateRoutingRules': {},
    }

    const handler = async (route: Route) => {
      const request = route.request()
      const path = new URL(request.url()).pathname
      let body: any = undefined
      try {
        body = request.postDataJSON()
      } catch {
        body = request.postData()
      }
      this.calls.push({ path, method: request.method(), body })

      const override = this.options.overrides?.[path]
      if (override) {
        return override(route)
      }
      if (!(path in responses)) {
        return route.fulfill({ status: 404, body: `Unmocked API path ${path}` })
      }
      // Clone so that a test mutating the UI state never changes the fixtures.
      return route.fulfill(ok(structuredClone(responses[path])))
    }

    await page.route('**/webapp/**', handler)
    for (const path of ['/loginType', '/login', '/logout', '/userinfo', '/sso']) {
      await page.route(`**${path}`, handler)
    }
  }
}

interface Fixtures {
  mockOptions: MockOptions
  api: MockApi
  browserErrors: string[]
}

export const test = base.extend<Fixtures>({
  mockOptions: [{}, { option: true }],

  // Automatic, so that no test ever reaches a real backend.
  api: [
    async ({ page, mockOptions }, use) => {
      const api = new MockApi(mockOptions)
      await api.install(page)
      await use(api)
    },
    { auto: true },
  ],

  // Every test fails on an uncaught exception or a console error, which is how
  // a broken dependency upgrade usually shows up first.
  browserErrors: [
    async ({ page }, use) => {
      const errors: string[] = []
      page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`))
      page.on('console', (message) => {
        if (message.type() === 'error') {
          errors.push(`console.error: ${message.text()}`)
        }
      })
      await use(errors)
      expect(errors, 'unexpected browser errors').toEqual([])
    },
    { auto: true },
  ],
})

export { expect }

export async function login(page: Page, username = 'admin') {
  await page.goto('./')
  await page.getByPlaceholder('Input username').fill(username)
  await page.getByPlaceholder('Input password').fill('secret')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('link', { name: 'Dashboard' })).toBeVisible()
}
