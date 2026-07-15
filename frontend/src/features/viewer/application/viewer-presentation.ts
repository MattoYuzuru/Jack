import type { ViewerResolvedEntry } from './viewer-runtime'

export interface ViewerFact {
  label: string
  value: string
}

export function formatViewerPreviewLabel(selection: ViewerResolvedEntry | null): string {
  if (!selection) {
    return ''
  }

  if (selection.kind === 'image') {
    return selection.format.previewPipeline === 'browser-native'
      ? 'Мгновенный просмотр'
      : 'Подготовленный просмотр'
  }

  if (selection.kind === 'video') {
    return 'Видеоплеер'
  }

  if (selection.kind === 'audio') {
    return 'Аудиоплеер'
  }

  if (selection.kind !== 'document') {
    return 'Просмотр'
  }

  switch (selection.layout.mode) {
    case 'pdf':
      return 'Постраничный просмотр'
    case 'table':
      return 'Табличный просмотр'
    case 'html':
      return 'Веб-предпросмотр'
    case 'workbook':
      return 'Рабочая книга'
    case 'slides':
      return 'Просмотр слайдов'
    case 'database':
      return 'Структура базы'
    default:
      return 'Текстовый просмотр'
  }
}

export function buildViewerFacts(selection: ViewerResolvedEntry | null): ViewerFact[] {
  if (!selection) {
    return []
  }

  const items = [
    { label: 'Имя файла', value: selection.file.name },
    {
      label: 'Размер',
      value: new Intl.NumberFormat('ru-RU').format(selection.file.size) + ' байт',
    },
    { label: 'Расширение', value: selection.extension || 'неизвестно' },
    { label: 'MIME', value: selection.file.type || 'Не определён' },
  ]

  if (selection.kind === 'image') {
    items.push({
      label: 'Размерность',
      value: `${selection.dimensions.width} x ${selection.dimensions.height}`,
    })
    items.push({ label: 'Режим просмотра', value: formatViewerPreviewLabel(selection) })
    items.push(...normalizeViewerFacts(selection.metadata.summary))
  }

  if (selection.kind === 'document' || selection.kind === 'video' || selection.kind === 'audio') {
    items.push({ label: 'Режим просмотра', value: formatViewerPreviewLabel(selection) })
    items.push(...normalizeViewerFacts(selection.summary))
  }

  return items
}

function normalizeViewerFacts(facts: Array<{ label: string; value: string }>): ViewerFact[] {
  return facts.map((fact) => ({
    label: formatViewerFactLabel(fact.label),
    value: formatViewerFactValue(fact.value),
  }))
}

function formatViewerFactLabel(label: string): string {
  const labels: Record<string, string> = {
    Headings: 'Заголовки',
    Delimiter: 'Разделитель',
    Sandbox: 'Режим HTML',
    'Top-level keys': 'Ключи верхнего уровня',
    'Root node': 'Корневой узел',
    'Outline entries': 'Элементы структуры',
    Sheets: 'Листы',
    Rows: 'Строки',
    Columns: 'Колонки',
    Views: 'Представления',
    Triggers: 'Триггеры',
    Sections: 'Разделы',
    Blocks: 'Блоки',
    'Top-level symbols': 'Символы верхнего уровня',
    Root: 'Корень',
    'Режим preview': 'Режим просмотра',
  }

  return labels[label] ?? label
}

function formatViewerFactValue(value: string): string {
  const values: Record<string, string> = {
    'Browser preview only': 'Без извлечения текста',
    'Backend srcdoc': 'Безопасный встроенный просмотр',
    'Backend PDF text extraction': 'С поиском по тексту',
    'Rendered article': 'Статья',
    'Structured config': 'Структурный просмотр',
    'Config review': 'Проверка конфигурации',
    'Schema read': 'Просмотр структуры',
    'Config table': 'Таблица переменных',
    'Delimited table preview': 'Табличный просмотр',
    'Tabbed table preview': 'Таблица с таб-разделителями',
    'PDF server preview': 'Постраничный просмотр',
    'HTML sanitized preview': 'Безопасный HTML',
    'Markdown reading preview': 'Чтение Markdown',
    'JSON structured preview': 'Структурный JSON',
    'YAML structured preview': 'Структурный YAML',
    'XML structure preview': 'Структура XML',
    'Environment config preview': 'Переменные окружения',
  }

  return values[value] ?? value
}
