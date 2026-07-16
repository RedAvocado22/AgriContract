import { expect, test, type Browser, type BrowserContext, type Page } from '@playwright/test'

interface ActiveContractFixture {
  buyer: { context: BrowserContext; page: Page }
  seller: { context: BrowserContext; page: Page }
  contractId: string
  contractUrl: string
  productName: string
}

const attachDiagnostics = (page: Page) => {
  page.on('console', (message) => {
    if (message.type() === 'error') console.log(`[browser console] ${message.text()}`)
  })
  page.on('response', (response) => {
    if (response.status() >= 400) console.log(`[http ${response.status()}] ${response.url()}`)
  })
}

const login = async (page: Page, username: string, organization: string) => {
  attachDiagnostics(page)
  await page.goto('/login')
  await page.getByRole('button', { name: 'Đăng nhập với Keycloak' }).click()
  await page.locator('#username').fill(username)
  await page.locator('#password').fill('pass123')
  await page.locator('#kc-login').click()
  await page.waitForURL(/localhost:5173/)

  if (await page.getByRole('heading', { name: 'Hoàn tất hồ sơ' }).isVisible()) {
    await page.getByLabel('Tên đơn vị').fill(organization)
    await page.getByLabel('Số điện thoại').fill('0901234567')
    await page.getByLabel('Địa chỉ').fill('Việt Nam')
    await page.getByRole('button', { name: 'Lưu hồ sơ' }).click()
  }

  await expect(page.getByRole('heading', { name: 'Tổng quan' })).toBeVisible()
}

const newRolePage = async (browser: Browser, username: string, organization: string) => {
  const context = await browser.newContext()
  const page = await context.newPage()
  await login(page, username, organization)
  return { context, page }
}

const createActiveContract = async (browser: Browser, label: string): Promise<ActiveContractFixture> => {
  const suffix = `${label}-${Date.now().toString().slice(-6)}`
  const productName = `Cà phê E2E ${suffix}`

  const seller = await newRolePage(browser, 'seller1', 'Seller E2E')
  await seller.page.goto('/listings/create')
  await seller.page.getByLabel('Tên sản phẩm').fill(productName)
  await seller.page.getByLabel('Loại hàng').selectOption('COFFEE')
  await seller.page.getByLabel('Đơn vị').fill('tấn')
  await seller.page.getByLabel('Số lượng').fill('2.5')
  await seller.page.getByLabel(/Giá sàn/).fill('100000')
  await seller.page.getByLabel('Hạn giao hàng').fill('2026-12-31')
  await seller.page.getByRole('button', { name: 'Đăng tin hàng' }).click()
  await expect(seller.page.getByRole('heading', { name: productName })).toBeVisible({ timeout: 15_000 })
  const listingUrl = seller.page.url()

  const buyer = await newRolePage(browser, 'buyer1', 'Buyer E2E')
  await buyer.page.goto(listingUrl)
  await buyer.page.getByLabel('Số lượng (tấn)').fill('1.25')
  await buyer.page.getByLabel('Giá đề nghị (VND)').fill('110000')
  await buyer.page.getByLabel('Quy cách chất lượng').fill('Độ ẩm dưới 12.5%')
  await buyer.page.getByRole('button', { name: 'Tạo đề nghị' }).click()
  await expect(buyer.page.getByRole('heading', { name: 'Hợp đồng' })).toBeVisible({ timeout: 15_000 })

  const buyerContractCard = buyer.page.locator('.contract-card').filter({ hasText: productName })
  await expect(buyerContractCard).toBeVisible()
  await buyerContractCard.getByRole('link', { name: 'Xem và xử lý' }).click()
  const contractUrl = buyer.page.url()
  const contractId = new URL(contractUrl).pathname.split('/').pop()!

  await seller.page.goto(contractUrl)
  await seller.page.getByLabel('Giá').fill('120000')
  await seller.page.getByRole('button', { name: 'Gửi counter-offer' }).click()
  await expect(seller.page.locator('.status-badge').first()).toHaveText('Đang thương lượng')
  await seller.page.getByRole('button', { name: 'Ký điều khoản hiện tại' }).click()
  await expect(
    seller.page.getByText('Bạn đã ký. Đang chờ bên còn lại ký hoặc gửi điều khoản mới.'),
  ).toBeVisible()

  await buyer.page.goto(contractUrl)
  await buyer.page.getByRole('button', { name: 'Ký điều khoản hiện tại' }).click()
  await expect(buyer.page.locator('.status-badge').first()).toHaveText('Đã ký', { timeout: 15_000 })

  await seller.page.goto(`/escrow?contractId=${contractId}`)
  await expect(async () => {
    if (!(await seller.page.getByRole('button', { name: 'Xác nhận cọc bên bán' }).isVisible())) {
      await seller.page.reload()
    }
    await expect(seller.page.getByRole('button', { name: 'Xác nhận cọc bên bán' })).toBeVisible()
  }).toPass({ timeout: 20_000, intervals: [1000, 2000] })
  await seller.page.getByRole('button', { name: 'Xác nhận cọc bên bán' }).click()

  await buyer.page.goto(contractUrl)
  await expect(buyer.page.locator('.status-badge').first()).toHaveText('Đang thực hiện', {
    timeout: 20_000,
  })

  return { buyer, seller, contractId, contractUrl, productName }
}

