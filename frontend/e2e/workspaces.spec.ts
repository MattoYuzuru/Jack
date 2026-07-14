import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'

const workspaces = [
  { path: '/', name: 'home' },
  { path: '/viewer', name: 'viewer' },
  { path: '/converter', name: 'converter' },
  { path: '/compression', name: 'compression' },
  { path: '/pdf-toolkit', name: 'pdf-toolkit' },
  { path: '/editor', name: 'editor' },
  { path: '/dev-tools', name: 'dev-tools' },
]

test.beforeEach(async ({ page }) => {
  await page.route('**/api/**', (route) =>
    route.fulfill({ status: 503, contentType: 'application/json', body: '{"code":"E2E_OFFLINE"}' }),
  )
  await page.route('**/api/capabilities/editor', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        scope: 'editor',
        editorMatrix: {
          acceptAttribute: '.md,.txt',
          formats: [
            {
              id: 'markdown',
              label: 'Markdown',
              extensions: ['md', 'markdown'],
              mimeTypes: ['text/markdown'],
              syntaxMode: 'markdown',
              previewMode: 'rendered',
              supportsFormatting: true,
              supportsPlainTextExport: true,
              statusLabel: 'Доступно',
              notes: '',
              accents: [],
              available: true,
              availabilityDetail: null,
              requiredJobTypes: [],
            },
          ],
        },
      }),
    }),
  )
})

for (const workspace of workspaces) {
  test(`${workspace.name} has no horizontal overflow`, async ({ page }) => {
    await openWorkspace(page, workspace.path)
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - window.innerWidth,
    )
    expect(overflow).toBeLessThanOrEqual(1)
  })

  test(`${workspace.name} empty state passes axe`, async ({ page }) => {
    await openWorkspace(page, workspace.path)
    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag22aa'])
      .analyze()

    expect(results.violations).toEqual([])
  })
}

test('home supports keyboard-only navigation', async ({ page }) => {
  await openWorkspace(page, '/')
  await page.keyboard.press('Tab')
  await expect(page.getByRole('link', { name: 'Открыть Viewer' })).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/viewer$/u)
})

test('editor accepts a local file without a pointer device', async ({ page }) => {
  await openWorkspace(page, '/editor')
  const fileInput = page.locator('input[type="file"]').first()
  await fileInput.setInputFiles({
    name: 'fixture.md',
    mimeType: 'text/markdown',
    buffer: Buffer.from('# Fixture\n\nKeyboard upload.'),
  })

  await expect(page.locator('input[type="text"]').first()).toHaveValue('fixture.md')
  await expect(page.locator('.cm-line').getByText('Keyboard upload.', { exact: true })).toBeVisible()
})

test('home visual baseline', async ({ page }) => {
  await openWorkspace(page, '/')
  await expect(page).toHaveScreenshot('home-empty.png', { fullPage: true })
})

async function openWorkspace(page: Page, path: string): Promise<void> {
  await page.goto(path)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.locator('main').waitFor()
  await page.evaluate(() => document.fonts.ready)
}
