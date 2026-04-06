import type { EditorOutlineItem } from './editor-server-runtime'

export type EditorLocalPreviewMode = 'rich-html' | 'sandbox' | 'syntax' | 'text'

export interface EditorLocalPreview {
  mode: EditorLocalPreviewMode
  html: string
  outline: EditorOutlineItem[]
  note: string | null
}

interface HighlightRule {
  pattern: RegExp
  replacement: string
}

const JAVASCRIPT_KEYWORDS =
  /\b(await|async|break|case|catch|class|const|continue|default|delete|else|export|extends|finally|for|from|function|if|import|let|new|return|switch|throw|try|typeof|var|while|yield)\b/g
const JSON_KEYWORDS = /\b(true|false|null)\b/g
const YAML_LITERALS = /\b(true|false|null|yes|no|on|off)\b/gi
const CSS_KEYWORDS =
  /\b(display|position|color|background|border|grid|flex|padding|margin|font|width|height|min|max|align|justify|transform|transition|animation)\b/g

export function buildEditorLocalPreview(formatId: string, content: string): EditorLocalPreview {
  switch (formatId) {
    case 'markdown':
      return {
        mode: 'rich-html',
        html: renderMarkdown(content),
        outline: extractMarkdownOutline(content),
        note: 'Предпросмотр обновляется сразу по мере редактирования.',
      }
    case 'html':
      return {
        mode: 'sandbox',
        html: sanitizeHtmlPreview(content),
        outline: extractHtmlOutline(content),
        note: 'Макет открывается в безопасном режиме без запуска встроенных скриптов.',
      }
    case 'css':
      return {
        mode: 'sandbox',
        html: buildCssPreviewDocument(content),
        outline: extractCssOutline(content),
        note: 'Показываем пример применения стилей; итоговую проверку можно запустить отдельно.',
      }
    case 'json': {
      const structured = renderJsonStructure(content)
      if (structured) {
        return {
          mode: 'rich-html',
          html: structured,
          outline: extractJsonOutline(content),
          note: 'Структура собирается автоматически, если JSON валиден.',
        }
      }

      return {
        mode: 'syntax',
        html: highlightEditorSyntax(formatId, content),
        outline: [],
        note: 'Пока в JSON есть ошибка, показываем исходный текст с подсветкой.',
      }
    }
    case 'txt':
      return {
        mode: 'text',
        html: escapeHtml(content),
        outline: extractTextOutline(content),
        note: 'Показываем документ без дополнительных преобразований.',
      }
    case 'javascript':
      return {
        mode: 'syntax',
        html: highlightEditorSyntax(formatId, content),
        outline: extractJavaScriptOutline(content),
        note: 'Код не выполняется: здесь доступна только подсветка и проверка структуры.',
      }
    case 'yaml':
      return {
        mode: 'syntax',
        html: highlightEditorSyntax(formatId, content),
        outline: extractYamlOutline(content),
        note: 'Для YAML доступен текстовый просмотр и отдельная проверка структуры.',
      }
    default:
      return {
        mode: 'syntax',
        html: highlightEditorSyntax(formatId, content),
        outline: [],
        note: null,
      }
  }
}

export function highlightEditorSyntax(formatId: string, content: string): string {
  let html = escapeHtml(content)
  const rules = buildHighlightRules(formatId)

  for (const rule of rules) {
    html = html.replace(rule.pattern, rule.replacement)
  }

  return html.replace(/\n/g, '<br />')
}