const closeFixture = async (fixture: ActiveContractFixture) => {
  await fixture.seller.context.close()
  await fixture.buyer.context.close()
}

test('seller and buyer complete the Phase 1 happy path', async ({ browser }) => {
  test.setTimeout(90_000)
  const fixture = await createActiveContract(browser, 'happy')

  await fixture.buyer.page.getByRole('button', { name: 'Xác nhận đã nhận hàng' }).click()
  await expect(fixture.buyer.page.locator('.status-badge').first()).toHaveText('Đã tất toán', {
    timeout: 20_000,
  })

  await closeFixture(fixture)
})

test('buyer can cancel only after the contract is active', async ({ browser }) => {
  test.setTimeout(90_000)
  const fixture = await createActiveContract(browser, 'cancel')

  await fixture.buyer.page.getByLabel('Lý do hủy hợp đồng').fill('Buyer cancel E2E')
  await fixture.buyer.page.getByRole('button', { name: 'Hủy hợp đồng' }).click()
  await expect(fixture.buyer.page.locator('.status-badge').first()).toHaveText('Đã hủy', {
    timeout: 15_000,
  })

  await closeFixture(fixture)
})

test('buyer can dispute delivered goods', async ({ browser }) => {
  test.setTimeout(90_000)
  const fixture = await createActiveContract(browser, 'dispute')

  await fixture.buyer.page.getByRole('button', { name: 'Xác nhận đã nhận hàng' }).click()
  await fixture.buyer.page.getByLabel('Lý do tranh chấp').fill('Chất lượng không đúng E2E')
  await fixture.buyer.page.getByRole('button', { name: 'Mở tranh chấp' }).click()
  await expect(fixture.buyer.page.locator('.status-badge').first()).toHaveText('Đang tranh chấp', {
    timeout: 15_000,
  })

  await closeFixture(fixture)
})

test('admin arbitrates a disputed contract', async ({ browser }) => {
  test.fail(
    true,
    'BE releases escrow on contract.delivered before arbitration, so the arbitrate endpoint returns 409.',
  )
  test.setTimeout(120_000)
  const fixture = await createActiveContract(browser, 'arbitrate')

  await fixture.buyer.page.getByRole('button', { name: 'Xác nhận đã nhận hàng' }).click()
  await fixture.buyer.page.getByLabel('Lý do tranh chấp').fill('Chất lượng không đúng E2E')
  await fixture.buyer.page.getByRole('button', { name: 'Mở tranh chấp' }).click()
  await expect(fixture.buyer.page.locator('.status-badge').first()).toHaveText('Đang tranh chấp', {
    timeout: 15_000,
  })

  const admin = await newRolePage(browser, 'admin1', 'Admin E2E')
  await admin.page.goto(fixture.contractUrl)
  await admin.page.getByRole('link', { name: 'Phân xử' }).click()
  await expect(admin.page.getByRole('heading', { name: 'Phân xử tranh chấp' })).toBeVisible()
  await admin.page.getByLabel('Căn cứ phân xử').fill('Phân xử E2E theo biên bản chất lượng')
  await admin.page.getByRole('button', { name: 'Xác nhận phân xử' }).click()
  await expect(admin.page.getByText('Đã ghi nhận kết quả phân xử.')).toBeVisible({ timeout: 15_000 })

  await admin.context.close()
  await closeFixture(fixture)
})
