import { describe, expect, it } from 'vitest'
import { decodeEditorFile, encodeEditorFile } from '../editor-file-codec'

describe('editor file codec', () => {
  it('round-trips Unicode with UTF-8 BOM and CRLF', () => {
    const source = 'Привет, Jack 👋\nВторая строка\n'
    const encoded = encodeEditorFile(source, 'utf-8-bom', 'crlf')
    const bytes = new Uint8Array(encoded)

    expect(Array.from(bytes.slice(0, 3))).toEqual([0xef, 0xbb, 0xbf])
    expect(new TextDecoder().decode(bytes.slice(3))).toContain('\r\n')
    expect(decodeEditorFile(encoded)).toEqual({
      content: source,
      encoding: 'utf-8-bom',
      newline: 'crlf',
    })
  })

  it('keeps plain UTF-8 and LF byte-stable', () => {
    const source = 'alpha\nbeta\n'
    const encoded = encodeEditorFile(source, 'utf-8', 'lf')

    expect(new TextDecoder().decode(encoded)).toBe(source)
    expect(decodeEditorFile(encoded)).toEqual({
      content: source,
      encoding: 'utf-8',
      newline: 'lf',
    })
  })

  it('rejects invalid UTF-8 instead of silently replacing bytes', () => {
    expect(() => decodeEditorFile(Uint8Array.from([0xc3, 0x28]).buffer)).toThrow(
      'The encoded data was not valid for encoding utf-8',
    )
  })
})
