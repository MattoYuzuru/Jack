import { EditorSelection, type EditorState, type SelectionRange } from '@codemirror/state'
import type { EditorView } from '@codemirror/view'
import { parse as parseYaml, stringify as stringifyYaml } from 'yaml'

interface WrapCommand {
  kind: 'wrap'
  prefix: string
  suffix: string
  fallback: string
}

interface InsertCommand {
  kind: 'insert'
  template: string
}

interface LineCommand {
  kind: 'line'
  prefix: string
}

type EditorCommandDefinition = WrapCommand | InsertCommand | LineCommand

const COMMANDS: Record<string, EditorCommandDefinition> = {
  'md-h1': { kind: 'line', prefix: '# ' },
  'md-h2': { kind: 'line', prefix: '## ' },
  'md-h3': { kind: 'line', prefix: '### ' },
  'md-h4': { kind: 'line', prefix: '#### ' },
  'md-h5': { kind: 'line', prefix: '##### ' },
  'md-h6': { kind: 'line', prefix: '###### ' },
  'md-heading': { kind: 'line', prefix: '## ' },
  'md-bold': { kind: 'wrap', prefix: '**', suffix: '**', fallback: 'bold' },
  'md-italic': { kind: 'wrap', prefix: '*', suffix: '*', fallback: 'italic' },
  'md-strike': { kind: 'wrap', prefix: '~~', suffix: '~~', fallback: 'strike' },
  'md-inline-code': { kind: 'wrap', prefix: '`', suffix: '`', fallback: 'code' },
  'md-link': { kind: 'wrap', prefix: '[', suffix: '](https://example.com)', fallback: 'label' },
  'md-image': { kind: 'insert', template: '![alt](https://example.com/image.png)' },
  'md-code': { kind: 'wrap', prefix: '```ts\n', suffix: '\n```', fallback: 'code' },
  'md-quote': { kind: 'line', prefix: '> ' },
  'md-bullet': { kind: 'line', prefix: '- ' },
  'md-ordered': { kind: 'line', prefix: '1. ' },
  'md-task': { kind: 'line', prefix: '- [ ] ' },
  'md-table': {
    kind: 'insert',
    template: '| Column 1 | Column 2 |\n| --- | --- |\n| Value | Value |',
  },
  'md-hr': { kind: 'insert', template: '---' },
  'md-footnote': { kind: 'insert', template: 'Text[^1]\n\n[^1]: Footnote' },
  'html-section': {
    kind: 'insert',
    template: '<section>\n  <h2>Section title</h2>\n  <p>Section copy</p>\n</section>',
  },
  'html-card': {
    kind: 'insert',
    template:
      '<article class="card">\n  <h3>Card title</h3>\n  <p>Supportive text.</p>\n</article>',
  },
  'html-link': {
    kind: 'insert',
    template:
      '<a href="https://example.com" target="_blank" rel="noopener noreferrer">Open link</a>',
  },
  'html-list': {
    kind: 'insert',
    template: '<ul>\n  <li>First item</li>\n  <li>Second item</li>\n</ul>',
  },
  'html-table': {
    kind: 'insert',
    template:
      '<table>\n  <caption>Data</caption>\n  <thead><tr><th scope="col">Name</th></tr></thead>\n  <tbody><tr><td>Value</td></tr></tbody>\n</table>',
  },
  'html-form': {
    kind: 'insert',
    template:
      '<form>\n  <label for="email">Email</label>\n  <input id="email" name="email" type="email" autocomplete="email" />\n  <button type="submit">Submit</button>\n</form>',
  },
  'html-landmarks': {
    kind: 'insert',
    template:
      '<header>Header</header>\n<nav aria-label="Main">Navigation</nav>\n<main>Content</main>\n<footer>Footer</footer>',
  },
  'css-variables': {
    kind: 'insert',
    template: ':root {\n  --accent: #1d5c55;\n  --panel: #fffaf2;\n}',
  },
  'css-flex': {
    kind: 'insert',
    template: 'display: flex;\nalign-items: center;\njustify-content: center;',
  },
  'css-grid': {
    kind: 'insert',
    template:
      'display: grid;\ngrid-template-columns: repeat(auto-fit, minmax(180px, 1fr));\ngap: 16px;',
  },
  'css-media': {
    kind: 'insert',
    template:
      '@media (max-width: 720px) {\n  .preview-grid {\n    grid-template-columns: 1fr;\n  }\n}',
  },
  'css-rule': { kind: 'insert', template: '.component {\n  color: var(--text-strong);\n}' },
  'css-container': {
    kind: 'insert',
    template:
      '.wrapper { container-type: inline-size; }\n@container (min-width: 36rem) {\n  .component { display: grid; }\n}',
  },
  'css-keyframes': {
    kind: 'insert',
    template: '@keyframes reveal {\n  from { opacity: 0; }\n  to { opacity: 1; }\n}',
  },
  'js-async': {
    kind: 'insert',
    template:
      "async function runTask() {\n  try {\n    return await Promise.resolve('done')\n  } catch (error) {\n    console.error(error)\n    throw error\n  }\n}",
  },
  'js-fetch': {
    kind: 'insert',
    template:
      "const response = await fetch('/api/example')\nif (!response.ok) {\n  throw new Error('Request failed')\n}\nconst payload = await response.json()",
  },
  'js-try': {
    kind: 'insert',
    template: 'try {\n  // work\n} catch (error) {\n  console.error(error)\n}',
  },
  'js-listener': {
    kind: 'insert',
    template: "window.addEventListener('click', (event) => {\n  console.log(event.type)\n})",
  },
  'js-module': {
    kind: 'insert',
    template: "import { value } from './module.js'\n\nexport { value }",
  },
  'js-function': {
    kind: 'insert',
    template: 'export function transform(value) {\n  return value\n}',
  },
  'json-object': { kind: 'insert', template: '{\n  "key": "value"\n}' },
  'json-array': { kind: 'insert', template: '[\n  "item-1",\n  "item-2"\n]' },
  'json-field': { kind: 'insert', template: '"key": "value"' },
  'yaml-map': { kind: 'insert', template: 'root:\n  key: value\n  nested:\n    enabled: true' },
  'yaml-list': { kind: 'insert', template: 'items:\n  - first\n  - second' },
  'yaml-service': {
    kind: 'insert',
    template: 'service:\n  enabled: true\n  port: 8080\n  host: localhost',
  },
  'txt-section': { kind: 'insert', template: 'SECTION:\n' },
  'txt-checklist': { kind: 'insert', template: '- [ ] First task\n- [ ] Second task' },
  'txt-divider': { kind: 'insert', template: '------------------------------' },
}

