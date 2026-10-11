import { expect, login, test } from './fixtures'

test.describe('navigation', () => {
  test('reaches every page from the sidebar', async ({ page }) => {
    await login(page)

    await page.getByRole('link', { name: 'Cluster' }).click()
    await expect(page).toHaveURL(/#\/cluster$/)
    await expect(page.getByRole('gridcell', { name: 'trino-1', exact: true })).toBeVisible()

    await page.getByRole('link', { name: 'History' }).click()
    await expect(page).toHaveURL(/#\/history$/)
    await expect(page.getByText('20260115_100000_00001_abcde')).toBeVisible()

    await page.getByRole('link', { name: 'Routing rules' }).click()
    await expect(page).toHaveURL(/#\/routing-rules$/)
    await expect(page.getByText('Routing rule #1')).toBeVisible()

    await page.getByRole('link', { name: 'Dashboard' }).click()
    await expect(page).toHaveURL(/#\/dashboard$/)
    await expect(page.getByText('Summary')).toBeVisible()
  })

  test('keeps the session and the page across a reload', async ({ page }) => {
    await login(page)
    await page.getByRole('link', { name: 'Cluster' }).click()
    await expect(page.getByRole('gridcell', { name: 'trino-1', exact: true })).toBeVisible()

    await page.reload()

    await expect(page).toHaveURL(/#\/cluster$/)
    await expect(page.getByRole('gridcell', { name: 'trino-1', exact: true })).toBeVisible()
  })

  test('cycles the theme', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' })
    await login(page)
    const body = page.locator('body')
    const themeButton = page.getByRole('button', { name: 'Switch Theme' })

    await expect(body).not.toHaveAttribute('theme-mode', 'dark')
    await themeButton.click()
    await expect(body).toHaveClass(/light/)
    await themeButton.click()
    await expect(body).toHaveClass(/dark/)
    await expect(body).toHaveAttribute('theme-mode', 'dark')
  })

  test('shows the profile of the signed-in user', async ({ page }) => {
    await login(page)
    await page.locator('header').getByRole('button').last().hover()
    await page.getByText('Profile').click()

    const dialog = page.getByRole('dialog')
    await expect(dialog.getByText('admin-id', { exact: true })).toBeVisible()
    await expect(dialog.getByText('ADMIN', { exact: true })).toBeVisible()
  })

  test.describe('with a disabled page', () => {
    test.use({ mockOptions: { disablePages: ['routing-rules'] } })

    test('hides the page from the sidebar', async ({ page }) => {
      await login(page)

      await expect(page.getByRole('link', { name: 'History' })).toBeVisible()
      await expect(page.getByRole('link', { name: 'Routing rules' })).toHaveCount(0)
    })
  })
})
