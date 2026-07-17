import { readdir, readFile, stat } from 'node:fs/promises'
import { resolve } from 'node:path'

const DIST_ROOT = resolve(import.meta.dirname, '..', 'dist')
const ASSETS_ROOT = resolve(DIST_ROOT, 'assets')
const KIB = 1024
const budgets = {
  initialJavaScript: 150 * KIB,
  initialCss: 40 * KIB,
  lazyJavaScript: 350 * KIB,
  lazyCss: 80 * KIB,
}

const indexHtml = await readFile(resolve(DIST_ROOT, 'index.html'), 'utf8')
const initialAssets = new Set(
  [...indexHtml.matchAll(/(?:src|href)="(\/assets\/[^"?#]+)"/gu)].map((match) =>
    match[1].replace('/assets/', ''),
  ),
)
const assetNames = await readdir(ASSETS_ROOT)
const failures = []

for (const assetName of assetNames) {
  const extension = assetName.split('.').at(-1)
  if (extension !== 'js' && extension !== 'css') continue

  const size = (await stat(resolve(ASSETS_ROOT, assetName))).size
  const isInitial = initialAssets.has(assetName)
  const budget =
    extension === 'js'
      ? isInitial
        ? budgets.initialJavaScript
        : budgets.lazyJavaScript
      : isInitial
        ? budgets.initialCss
        : budgets.lazyCss

  if (size > budget) {
    failures.push(`${assetName}: ${formatKib(size)} > ${formatKib(budget)}`)
  }
}

if (failures.length > 0) {
  throw new Error(`Frontend performance budget exceeded:\n${failures.join('\n')}`)
}

console.log(
  `Performance budgets passed: ${initialAssets.size} initial assets, ${assetNames.length} total assets.`,
)

function formatKib(bytes) {
  return `${(bytes / KIB).toFixed(1)} KiB`
}