export function applyEditorCommand(view: EditorView, commandId: string): boolean {
  if (commandId.startsWith('json-')) {
    return applyJsonCommand(view, commandId)
  }
  if (commandId.startsWith('yaml-')) {
    return applyYamlCommand(view, commandId)
  }

  const command = COMMANDS[commandId]
  if (!command) {
    return false
  }

  switch (command.kind) {
    case 'wrap':
      return applyWrapCommand(view, command)
    case 'line':
      return applyLineCommand(view, command.prefix)
    case 'insert':
      return applyInsertCommand(view, command.template)
  }
}

function applyJsonCommand(view: EditorView, commandId: string): boolean {
  const source = view.state.doc.toString()
  let value: unknown

  try {
    value = source.trim() ? (JSON.parse(source) as unknown) : {}
  } catch {
    return false
  }

  if (Array.isArray(value)) {
    value.push(commandId === 'json-array' ? ['item'] : { key: 'value' })
  } else if (isRecord(value)) {
    const key = resolveAvailableKey(
      value,
      commandId === 'json-array' ? 'items' : commandId === 'json-field' ? 'key' : 'object',
    )
    value[key] =
      commandId === 'json-array'
        ? ['item']
        : commandId === 'json-field'
          ? 'value'
          : { key: 'value' }
  } else {
    return false
  }

  return replaceWholeDocument(view, `${JSON.stringify(value, null, 2)}\n`)
}

function applyYamlCommand(view: EditorView, commandId: string): boolean {
  const source = view.state.doc.toString()
  let value: unknown

  try {
    value = source.trim() ? (parseYaml(source) as unknown) : {}
  } catch {
    return false
  }

  const snippet =
    commandId === 'yaml-list'
      ? { key: 'items', value: ['first', 'second'] }
      : commandId === 'yaml-service'
        ? { key: 'service', value: { enabled: true, port: 8080, host: 'localhost' } }
        : { key: 'root', value: { key: 'value', nested: { enabled: true } } }

  if (Array.isArray(value)) {
    value.push(snippet.value)
  } else if (isRecord(value)) {
    value[resolveAvailableKey(value, snippet.key)] = snippet.value
  } else {
    return false
  }

  return replaceWholeDocument(view, stringifyYaml(value))
}

