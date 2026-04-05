import type { ViewerDocumentFact } from './viewer-document'

export function buildTextSummary(kind: string, text: string): ViewerDocumentFact[] {
  return [
    { label: 'Тип документа', value: kind },
    { label: 'Строки', value: String(countLines(text)) },
    { label: 'Слова', value: String(countWords(text)) },
    { label: 'Символы', value: String(text.length) },
  ]
}

export function splitParagraphs(text: string, limit = 18): string[] {
  return text
    .split(/\n{2,}/u)
    .map((paragraph) => paragraph.trim())
    .filter(Boolean)
    .slice(0, limit)
}

export function readDocumentText(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer)

  if (bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) {
    return new TextDecoder('utf-8').decode(bytes)
  }

  if (bytes[0] === 0xff && bytes[1] === 0xfe) {
    return new TextDecoder('utf-16le').decode(bytes)
  }

  if (bytes[0] === 0xfe && bytes[1] === 0xff) {
    return new TextDecoder('utf-16be').decode(bytes)
  }

  return new TextDecoder('utf-8').decode(bytes)
}

export function normalizeExtractedText(text: string): string {
  return text
    .replace(/\r\n/gu, '\n')
    .replace(/\r/gu, '\n')
    .replace(/[ \t]+\n/gu, '\n')
    .replace(/\n[ \t]+/gu, '\n')
    .replace(/\n{3,}/gu, '\n\n')
    .trim()
}

function countLines(text: string): number {
  if (!text.length) {
    return 0
  }

  return text.split(/\r?\n/u).length
}

function countWords(text: string): number {
  const matches = text.match(/\S+/gu)
  return matches?.length ?? 0
}
