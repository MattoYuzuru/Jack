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

const markdownTableHtml = `
<h1 id="release-matrix">Матрица релиза</h1>
<p>Проверка общего GFM-контракта Viewer и Editor.</p>
<div class="markdown-table-scroll" role="region" aria-label="Таблица Markdown" tabindex="0">
  <table class="markdown-table">
    <thead><tr><th class="markdown-align-left" scope="col">Функция</th><th class="markdown-align-center" scope="col">Статус</th><th class="markdown-align-right" scope="col">Прогресс</th></tr></thead>
    <tbody>
      <tr><td>Безопасный renderer</td><td class="markdown-align-center">Готово</td><td class="markdown-align-right">100%</td></tr>
      <tr><td>Очень длинное значение проверяет перенос содержимого без расширения всей страницы</td><td class="markdown-align-center">Проверка</td><td class="markdown-align-right">42%</td></tr>
    </tbody>
  </table>
</div>`

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

    const pageMetrics = await page.evaluate(() => ({
      overflow: document.documentElement.scrollWidth - window.innerWidth,
      domNodes: document.getElementsByTagName('*').length,
    }))
    expect(pageMetrics.overflow).toBeLessThanOrEqual(1)
    expect(pageMetrics.domNodes).toBeLessThanOrEqual(2_500)
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
  await expect(
    page.locator('.cm-line').getByText('Keyboard upload.', { exact: true }),
  ).toBeVisible()
})

test('editor renders an accessible GFM table preview', async ({ page }) => {
  await openMarkdownTableInEditor(page)
  const preview = page.frameLocator('.editor-preview-frame')

  await expect(preview.locator('.markdown-table-scroll')).toHaveAttribute('role', 'region')
  await expect(preview.locator('.markdown-table-scroll')).toHaveAttribute('tabindex', '0')
  await expect(preview.locator('thead th[scope="col"]')).toHaveCount(3)
  expect(
    await preview.locator('.markdown-table-scroll').evaluate((element) => {
      return window.getComputedStyle(element).overflowX
    }),
  ).toBe('auto')
})

test('editor GFM table visual baseline', async ({ page }, testInfo) => {
  test.skip(
    !['chromium-1440', 'chromium-390'].includes(testInfo.project.name),
    'Visual table baseline хранится для desktop и mobile.',
  )
  await openMarkdownTableInEditor(page)
  await expect(page.locator('.editor-panel--preview')).toHaveScreenshot('editor-gfm-table.png')
})

test('home visual baseline', async ({ page }) => {
  await openWorkspace(page, '/')
  await expect(page).toHaveScreenshot('home-empty.png', { fullPage: true })
})

async function openWorkspace(page: Page, path: string): Promise<void> {
  await page.goto(path)
  await page.locator('main').waitFor()
  await page.evaluate(() => document.fonts.ready)
}

async function openMarkdownTableInEditor(page: Page): Promise<void> {
  await openWorkspace(page, '/editor')
  await page.route('**/api/markdown/render', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        profileVersion: 'jack-markdown-1.1.0',
        profile: 'obsidian-safe',
        sanitizedHtml: markdownTableHtml,
        previewDocument: buildMarkdownTablePreviewDocument(markdownTableHtml),
        outline: [{ id: 'release-matrix', label: 'Матрица релиза', depth: 1, kind: 'heading' }],
        unresolvedReferences: [],
        warnings: [],
        detectedFeatures: ['gfm-table'],
      }),
    }),
  )
  await page
    .locator('input[type="file"]')
    .first()
    .setInputFiles({
      name: 'release-matrix.md',
      mimeType: 'text/markdown',
      buffer: Buffer.from(
        '| Функция | Статус | Прогресс |\n| :--- | :---: | ---: |\n| Renderer | Готово | 100% |',
      ),
    })
  await expect(page.frameLocator('.editor-preview-frame').locator('.markdown-table')).toBeVisible()
}

function buildMarkdownTablePreviewDocument(body: string): string {
  return `<!doctype html>
<html lang="ru">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'">
    <style>
      *{box-sizing:border-box}html,body{margin:0;background:#fffaf1;color:#1f4140;font-family:Manrope,"Segoe UI",system-ui,sans-serif;line-height:1.65}body{padding:28px;overflow-wrap:anywhere}h1{margin:0 0 .45em;color:#102426;line-height:1.18}.markdown-table-scroll{max-width:100%;margin:1.2em 0;overflow-x:auto;border:1px solid rgba(23,52,54,.16);border-radius:16px;background:#fffdf9;box-shadow:0 12px 28px rgba(86,94,93,.12)}.markdown-table-scroll:focus-visible{outline:3px solid rgba(29,92,85,.42);outline-offset:3px}.markdown-table{width:max-content;min-width:100%;border-collapse:separate;border-spacing:0}.markdown-table th,.markdown-table td{max-width:34rem;padding:11px 14px;border-right:1px solid rgba(23,52,54,.16);border-bottom:1px solid rgba(23,52,54,.16);text-align:left;vertical-align:top;white-space:normal;overflow-wrap:anywhere}.markdown-table th{background:rgba(29,92,85,.09);color:#102426;font-weight:800}.markdown-table .markdown-align-center{text-align:center}.markdown-table .markdown-align-right{text-align:right}@media(max-width:640px){body{padding:16px}.markdown-table th,.markdown-table td{max-width:18rem;padding:9px 11px}}
    </style>
  </head>
  <body>${body}</body>
</html>`
}
