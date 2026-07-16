import { expect, test, type Page } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  page.on('console', (message) => {
    if (message.type() === 'error') console.log(`[browser console] ${message.text()}`)
  })
  page.on('requestfailed', (request) =>
    console.log(`[request failed] ${request.method()} ${request.url()} ${request.failure()?.errorText}`),
  )
  page.on('response', (response) => {
    if (response.status() >= 400) console.log(`[http ${response.status()}] ${response.url()}`)
  })
})

const completeProfileIfNeeded = async (page: Page, organization: string) => {
  if (await page.getByRole('heading', { name: 'Hoàn tất hồ sơ' }).isVisible()) {
    await page.getByLabel('Tên đơn vị').fill(organization)
    await page.getByLabel('Số điện thoại').fill('0901234567')
    await page.getByLabel('Địa chỉ').fill('Việt Nam')
    await page.getByRole('button', { name: 'Lưu hồ sơ' }).click()
  }
}

const login = async (page: Page, username: string, organization: string) => {
  await page.goto('/login')
  await page.getByRole('button', { name: 'Đăng nhập với Keycloak' }).click()
  await page.locator('#username').fill(username)
  await page.locator('#password').fill('pass123')
  await page.locator('#kc-login').click()
  await page.waitForURL(/localhost:5173/)
  await completeProfileIfNeeded(page, organization)
  await expect(page.getByRole('heading', { name: 'Tổng quan' })).toBeVisible()
}

test('public marketplace and buyer Keycloak login work against the real stack', async ({ page }) => {
  await page.goto('/listings')
  await expect(page.getByRole('heading', { name: 'Chợ nông sản' })).toBeVisible()
  await expect(page.getByRole('link', { name: /Đăng nhập/ })).toBeVisible()

  await login(page, 'buyer1', 'Buyer E2E')
  await expect(page.getByRole('link', { name: 'Hợp đồng' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Ký quỹ' })).toBeVisible()
})
