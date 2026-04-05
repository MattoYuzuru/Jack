export interface ViewerDocumentFact {
  label: string
  value: string
}

export interface ViewerDocumentOutlineItem {
  id: string
  label: string
  level: number
}

export interface ViewerDocumentTablePreview {
  columns: string[]
  rows: string[][]
  totalRows: number
  totalColumns: number
  delimiter: string
}

export interface ViewerDocumentSheetPreview {
  id: string
  name: string
  table: ViewerDocumentTablePreview
}

export interface ViewerDocumentSlidePreview {
  id: string
  title: string
  bullets: string[]
}

export interface ViewerDocumentSearchMatch {
  id: string
  excerpt: string
}

export type ViewerDocumentLayout =
  | {
      mode: 'pdf'
      objectUrl: string
      pageCount: number | null
    }
  | {
      mode: 'text'
      text: string
      paragraphs: string[]
    }
  | {
      mode: 'table'
      text: string
      table: ViewerDocumentTablePreview
    }
  | {
      mode: 'html'
      text: string
      srcDoc: string
      outline: ViewerDocumentOutlineItem[]
    }
  | {
      mode: 'workbook'
      text: string
      sheets: ViewerDocumentSheetPreview[]
      activeSheetIndex: number
    }
  | {
      mode: 'slides'
      text: string
      slides: ViewerDocumentSlidePreview[]
    }

export interface ViewerDocumentPreviewPayload {
  summary: ViewerDocumentFact[]
  searchableText: string
  warnings: string[]
  layout: ViewerDocumentLayout
  previewLabel: string
}

export function findViewerDocumentMatches(
  searchableText: string,
  query: string,
  maxMatches = 10,
): ViewerDocumentSearchMatch[] {
  const normalizedQuery = query.trim().toLowerCase()
  if (!normalizedQuery) {
    return []
  }

  const normalizedText = searchableText.toLowerCase()
  const matches: ViewerDocumentSearchMatch[] = []
  let cursor = 0

  // Держим поиск предсказуемым и дешёвым: линейно собираем первые внятные совпадения
  // с коротким контекстом, а не строим отдельный индекс под каждый формат документа.
  while (matches.length < maxMatches) {
    const index = normalizedText.indexOf(normalizedQuery, cursor)
    if (index === -1) {
      break
    }

    matches.push({
      id: `match-${index}`,
      excerpt: buildExcerpt(searchableText, index, normalizedQuery.length),
    })

    cursor = index + normalizedQuery.length
  }

  return matches
}

function buildExcerpt(text: string, index: number, length: number): string {
  const start = Math.max(index - 44, 0)
  const end = Math.min(index + length + 72, text.length)
  const excerpt = text.slice(start, end).replace(/\s+/gu, ' ').trim()

  if (start > 0 && end < text.length) {
    return `…${excerpt}…`
  }

  if (start > 0) {
    return `…${excerpt}`
  }

  if (end < text.length) {
    return `${excerpt}…`
  }

  return excerpt
}
