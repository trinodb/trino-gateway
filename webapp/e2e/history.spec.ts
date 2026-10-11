import { expect, login, test } from './fixtures'

test.describe('history', () => {
  test.beforeEach(async ({ page }) => {
    await login(page)
    await page.getByRole('link', { name: 'History' }).click()
    await expect(page.getByText('20260115_100000_00001_abcde')).toBeVisible()
  })

  test('lists the queries with the backend name and submission time', async ({ page, api }) => {
    const row = page.getByRole('row').filter({ hasText: '20260115_100000_00001_abcde' })
    await expect(row).toContainText('adhoc')
    await expect(row).toContainText('trino-1')
    await expect(row).toContainText('https://trino-1.example.com')
    await expect(row).toContainText('trino-cli')
    await expect(row).toContainText('2026-01-15 10:00:00')
    await expect(
      row.getByRole('link', { name: '20260115_100000_00001_abcde' })
    ).toHaveAttribute(
      'href',
      'https://trino-1.example.com/ui/query.html?20260115_100000_00001_abcde'
    )

    expect(api.callsTo('/webapp/findQueryHistory')[0].body).toMatchObject({ page: 1, size: 15 })
  })

  test('filters by query id', async ({ page, api }) => {
    const before = api.callsTo('/webapp/findQueryHistory').length
    await page.locator('#queryId').fill('20260115_100100_00002_abcde')
    await page.getByRole('button', { name: 'Query' }).click()

    await expect.poll(() => api.callsTo('/webapp/findQueryHistory').length).toBeGreaterThan(before)
    expect(api.callsTo('/webapp/findQueryHistory').at(-1)?.body).toMatchObject({
      page: 1,
      queryId: '20260115_100100_00002_abcde',
    })
  })

  test('opens the query text', async ({ page }) => {
    await page.getByText('SELECT count(*) FROM tpch.tiny.nation').click()

    const dialog = page.getByRole('dialog')
    await expect(dialog).toContainText('Query Text')
    await expect(dialog.locator('code')).toContainText('SELECT count(*) FROM tpch.tiny.nation')
    await expect(dialog.locator('button', { hasText: 'Copy to Clipboard' })).toBeVisible()
  })
})
