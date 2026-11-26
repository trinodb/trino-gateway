import { Route } from '@playwright/test'
import { expect, login, test } from './fixtures'

const failWith = (status: number) => (route: Route) =>
  route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify({ code: status, msg: status === 403 ? 'Forbidden' : 'Unauthorized', data: null }),
  })

// The browser logs the failed response as a console error
function removeExpectedError(browserErrors: string[], status: number) {
  const expectedError = browserErrors.findIndex((error) => error.includes(`status of ${status}`))
  expect(expectedError).toBeGreaterThanOrEqual(0)
  browserErrors.splice(expectedError, 1)
}

test.describe('permissions', () => {
  test.describe('when the user lacks the role for a request', () => {
    test.use({ mockOptions: { overrides: { '/webapp/getAllBackends': failWith(403) } } })

    test('shows a permission error and keeps the session', async ({ page, browserErrors }) => {
      await login(page)
      await page.getByRole('link', { name: 'Cluster' }).click()

      await expect(page.getByText('You do not have permission for this action')).toBeVisible()
      await expect(page.getByText('Login has expired, please log in again')).toHaveCount(0)
      await expect(page.getByRole('link', { name: 'Dashboard' })).toBeVisible()
      await expect(page.getByPlaceholder('Input username')).toHaveCount(0)

      removeExpectedError(browserErrors, 403)
    })
  })

  test.describe('when authentication fails with 403', () => {
    test.use({ mockOptions: { overrides: { '/webapp/getAllBackends': (route: Route) => route.fulfill({ status: 403, body: '' }) } } })

    test('logs out and returns to the login form', async ({ page, browserErrors }) => {
      await login(page)
      await page.getByRole('link', { name: 'Cluster' }).click()

      await expect(page.getByText('Login has expired, please log in again')).toBeVisible()
      await expect(page.getByPlaceholder('Input username')).toBeVisible()

      removeExpectedError(browserErrors, 403)
    })
  })

  test.describe('when the session is no longer valid', () => {
    test.use({ mockOptions: { overrides: { '/webapp/getAllBackends': failWith(401) } } })

    test('logs out and returns to the login form', async ({ page, browserErrors }) => {
      await login(page)
      await page.getByRole('link', { name: 'Cluster' }).click()

      await expect(page.getByText('Login has expired, please log in again')).toBeVisible()
      await expect(page.getByPlaceholder('Input username')).toBeVisible()

      removeExpectedError(browserErrors, 401)
    })
  })
})
