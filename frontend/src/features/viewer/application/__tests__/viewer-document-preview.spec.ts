import JSZip from 'jszip'
import { describe, expect, it } from 'vitest'
import { findViewerDocumentMatches } from '../viewer-document'
import {
  buildDocxDocumentPreview,
  buildPptxDocumentPreview,
  buildXlsxDocumentPreview,
  decodeRtfDocument,
  parseDelimitedTextDocument,
  sanitizeHtmlDocument,
} from '../viewer-document-preview'

describe('viewer document preview', () => {
  it('parses csv content into a bounded table preview', () => {
    const table = parseDelimitedTextDocument(
      'Name,Role,Team\nAlice,Engineer,Viewer\nBob,Designer,Viewer\nCara,QA,Viewer',
    )

    expect(table.columns).toEqual(['Name', 'Role', 'Team'])
    expect(table.rows).toEqual([
      ['Alice', 'Engineer', 'Viewer'],
      ['Bob', 'Designer', 'Viewer'],
      ['Cara', 'QA', 'Viewer'],
    ])
    expect(table.totalRows).toBe(3)
    expect(table.totalColumns).toBe(3)
  })

  it('extracts readable text from simple rtf content', () => {
    const text = decodeRtfDocument('{\\rtf1\\ansi Hello\\par Viewer\\tab Workspace}')

    expect(text).toBe('Hello\nViewer\tWorkspace')
  })

  it('sanitizes html previews and keeps outline/text content', () => {
    const preview = sanitizeHtmlDocument(
      '<html><body><h1>Viewer</h1><p onclick="alert(1)">Docs</p><script>alert(1)</script></body></html>',
    )

    expect(preview.outline).toEqual([{ id: 'heading-1', label: 'Viewer', level: 1 }])
    expect(preview.textContent).toBe('Viewer Docs')
    expect(preview.srcDoc).not.toContain('<script>')
    expect(preview.srcDoc).not.toContain('onclick=')
    expect(preview.warnings).toContain('Из HTML preview удалены активные или потенциально опасные узлы.')
  })

  it('builds capped search excerpts for normalized document text', () => {
    const matches = findViewerDocumentMatches(
      'Viewer foundation adds pdf preview, text search and document outline for the workspace.',
      'document',
    )

    expect(matches).toHaveLength(1)
    expect(matches[0]?.excerpt).toContain('document outline')
  })

  it('builds a simplified docx preview from an OOXML package', async () => {
    const zip = new JSZip()
    zip.file(
      'word/document.xml',
      `<?xml version="1.0" encoding="UTF-8"?>
      <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
        <w:body>
          <w:p>
            <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
            <w:r><w:t>Viewer Docs</w:t></w:r>
          </w:p>
          <w:p><w:r><w:t>Foundation paragraph</w:t></w:r></w:p>
          <w:tbl>
            <w:tr><w:tc><w:p><w:r><w:t>Name</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Value</w:t></w:r></w:p></w:tc></w:tr>
            <w:tr><w:tc><w:p><w:r><w:t>Search</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Enabled</w:t></w:r></w:p></w:tc></w:tr>
          </w:tbl>
        </w:body>
      </w:document>`,
    )

    const file = new File([toArrayBuffer(await zip.generateAsync({ type: 'uint8array' }))], 'viewer.docx')
    const preview = await buildDocxDocumentPreview(file)

    expect(preview.layout.mode).toBe('html')
    expect(preview.searchableText).toContain('Foundation paragraph')
    expect(preview.layout.mode === 'html' ? preview.layout.outline : []).toEqual([
      { id: 'docx-heading-1', label: 'Viewer Docs', level: 1 },
    ])
  })

  it('builds workbook preview from a minimal xlsx package', async () => {
    const zip = new JSZip()
    zip.file(
      'xl/workbook.xml',
      `<?xml version="1.0" encoding="UTF-8"?>
      <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
        <sheets>
          <sheet name="Summary" sheetId="1" r:id="rId1"/>
        </sheets>
      </workbook>`,
    )
    zip.file(
      'xl/_rels/workbook.xml.rels',
      `<?xml version="1.0" encoding="UTF-8"?>
      <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
        <Relationship Id="rId1" Type="worksheet" Target="worksheets/sheet1.xml"/>
      </Relationships>`,
    )
    zip.file(
      'xl/sharedStrings.xml',
      `<?xml version="1.0" encoding="UTF-8"?>
      <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
        <si><t>Name</t></si>
        <si><t>Status</t></si>
        <si><t>Viewer</t></si>
      </sst>`,
    )
    zip.file(
      'xl/worksheets/sheet1.xml',
      `<?xml version="1.0" encoding="UTF-8"?>
      <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
        <sheetData>
          <row r="1">
            <c r="A1" t="s"><v>0</v></c>
            <c r="B1" t="s"><v>1</v></c>
          </row>
          <row r="2">
            <c r="A2" t="s"><v>2</v></c>
            <c r="B2"><v>1</v></c>
          </row>
        </sheetData>
      </worksheet>`,
    )

    const file = new File([toArrayBuffer(await zip.generateAsync({ type: 'uint8array' }))], 'viewer.xlsx')
    const preview = await buildXlsxDocumentPreview(file)

    expect(preview.layout.mode).toBe('workbook')
    expect(preview.layout.mode === 'workbook' ? preview.layout.sheets[0]?.name : '').toBe('Summary')
    expect(preview.searchableText).toContain('Viewer')
  })

  it('builds slide preview from a minimal pptx package', async () => {
    const zip = new JSZip()
    zip.file(
      'ppt/presentation.xml',
      `<?xml version="1.0" encoding="UTF-8"?>
      <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
        <p:sldIdLst>
          <p:sldId id="256" r:id="rId1"/>
        </p:sldIdLst>
      </p:presentation>`,
    )
    zip.file(
      'ppt/_rels/presentation.xml.rels',
      `<?xml version="1.0" encoding="UTF-8"?>
      <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
        <Relationship Id="rId1" Type="slide" Target="slides/slide1.xml"/>
      </Relationships>`,
    )
    zip.file(
      'ppt/slides/slide1.xml',
      `<?xml version="1.0" encoding="UTF-8"?>
      <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
        <p:cSld>
          <p:spTree>
            <p:sp><p:txBody><a:p><a:r><a:t>Viewer Deck</a:t></a:r></a:p></p:txBody></p:sp>
            <p:sp><p:txBody><a:p><a:r><a:t>First bullet</a:t></a:r></a:p></p:txBody></p:sp>
          </p:spTree>
        </p:cSld>
      </p:sld>`,
    )

    const file = new File([toArrayBuffer(await zip.generateAsync({ type: 'uint8array' }))], 'viewer.pptx')
    const preview = await buildPptxDocumentPreview(file)

    expect(preview.layout.mode).toBe('slides')
    expect(preview.layout.mode === 'slides' ? preview.layout.slides[0]?.title : '').toBe('Viewer Deck')
    expect(preview.searchableText).toContain('First bullet')
  })
})

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  return new Uint8Array(bytes).buffer
}