function replaceWholeDocument(view: EditorView, content: string): boolean {
  // Для structured snippets перестраиваем валидный root целиком, чтобы вставка не ломала JSON/YAML.
  view.dispatch({
    changes: { from: 0, to: view.state.doc.length, insert: content },
    selection: { anchor: content.length },
  })
  view.focus()
  return true
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function resolveAvailableKey(value: Record<string, unknown>, preferredKey: string): string {
  if (!(preferredKey in value)) {
    return preferredKey
  }

  let suffix = 2
  while (`${preferredKey}${suffix}` in value) {
    suffix += 1
  }
  return `${preferredKey}${suffix}`
}

export function continueMarkdownList(view: EditorView): boolean {
  const { state } = view
  const matches = state.selection.ranges.map((range) => {
    if (!range.empty) {
      return null
    }

    const line = state.doc.lineAt(range.head)
    const beforeCursor = line.text.slice(0, range.head - line.from)
    const match = beforeCursor.match(/^(\s*)([-+*]|\d+\.)(\s+)(?:\[([ xX])\](\s+))?(.*)$/u)
    return match ? { range, line, match } : null
  })

  if (matches.some((match) => !match)) {
    return false
  }

  // changeByRange сохраняет каждую каретку и корректно сдвигает соседние изменения.
  const transaction = state.changeByRange((range) => {
    const line = state.doc.lineAt(range.head)
    const beforeCursor = line.text.slice(0, range.head - line.from)
    const match = beforeCursor.match(/^(\s*)([-+*]|\d+\.)(\s+)(?:\[([ xX])\](\s+))?(.*)$/u)!
    const [, indent = '', marker = '-', spacing = ' ', taskState, taskSpacing = ' ', content = ''] =
      match

    if (!content.trim()) {
      return {
        changes: { from: line.from, to: range.head, insert: '' },
        range: EditorSelection.cursor(line.from),
      }
    }

    const nextMarker = /^\d+\.$/u.test(marker) ? `${Number.parseInt(marker, 10) + 1}.` : marker
    const task = taskState === undefined ? '' : `[ ]${taskSpacing}`
    const insertion = `\n${indent}${nextMarker}${spacing}${task}`
    return {
      changes: { from: range.head, insert: insertion },
      range: EditorSelection.cursor(range.head + insertion.length),
    }
  })

  view.dispatch(transaction)
  return true
}

function applyWrapCommand(view: EditorView, command: WrapCommand): boolean {
  const transaction = view.state.changeByRange((range) => wrapRange(view.state, range, command))
  view.dispatch(transaction)
  view.focus()
  return true
}

function wrapRange(
  state: EditorState,
  range: SelectionRange,
  command: WrapCommand,
): { changes: { from: number; to: number; insert: string }; range: SelectionRange } {
  const selected = state.sliceDoc(range.from, range.to)
  const hasOuterMarkers =
    range.from >= command.prefix.length &&
    state.sliceDoc(range.from - command.prefix.length, range.from) === command.prefix &&
    state.sliceDoc(range.to, range.to + command.suffix.length) === command.suffix

  if (hasOuterMarkers) {
    return {
      changes: {
        from: range.from - command.prefix.length,
        to: range.to + command.suffix.length,
        insert: selected,
      },
      range: EditorSelection.range(
        range.from - command.prefix.length,
        range.to - command.prefix.length,
      ),
    }
  }

  const content = selected || command.fallback
  const insertion = `${command.prefix}${content}${command.suffix}`
  const selectionStart = range.from + command.prefix.length
  return {
    changes: { from: range.from, to: range.to, insert: insertion },
    range: EditorSelection.range(selectionStart, selectionStart + content.length),
  }
}

function applyLineCommand(view: EditorView, prefix: string): boolean {
  const lineNumbers = new Set<number>()
  for (const range of view.state.selection.ranges) {
    const startLine = view.state.doc.lineAt(range.from)
    const endPosition = range.to > range.from ? range.to - 1 : range.to
    const endLine = view.state.doc.lineAt(endPosition)
    for (let lineNumber = startLine.number; lineNumber <= endLine.number; lineNumber += 1) {
      lineNumbers.add(lineNumber)
    }
  }

  const lines = [...lineNumbers].map((lineNumber) => view.state.doc.line(lineNumber))
  const parsed = lines.map((line) => ({
    line,
    match: line.text.match(/^(\s*)((?:#{1,6}|>|- \[[ xX]\]|[-+*]|\d+\.))\s+/u),
  }))
  const shouldRemove = parsed.every(({ match }) => match?.[2] && `${match[2]} ` === prefix)
  const changes = parsed.map(({ line, match }) => {
    const indent = match?.[1] ?? line.text.match(/^\s*/u)?.[0] ?? ''
    const from = line.from + indent.length
    return {
      from,
      to: match ? line.from + match[0].length : from,
      insert: shouldRemove ? '' : prefix,
    }
  })

  view.dispatch({ changes })
  view.focus()
  return true
}

function applyInsertCommand(view: EditorView, template: string): boolean {
  const range = view.state.selection.main
  const line = view.state.doc.lineAt(range.from)
  const indent = line.text.match(/^\s*/u)?.[0] ?? ''
  const insertion = template
    .split('\n')
    .map((part, index) => (index === 0 ? part : `${indent}${part}`))
    .join('\n')

  view.dispatch({
    changes: { from: range.from, to: range.to, insert: insertion },
    selection: { anchor: range.from + insertion.length },
  })
  view.focus()
  return true
}
