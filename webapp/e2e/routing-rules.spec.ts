import { expect, login, readOnlyUser, routingRules, test } from './fixtures'

test.describe('routing rules', () => {
  test('lists the rules read-only until edited', async ({ page }) => {
    await login(page)
    await page.getByRole('link', { name: 'Routing rules' }).click()

    const first = page.locator('.semi-card').filter({ hasText: 'Routing rule #1' })
    await expect(first.locator('#name')).toHaveValue('airflow')
    await expect(first.locator('#description')).toHaveValue('Route airflow queries to etl')
    await expect(first.locator('#condition')).toHaveValue(routingRules[0].condition)
    await expect(first.locator('#description')).toBeDisabled()
    await expect(page.getByText('Routing rule #2')).toBeVisible()
  })

  test('edits and saves a rule', async ({ page, api }) => {
    await login(page)
    await page.getByRole('link', { name: 'Routing rules' }).click()

    const first = page.locator('.semi-card').filter({ hasText: 'Routing rule #1' })
    await first.getByRole('button', { name: 'Edit' }).click()
    await expect(first.locator('#name')).toBeDisabled()
    await first.locator('#description').fill('Airflow goes to etl')
    await first.getByRole('button', { name: 'Save' }).click()

    await expect(page.getByText('Routing rule updated successfully')).toBeVisible()
    expect(api.callsTo('/webapp/updateRoutingRules')[0].body).toMatchObject({
      name: 'airflow',
      description: 'Airflow goes to etl',
      condition: routingRules[0].condition,
      actions: routingRules[0].actions,
    })
    await expect(first.locator('#description')).toBeDisabled()
    await expect(first.locator('#description')).toHaveValue('Airflow goes to etl')
  })

  test.describe('as a read-only user', () => {
    test.use({ mockOptions: { user: readOnlyUser } })

    test('offers no edit', async ({ page }) => {
      await login(page, 'reader')
      await page.getByRole('link', { name: 'Routing rules' }).click()

      await expect(page.getByText('Routing rule #1')).toBeVisible()
      await expect(page.getByRole('button', { name: 'Edit' })).toHaveCount(0)
    })
  })

  test.describe('with an external routing service', () => {
    test.use({
      mockOptions: {
        overrides: {
          '/webapp/getRoutingRules': (route) => route.fulfill({ status: 204 }),
        },
      },
    })

    test('explains that rules are managed externally', async ({ page }) => {
      await login(page)
      await page.getByRole('link', { name: 'Routing rules' }).click()

      await expect(
        page.getByText('Routing rules are managed by an external service.')
      ).toBeVisible()
    })
  })
})
