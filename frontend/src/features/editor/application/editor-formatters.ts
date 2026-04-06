type PrettierModule = typeof import('prettier/standalone')

interface LoadedFormatterRuntime {
  prettier: PrettierModule
  babelPlugin: typeof import('prettier/plugins/babel')
  estreePlugin: typeof import('prettier/plugins/estree')
  htmlPlugin: typeof import('prettier/plugins/html')
  markdownPlugin: typeof import('prettier/plugins/markdown')
  postcssPlugin: typeof import('prettier/plugins/postcss')
  yamlPlugin: typeof import('prettier/plugins/yaml')
}

let formatterRuntimePromise: Promise<LoadedFormatterRuntime> | null = null

export function canFormatEditorFormat(formatId: string): boolean {
  return ['markdown', 'html', 'css', 'javascript', 'json', 'yaml'].includes(formatId)
}

export async function formatEditorContent(formatId: string, content: string): Promise<string> {
  if (!canFormatEditorFormat(formatId)) {
    return content
  }

  const runtime = await loadFormatterRuntime()

  try {
    switch (formatId) {
      case 'markdown':
        return await runtime.prettier.format(content, {
          parser: 'markdown',
          plugins: [runtime.markdownPlugin],
          proseWrap: 'preserve',
        })
      case 'html':
        return await runtime.prettier.format(content, {
          parser: 'html',
          plugins: [runtime.htmlPlugin],
        })
      case 'css':
        return await runtime.prettier.format(content, {
          parser: 'css',
          plugins: [runtime.postcssPlugin],
        })
      case 'javascript':
        return await runtime.prettier.format(content, {
          parser: 'babel',
          plugins: [runtime.babelPlugin, runtime.estreePlugin],
          semi: false,
          singleQuote: true,
        })
      case 'json':
        return await runtime.prettier.format(content, {
          parser: 'json-stringify',
          plugins: [runtime.babelPlugin, runtime.estreePlugin],
        })
      case 'yaml':
        return await runtime.prettier.format(content, {
          parser: 'yaml',
          plugins: [runtime.yamlPlugin],
        })
      default:
        return content
    }
  } catch (error) {
    throw new Error(resolveFormatterErrorMessage(formatId, error))
  }
}

function resolveFormatterErrorMessage(formatId: string, error: unknown): string {
  const fallback = `Не удалось отформатировать ${formatId} документ.`
  if (!(error instanceof Error) || !error.message.trim()) {
    return fallback
  }

  return `${fallback} ${error.message.trim()}`
}

async function loadFormatterRuntime(): Promise<LoadedFormatterRuntime> {
  if (!formatterRuntimePromise) {
    formatterRuntimePromise = Promise.all([
      import('prettier/standalone'),
      import('prettier/plugins/babel'),
      import('prettier/plugins/estree'),
      import('prettier/plugins/html'),
      import('prettier/plugins/markdown'),
      import('prettier/plugins/postcss'),
      import('prettier/plugins/yaml'),
    ]).then(
      ([
        prettier,
        babelPlugin,
        estreePlugin,
        htmlPlugin,
        markdownPlugin,
        postcssPlugin,
        yamlPlugin,
      ]) => ({
        prettier,
        babelPlugin,
        estreePlugin,
        htmlPlugin,
        markdownPlugin,
        postcssPlugin,
        yamlPlugin,
      }),
    )
  }

  return formatterRuntimePromise
}
