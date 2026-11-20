import { expect, login, test } from './fixtures'

test.describe('login', () => {
  test('signs in with the form and lands on the dashboard', async ({ page, api }) => {
    await login(page)

    await expect(page).toHaveURL(/#\/dashboard$/)
    await expect(page.getByText('Summary')).toBeVisible()
    expect(api.callsTo('/login')[0].body).toEqual({ username: 'admin', password: 'secret' })
    expect(api.callsTo('/userinfo').length).toBeGreaterThan(0)
  })

  test('sends the token on authenticated requests', async ({ page }) => {
    const distributionRequest = page.waitForRequest('**/webapp/getDistribution')
    await login(page)

    expect((await distributionRequest).headers()['authorization']).toBe('Bearer test-token')
  })

  test('requires a username and password', async ({ page, api }) => {
    await page.goto('./')
    await page.getByRole('button', { name: 'Sign in' }).click()

    await expect(page.getByText('required error').first()).toBeVisible()
    expect(api.callsTo('/login')).toHaveLength(0)
  })

  test.describe('with wrong credentials', () => {
    test.use({
      mockOptions: {
        overrides: {
          '/login': (route) =>
            route.fulfill({
              status: 403,
              contentType: 'application/json',
              body: JSON.stringify({ code: 500, msg: 'Authentication failed.' }),
            }),
        },
      },
    })

    test('shows the login error and stays on the login form', async ({ page, browserErrors }) => {
      await page.goto('./')
      await page.getByPlaceholder('Input username').fill('admin')
      await page.getByPlaceholder('Input password').fill('wrong')
      await page.getByRole('button', { name: 'Sign in' }).click()

      await expect(page.getByText('Authentication failed.')).toBeVisible()
      await expect(page.getByPlaceholder('Input username')).toBeVisible()

      // The browser logs the 403 response of the failed login as a console error
      const expectedError = browserErrors.findIndex((error) => error.includes('status of 403'))
      expect(expectedError).toBeGreaterThanOrEqual(0)
      browserErrors.splice(expectedError, 1)
    })
  })

  test('logs out and returns to the login form', async ({ page, api }) => {
    await login(page)
    await page.locator('header').getByRole('button').last().hover()
    await page.getByText('Logout').click()

    await expect(page.getByPlaceholder('Input username')).toBeVisible()
    expect(api.callsTo('/logout')).toHaveLength(1)
  })

  test.describe('with several authentication methods', () => {
    test.use({ mockOptions: { loginTypes: ['oauth', 'form'] } })

    test('switches between external and form authentication', async ({ page }) => {
      await page.goto('./')

      await expect(
        page.getByRole('button', { name: 'Sign in with external authentication' })
      ).toBeVisible()
      await page.getByText('Username & password').click()
      await expect(page.getByPlaceholder('Input username')).toBeVisible()
    })
  })
})
