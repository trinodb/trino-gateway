import { backends, expect, login, readOnlyUser, test } from './fixtures'

test.describe('cluster', () => {
  test.beforeEach(async ({ page }) => {
    await login(page)
    await page.getByRole('link', { name: 'Cluster' }).click()
    await expect(page.getByRole('gridcell', { name: 'trino-1', exact: true })).toBeVisible()
  })

  test('lists the backends', async ({ page }) => {
    const first = page.getByRole('row').filter({ hasText: 'trino-1' })
    await expect(first).toContainText('adhoc')
    await expect(first).toContainText('http://trino-1.internal:8080')
    await expect(first).toContainText('prod')
    await expect(first).toContainText('HEALTHY')
    await expect(first.getByRole('switch')).toBeChecked()

    const second = page.getByRole('row').filter({ hasText: 'trino-2' })
    await expect(second).toContainText('UNHEALTHY')
    await expect(second.getByRole('switch')).not.toBeChecked()
  })

  test('deactivates a backend with the switch', async ({ page, api }) => {
    await page.getByRole('row').filter({ hasText: 'trino-1' }).getByRole('switch').click()

    await expect.poll(() => api.callsTo('/webapp/updateBackend').length).toBe(1)
    expect(api.callsTo('/webapp/updateBackend')[0].body).toMatchObject({
      name: 'trino-1',
      active: false,
    })
  })

  test('creates a backend', async ({ page, api }) => {
    await page.getByRole('button', { name: 'Create' }).click()
    const dialog = page.getByRole('dialog')
    await dialog.locator('#name').fill('trino-3')
    await dialog.locator('#routingGroup').fill('adhoc')
    await dialog.locator('#proxyTo').fill('http://trino-3.internal:8080')
    await dialog.locator('#externalUrl').fill('https://trino-3.example.com')
    await dialog.locator('button', { hasText: 'Confirm' }).click()

    await expect(page.getByText('Cluster created successfully')).toBeVisible()
    expect(api.callsTo('/webapp/saveBackend')[0].body).toMatchObject({
      name: 'trino-3',
      routingGroup: 'adhoc',
      proxyTo: 'http://trino-3.internal:8080',
      externalUrl: 'https://trino-3.example.com',
      active: false,
    })
  })

  test('edits a backend', async ({ page, api }) => {
    await page.getByRole('row').filter({ hasText: 'trino-1' }).getByRole('button', { name: 'Edit' }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.locator('#name')).toBeDisabled()
    await expect(dialog.locator('#name')).toHaveValue('trino-1')
    await dialog.locator('#routingGroup').fill('etl')
    await dialog.locator('button', { hasText: 'Confirm' }).click()

    await expect(page.getByText('Cluster updated successfully')).toBeVisible()
    expect(api.callsTo('/webapp/updateBackend')[0].body).toMatchObject({
      name: 'trino-1',
      routingGroup: 'etl',
      proxyTo: backends[0].proxyTo,
    })
  })

  test('deletes a backend after confirmation', async ({ page, api }) => {
    await page.getByRole('row').filter({ hasText: 'trino-2' }).getByRole('button', { name: 'Delete' }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toContainText('Are you sure you want to delete the cluster "trino-2"?')
    await dialog.locator('button', { hasText: 'Delete' }).click()

    await expect(page.getByText('Cluster deleted successfully')).toBeVisible()
    expect(api.callsTo('/webapp/deleteBackend')[0].body).toEqual({ name: 'trino-2' })
  })
})

test.describe('cluster as a read-only user', () => {
  test.use({ mockOptions: { user: readOnlyUser } })

  test('offers no changes', async ({ page }) => {
    await login(page, 'reader')
    await page.getByRole('link', { name: 'Cluster' }).click()
    const row = page.getByRole('row').filter({ hasText: 'trino-1' })

    await expect(row).toBeVisible()
    await expect(row.getByRole('switch')).toBeDisabled()
    await expect(page.getByRole('button', { name: 'Create' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Edit' })).toHaveCount(0)
  })
})