function buildHighlightRules(formatId: string): HighlightRule[] {
  switch (formatId) {
    case 'markdown':
      return [
        { pattern: /(^|\n)(#{1,6}\s.*)/g, replacement: '$1<span class="token-heading">$2</span>' },
        { pattern: /(```[\s\S]*?```)/g, replacement: '<span class="token-code">$1</span>' },
        { pattern: /(`[^`]+`)/g, replacement: '<span class="token-code">$1</span>' },
        { pattern: /(\[[^\]]+]\([^)]+\))/g, replacement: '<span class="token-link">$1</span>' },
        { pattern: /(^|\n)(>\s.*)/g, replacement: '$1<span class="token-comment">$2</span>' },
      ]
    case 'html':
      return [
        {
          pattern: /(&lt;\/?)([A-Za-z][\w:-]*)([^&]*?)(\/?&gt;)/g,
          replacement: '<span class="token-tag">$1<span class="token-keyword">$2</span>$3$4</span>',
        },
        {
          pattern: /(&quot;[^&]*?&quot;|"[^"\n]*")/g,
          replacement: '<span class="token-string">$1</span>',
        },
      ]
    case 'css':
      return [
        { pattern: /(\/\*[\s\S]*?\*\/)/g, replacement: '<span class="token-comment">$1</span>' },
        { pattern: /(@[\w-]+)/g, replacement: '<span class="token-keyword">$1</span>' },
        { pattern: CSS_KEYWORDS, replacement: '<span class="token-keyword">$&</span>' },
        {
          pattern: /(#[0-9a-fA-F]{3,8}|\b\d+(?:\.\d+)?(?:px|rem|em|%|vh|vw)?\b)/g,
          replacement: '<span class="token-number">$1</span>',
        },
      ]
    case 'javascript':
      return [
        {
          pattern: /(\/\*[\s\S]*?\*\/|\/\/.*$)/gm,
          replacement: '<span class="token-comment">$1</span>',
        },
        {
          pattern: /("(?:\\.|[^"\n])*"|'(?:\\.|[^'\n])*'|`(?:\\.|[^`])*`)/g,
          replacement: '<span class="token-string">$1</span>',
        },
        { pattern: JAVASCRIPT_KEYWORDS, replacement: '<span class="token-keyword">$&</span>' },
        { pattern: /\b\d+(?:\.\d+)?\b/g, replacement: '<span class="token-number">$&</span>' },
      ]
    case 'json':
      return [
        { pattern: /("(?:\\.|[^"\n])*")\s*:/g, replacement: '<span class="token-key">$1</span>:' },
        {
          pattern: /:\s*("(?:\\.|[^"\n])*")/g,
          replacement: ': <span class="token-string">$1</span>',
        },
        { pattern: /\b\d+(?:\.\d+)?\b/g, replacement: '<span class="token-number">$&</span>' },
        { pattern: JSON_KEYWORDS, replacement: '<span class="token-keyword">$&</span>' },
      ]
    case 'yaml':
      return [
        { pattern: /(^|\n)(\s*#.*)/g, replacement: '$1<span class="token-comment">$2</span>' },
        { pattern: /(^|\n)(\s*[\w-]+:)/g, replacement: '$1<span class="token-key">$2</span>' },
        {
          pattern: /("(?:\\.|[^"\n])*"|'(?:\\.|[^'\n])*')/g,
          replacement: '<span class="token-string">$1</span>',
        },
        { pattern: YAML_LITERALS, replacement: '<span class="token-keyword">$&</span>' },
      ]
    default:
      return []
  }
}

function renderMarkdown(content: string): string {
  const escaped = escapeHtml(content)
  const codeBlocks: string[] = []
  let prepared = escaped.replace(/```([\s\S]*?)```/g, (_, block: string) => {
    const token = `@@CODEBLOCK_${codeBlocks.length}@@`
    codeBlocks.push(`<pre><code>${block.trim()}</code></pre>`)
    return token
  })

  prepared = prepared
    .replace(/^######\s+(.*)$/gm, '<h6>$1</h6>')
    .replace(/^#####\s+(.*)$/gm, '<h5>$1</h5>')
    .replace(/^####\s+(.*)$/gm, '<h4>$1</h4>')
    .replace(/^###\s+(.*)$/gm, '<h3>$1</h3>')
    .replace(/^##\s+(.*)$/gm, '<h2>$1</h2>')
    .replace(/^#\s+(.*)$/gm, '<h1>$1</h1>')
    .replace(/^>\s?(.*)$/gm, '<blockquote>$1</blockquote>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/__(.+?)__/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*(?!\s)(.+?)(?<!\s)\*/g, '$1<em>$2</em>')
    .replace(/(^|[^_])_(?!\s)(.+?)(?<!\s)_/g, '$1<em>$2</em>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/!\[([^\]]*)]\(([^)]+)\)/g, '<span class="md-image">Image: $1</span>')
    .replace(
      /\[([^\]]+)]\(([^)]+)\)/g,
      '<a href="$2" target="_blank" rel="noreferrer noopener">$1</a>',
    )

  prepared = prepared.replace(
    /(?:^|\n)(?:-\s.+(?:\n|$))+?/g,
    (block) =>
      `<ul>${block
        .trim()
        .split('\n')
        .map((line) => `<li>${line.replace(/^-\s+/u, '')}</li>`)
        .join('')}</ul>`,
  )

  prepared = prepared.replace(
    /(?:^|\n)(?:\d+\.\s.+(?:\n|$))+?/g,
    (block) =>
      `<ol>${block
        .trim()
        .split('\n')
        .map((line) => `<li>${line.replace(/^\d+\.\s+/u, '')}</li>`)
        .join('')}</ol>`,
  )

  const paragraphs = prepared
    .split(/\n{2,}/)
    .map((block) => block.trim())
    .filter(Boolean)
    .map((block) => {
      if (
        block.startsWith('<h') ||
        block.startsWith('<ul>') ||
        block.startsWith('<ol>') ||
        block.startsWith('<blockquote>') ||
        block.startsWith('<pre>')
      ) {
        return block
      }

      return `<p>${block.replace(/\n/g, '<br />')}</p>`
    })

  let html = paragraphs.join('')
  for (const [index, codeBlock] of codeBlocks.entries()) {
    html = html.replace(`@@CODEBLOCK_${index}@@`, codeBlock)
  }

  return html || '<p class="editor-preview-empty">Документ пока пуст.</p>'
}

function sanitizeHtmlPreview(content: string): string {
  if (typeof DOMParser === 'undefined') {
    return content
  }

  const document = new DOMParser().parseFromString(content, 'text/html')
  document.querySelectorAll('script, iframe, object, embed').forEach((node) => node.remove())
  document.querySelectorAll('*').forEach((element) => {
    for (const attribute of element.attributes) {
      const attributeName = attribute.name.toLowerCase()
      const attributeValue = attribute.value.trim().toLowerCase()

      if (attributeName.startsWith('on')) {
        element.removeAttribute(attribute.name)
      }

      if (
        (attributeName === 'href' || attributeName === 'src') &&
        attributeValue.startsWith('javascript:')
      ) {
        element.removeAttribute(attribute.name)
      }
    }
  })

  return `<!doctype html><html><body>${document.body.innerHTML}</body></html>`
}

function buildCssPreviewDocument(content: string): string {
  const safeCss = content.replace(/<\/style/giu, '<\\/style')
  return `<!doctype html>
  <html>
    <head>
      <style>
        :root {
          color-scheme: light;
          font-family: Manrope, system-ui, sans-serif;
          background: #f4ede3;
          color: #193333;
        }
        body {
          margin: 0;
          padding: 24px;
          background:
            radial-gradient(circle at top left, rgba(255, 207, 143, 0.45), transparent 28%),
            linear-gradient(180deg, #efe5d6 0%, #e5ddce 100%);
        }
        .preview-shell {
          display: grid;
          gap: 16px;
        }
        .preview-card {
          padding: 18px;
          border-radius: 24px;
          background: rgba(255, 250, 242, 0.85);
          box-shadow:
            -10px -10px 24px rgba(255, 252, 245, 0.95),
            14px 16px 28px rgba(105, 88, 67, 0.18);
        }
        .preview-card strong {
          display: block;
          margin-bottom: 6px;
          font-size: 1rem;
        }
        .preview-grid {
          display: grid;
          grid-template-columns: repeat(3, minmax(0, 1fr));
          gap: 12px;
        }
        @media (max-width: 640px) {
          .preview-grid {
            grid-template-columns: 1fr;
          }
        }
        ${safeCss}
      </style>
    </head>
    <body>
      <section class="preview-shell">
        <article class="preview-card">
          <strong>Пример оформления</strong>
          <p>Безопасная область, где можно быстро оценить стили.</p>
        </article>
        <div class="preview-grid">
          <article class="preview-card"><strong>Карточка</strong><p>Сетка и отступы</p></article>
          <article class="preview-card"><strong>Блок</strong><p>Заголовок и описание</p></article>
          <article class="preview-card"><strong>Состояние</strong><p>Цвет и акцент</p></article>
        </div>
      </section>
    </body>
  </html>`
}

function renderJsonStructure(content: string): string | null {
  try {
    const value = JSON.parse(content) as unknown
    return `<div class="json-tree">${renderStructuredValue(value)}</div>`
  } catch {
    return null
  }
}

function renderStructuredValue(value: unknown): string {
  if (Array.isArray(value)) {
    return `<ol>${value.map((entry) => `<li>${renderStructuredValue(entry)}</li>`).join('')}</ol>`
  }

  if (value && typeof value === 'object') {
    return `<ul>${Object.entries(value as Record<string, unknown>)
      .map(
        ([key, entry]) =>
          `<li><strong>${escapeHtml(key)}</strong><div>${renderStructuredValue(entry)}</div></li>`,
      )
      .join('')}</ul>`
  }

  if (typeof value === 'string') {
    return `<span class="tree-string">"${escapeHtml(value)}"</span>`
  }

  return `<span class="tree-value">${escapeHtml(String(value))}</span>`
}

function extractMarkdownOutline(content: string): EditorOutlineItem[] {
  const outline: EditorOutlineItem[] = []

  content.replace(/^(#{1,6})\s+(.+)$/gmu, (_match, hashes: string, label: string) => {
    outline.push({
      id: `md-${outline.length + 1}`,
      label: label.trim(),
      depth: hashes.length,
      kind: 'heading',
    })
    return _match
  })

  return outline
}

function extractHtmlOutline(content: string): EditorOutlineItem[] {
  if (typeof DOMParser === 'undefined') {
    return []
  }

  const document = new DOMParser().parseFromString(content, 'text/html')
  return [...document.querySelectorAll('h1, h2, h3, h4, h5, h6')]
    .slice(0, 18)
    .map((heading, index) => ({
      id: `html-${index + 1}`,
      label: heading.textContent?.trim() || heading.tagName.toLowerCase(),
      depth: Number.parseInt(heading.tagName.slice(1), 10) || 1,
      kind: 'heading',
    }))
}

function extractCssOutline(content: string): EditorOutlineItem[] {
  const matches = content.matchAll(/^\s*([^@\s][^{]+|@[\w-]+[^{]*)\s*\{/gmu)
  return [...matches].slice(0, 18).map((match, index) => ({
    id: `css-${index + 1}`,
    label: (match[1] || '').trim(),
    depth: 1,
    kind: (match[1] || '').trim().startsWith('@') ? 'at-rule' : 'selector',
  }))
}

function extractJavaScriptOutline(content: string): EditorOutlineItem[] {
  const matches = content.matchAll(
    /^\s*(?:export\s+)?(?:async\s+)?(?:function\s+([A-Za-z_$][\w$]*)|class\s+([A-Za-z_$][\w$]*)|(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=)/gmu,
  )

  return [...matches].slice(0, 18).map((match, index) => {
    const label = match[1] || match[2] || match[3] || `symbol-${index + 1}`
    const kind = match[1] ? 'function' : match[2] ? 'class' : 'binding'
    return {
      id: `js-${index + 1}`,
      label,
      depth: 1,
      kind,
    }
  })
}

function extractJsonOutline(content: string): EditorOutlineItem[] {
  try {
    const value = JSON.parse(content) as unknown
    if (Array.isArray(value)) {
      return value.slice(0, 12).map((_entry, index) => ({
        id: `json-${index + 1}`,
        label: `[${index}]`,
        depth: 1,
        kind: 'array-item',
      }))
    }

    if (value && typeof value === 'object') {
      return Object.keys(value as Record<string, unknown>)
        .slice(0, 18)
        .map((key, index) => ({
          id: `json-${index + 1}`,
          label: key,
          depth: 1,
          kind: 'key',
        }))
    }
  } catch {
    return []
  }

  return []
}

function extractYamlOutline(content: string): EditorOutlineItem[] {
  const matches = content.matchAll(/^\s*([\w-]+):/gmu)
  return [...matches].slice(0, 18).map((match, index) => ({
    id: `yaml-${index + 1}`,
    label: match[1] || `key-${index + 1}`,
    depth: 1,
    kind: 'key',
  }))
}

function extractTextOutline(content: string): EditorOutlineItem[] {
  const matches = content.matchAll(/^([A-Z0-9][^\n]{2,80}:)\s*$/gmu)
  return [...matches].slice(0, 16).map((match, index) => ({
    id: `txt-${index + 1}`,
    label: match[1] || `section-${index + 1}`,
    depth: 1,
    kind: 'section',
  }))
}

function escapeHtml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}
