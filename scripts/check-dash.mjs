import { chromium } from '../tools/pw/node_modules/playwright/index.mjs'

const base = 'http://127.0.0.1:8080'
const browser = await chromium.launch({ executablePath: 'C:/Users/Ziv/AppData/Local/ms-playwright/chromium-1169/chrome-win/chrome.exe' })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })

await page.goto(base + '/login')
await page.screenshot({ path: '.shots/debug-load.png' })
console.log('url after goto:', page.url())
await page.fill('input[placeholder="请输入账号"]', 'admin')
await page.fill('input[type="password"]', 'admin123')
await page.click('button.submit')
await page.waitForURL('**/dashboard', { timeout: 15000 })
await page.waitForTimeout(1500)
await page.screenshot({ path: '.shots/dash-qdrant.png', fullPage: false })
console.log('saved .shots/dash-qdrant.png')
await browser.close()
