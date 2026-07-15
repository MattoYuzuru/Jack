export type EditorEncoding = 'utf-8' | 'utf-8-bom'
export type EditorNewline = 'lf' | 'crlf'

export interface DecodedEditorFile {
  content: string
  encoding: EditorEncoding
  newline: EditorNewline
}

const UTF8_BOM = [0xef, 0xbb, 0xbf] as const

export function decodeEditorFile(buffer: ArrayBuffer): DecodedEditorFile {
  const bytes = new Uint8Array(buffer)
  const hasUtf8Bom = UTF8_BOM.every((byte, index) => bytes[index] === byte)
  const decoded = new TextDecoder('utf-8', { fatal: true }).decode(
    hasUtf8Bom ? bytes.subarray(UTF8_BOM.length) : bytes,
  )

  return {
    content: decoded.replace(/\r\n?/gu, '\n'),
    encoding: hasUtf8Bom ? 'utf-8-bom' : 'utf-8',
    newline: decoded.includes('\r\n') ? 'crlf' : 'lf',
  }
}

export function encodeEditorFile(
  content: string,
  encoding: EditorEncoding,
  newline: EditorNewline,
): ArrayBuffer {
  const normalized = newline === 'crlf' ? content.replace(/\n/gu, '\r\n') : content
  const encoded = new TextEncoder().encode(normalized)
  const result = new Uint8Array(encoded.length + (encoding === 'utf-8-bom' ? UTF8_BOM.length : 0))

  if (encoding === 'utf-8-bom') {
    result.set(UTF8_BOM)
    result.set(encoded, UTF8_BOM.length)
  } else {
    result.set(encoded)
  }

  // File/Blob принимают ArrayBuffer без неоднозначности SharedArrayBuffer в TypeScript 6.
  return result.buffer
}
