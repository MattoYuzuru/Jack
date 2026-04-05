import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { findViewerDocumentMatches } from '../viewer-document'
import {
  buildPdfDocumentPreview,
  buildXlsxDocumentPreview,
} from '../viewer-document-preview'

const originalFetch = globalThis.fetch
const originalCreateObjectUrl = URL.createObjectURL
const originalRevokeObjectUrl = URL.revokeObjectURL

beforeEach(() => {
  globalThis.fetch = vi.fn() as typeof fetch
})

afterEach(() => {
  globalThis.fetch = originalFetch
  Object.defineProperty(URL, 'createObjectURL', {
    configurable: true,
    value: originalCreateObjectUrl,
  })
  Object.defineProperty(URL, 'revokeObjectURL', {
    configurable: true,
    value: originalRevokeObjectUrl,
  })
  vi.clearAllMocks()
})

describe('viewer document preview client', () => {
  it('builds a server-assisted pdf preview from backend artifacts', async () => {
    const fetchMock = vi.mocked(globalThis.fetch)
    const progressMessages: string[] = []

    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:document-preview'),
    })

    fetchMock
      .mockResolvedValueOnce(
        createJsonResponse({
          scope: 'viewer',
          phase: 'document-intelligence',
          jobTypes: [
            {
              jobType: 'DOCUMENT_PREVIEW',
              implemented: true,
              detail: 'Backend already supports document intelligence preview.',
            },
          ],
        }),
      )
      .mockResolvedValueOnce(createJsonResponse({ id: 'upload-document' }, 201))
      .mockResolvedValueOnce(createJsonResponse({ id: 'job-document' }, 202))
      .mockResolvedValueOnce(
        createJsonResponse({
          id: 'job-document',
          status: 'RUNNING',
          progressPercent: 35,
          message: 'Подготавливаю document intelligence payload и search layer.',
          errorMessage: null,
          artifacts: [],
        }),
      )
      .mockResolvedValueOnce(
        createJsonResponse({
          id: 'job-document',
          status: 'COMPLETED',
          progressPercent: 100,
          message: 'Document preview готов через backend Document intelligence service.',
          errorMessage: null,
          artifacts: [
            {
              id: 'document-manifest',
              kind: 'document-preview-manifest',
              fileName: 'document-preview-manifest.json',
              mediaType: 'application/json',
              sizeBytes: 512,
              createdAt: '2026-04-05T15:00:00Z',
              downloadPath: '/api/jobs/job-document/artifacts/document-manifest',
            },
            {
              id: 'document-binary',
              kind: 'document-preview-binary',
              fileName: 'report.preview.pdf',
              mediaType: 'application/pdf',
              sizeBytes: 2048,
              createdAt: '2026-04-05T15:00:00Z',
              downloadPath: '/api/jobs/job-document/artifacts/document-binary',
            },
          ],
        }),
      )
      .mockResolvedValueOnce(
        createJsonResponse({
          summary: [
            { label: 'Тип документа', value: 'PDF' },
            { label: 'Страниц', value: '3' },
          ],
          searchableText: 'Alpha beta gamma',
          warnings: ['Backend PDF text extraction completed.'],
          layout: {
            mode: 'pdf',
            pageCount: 3,
          },
          previewLabel: 'PDF server preview',
        }),
      )
      .mockResolvedValueOnce(
        new Response('pdf-binary', {
          status: 200,
          headers: { 'Content-Type': 'application/pdf' },
        }),
      )

    const result = await buildPdfDocumentPreview(
      new File(['pdf'], 'report.pdf', { type: 'application/pdf' }),
      (message) => {
        progressMessages.push(message)
      },
    )

    expect(fetchMock).toHaveBeenCalledTimes(7)
    expect(fetchMock.mock.calls[0]?.[0]).toBe('http://localhost:8080/api/capabilities/viewer')
    expect(fetchMock.mock.calls[1]?.[0]).toBe('http://localhost:8080/api/uploads')
    expect(fetchMock.mock.calls[2]?.[0]).toBe('http://localhost:8080/api/jobs')

    expect(result.previewLabel).toBe('PDF server preview')
    expect(result.searchableText).toBe('Alpha beta gamma')
    expect(result.warnings).toEqual(['Backend PDF text extraction completed.'])
    expect(result.layout.mode).toBe('pdf')
    expect(result.layout.mode === 'pdf' ? result.layout.objectUrl : '').toBe('blob:document-preview')
    expect(result.layout.mode === 'pdf' ? result.layout.pageCount : null).toBe(3)
    expect(progressMessages).toContain('Подготавливаю document intelligence payload и search layer.')
    expect(progressMessages).toContain('Загружаю document manifest и preview artifact с backend...')
  })

  it('builds a server-assisted workbook preview from backend manifest only', async () => {
    const fetchMock = vi.mocked(globalThis.fetch)

    fetchMock
      .mockResolvedValueOnce(
        createJsonResponse({
          scope: 'viewer',
          phase: 'document-intelligence',
          jobTypes: [
            {
              jobType: 'DOCUMENT_PREVIEW',
              implemented: true,
            },
          ],
        }),
      )
      .mockResolvedValueOnce(createJsonResponse({ id: 'upload-workbook' }, 201))
      .mockResolvedValueOnce(createJsonResponse({ id: 'job-workbook' }, 202))
      .mockResolvedValueOnce(
        createJsonResponse({
          id: 'job-workbook',
          status: 'COMPLETED',
          progressPercent: 100,
          message: 'Document preview готов через backend Document intelligence service.',
          errorMessage: null,
          artifacts: [
            {
              id: 'workbook-manifest',
              kind: 'document-preview-manifest',
              fileName: 'document-preview-manifest.json',
              mediaType: 'application/json',
              sizeBytes: 768,
              createdAt: '2026-04-05T15:00:00Z',
              downloadPath: '/api/jobs/job-workbook/artifacts/workbook-manifest',
            },
          ],
        }),
      )
      .mockResolvedValueOnce(
        createJsonResponse({
          summary: [{ label: 'Тип документа', value: 'XLSX' }],
          searchableText: 'Summary Viewer',
          warnings: [],
          layout: {
            mode: 'workbook',
            text: 'Summary Viewer',
            activeSheetIndex: 0,
            sheets: [
              {
                id: 'xlsx-sheet-1',
                name: 'Summary',
                table: {
                  columns: ['Name'],
                  rows: [['Viewer']],
                  totalRows: 1,
                  totalColumns: 1,
                  delimiter: '',
                },
              },
            ],
          },
          previewLabel: 'XLSX workbook adapter',
        }),
      )

    const result = await buildXlsxDocumentPreview(
      new File(['xlsx'], 'report.xlsx', {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      }),
    )

    expect(fetchMock).toHaveBeenCalledTimes(5)
    expect(result.previewLabel).toBe('XLSX workbook adapter')
    expect(result.layout.mode).toBe('workbook')
    expect(result.layout.mode === 'workbook' ? result.layout.sheets[0]?.name : '').toBe('Summary')
    expect(result.searchableText).toContain('Viewer')
  })

  it('builds capped search excerpts for normalized document text', () => {
    const matches = findViewerDocumentMatches(
      'Viewer foundation adds pdf preview, text search and document outline for the workspace.',
      'document',
    )

    expect(matches).toHaveLength(1)
    expect(matches[0]?.excerpt).toContain('document outline')
  })
})

function createJsonResponse(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      'Content-Type': 'application/json',
    },
  })
}
