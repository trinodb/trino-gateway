import { expect, login, test } from './fixtures'

test.describe('dashboard', () => {
  test('shows the summary and renders both charts', async ({ page }) => {
    await login(page)

    const summary = page.locator('.semi-descriptions')
    await expect(summary).toContainText('Started at2026-01-15 08:30:00')
    await expect(summary).toContainText('Backends2')
    await expect(summary).toContainText('Backends online1')
    await expect(summary).toContainText('Backends unhealthy1')
    await expect(summary).toContainText('QPH120')
    await expect(summary).toContainText('Average QPM2.00')
    await expect(summary).toContainText('Average QPS0.03')

    // ECharts draws each chart into its own canvas.
    await expect(page.locator('canvas')).toHaveCount(2)
    for (const canvas of await page.locator('canvas').all()) {
      const box = await canvas.boundingBox()
      expect(box?.width).toBeGreaterThan(100)
      expect(box?.height).toBeGreaterThan(100)
    }
  })

  test('explains a metric in a tooltip', async ({ page }) => {
    await login(page)
    await page.getByRole('img', { name: 'help_circle' }).first().hover()

    await expect(page.getByText('The number of queries in the past hour')).toBeVisible()
  })

  test('follows the timezone selection', async ({ page }) => {
    await login(page)
    const summary = page.locator('.semi-descriptions')
    await expect(summary).toContainText('2026-01-15 08:30:00')

    await page.locator('header .semi-select').click()
    await page.locator('.semi-select-option-list input, header .semi-select input').first().fill('Asia/Tokyo')
    await page.getByRole('option', { name: 'Asia/Tokyo' }).click()

    await expect(summary).toContainText('2026-01-15 17:30:00')
  })

  test('links the backend count to the cluster page', async ({ page }) => {
    await login(page)
    await page.locator('.semi-descriptions').getByText('2', { exact: true }).first().click()

    await expect(page).toHaveURL(/#\/cluster$/)
  })
})
