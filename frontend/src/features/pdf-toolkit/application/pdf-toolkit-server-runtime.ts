import {
  awaitProcessingJob,
  createProcessingJob,
  ensureProcessingCapability,
  requestProcessingBlob,
  requestProcessingJson,
  uploadProcessingFile,
  type ProcessingArtifact,
  type ProcessingJobResponse,
} from '../../processing/application/processing-client'
import {
  runServerImageConvert,
  runServerOfficeConvert,
  type ConverterFact,
} from '../../converter/application/converter-server-runtime'
import { resolveServerViewerPreview } from '../../viewer/application/viewer-server-preview'
import {
  resolvePdfToolkitDirectSource,
  resolvePdfToolkitImportSource,
  type PdfToolkitSourceDefinition,
} from '../domain/pdf-toolkit-registry'

export interface PdfToolkitFact {
  label: string
  value: string
}

export interface ServerPdfToolkitManifest {
  uploadId: string
  originalFileName: string
  operation:
    | 'MERGE'
    | 'SPLIT'
    | 'ROTATE'
    | 'REORDER'
    | 'OCR'
    | 'SIGN'
    | 'REDACT'
    | 'PROTECT'
    | 'UNLOCK'
  resultFileName: string
  resultMediaType: string
  previewFileName: string
  previewMediaType: string
  previewKind: 'document'
  sourcePageCount: number | null
  resultPageCount: number | null
  sourceAdapterLabel: string
  targetAdapterLabel: string
  runtimeLabel: string
  sourceFacts: PdfToolkitFact[]
  resultFacts: PdfToolkitFact[]
  operationFacts: PdfToolkitFact[]
  warnings: string[]
  generatedAt: string
}

export interface ServerPdfToolkitResult {
  job: ProcessingJobResponse
  manifestArtifact: ProcessingArtifact
  resultArtifact: ProcessingArtifact
  previewArtifact: ProcessingArtifact
  textArtifact: ProcessingArtifact | null
  manifest: ServerPdfToolkitManifest
  resultBlob: Blob
  previewBlob: Blob
  textBlob: Blob | null
}

export interface PdfToolkitDocumentPreview {
  file: File
  sourceLabel: string
  sourceRouteKind: 'direct-pdf' | 'convert-to-pdf'
  summary: ConverterFact[]
  searchableText: string
  warnings: string[]
  pageCount: number | null
  previewLabel: string
  objectUrl: string
}

export interface OpenPdfToolkitSourceResult {
  document: PdfToolkitDocumentPreview
  importedFrom: PdfToolkitSourceDefinition | null
}

export interface RunPdfToolkitJobInput {
  file: File
  operation:
    | 'merge'
    | 'split'
    | 'rotate'
    | 'reorder'
    | 'ocr'
    | 'sign'
    | 'redact'
    | 'protect'
    | 'unlock'
  parameters?: Record<string, unknown>
  additionalPdfFiles?: File[]
  signatureImageFile?: File | null
  reportProgress?: (message: string) => void
  onJobCreated?: (job: ProcessingJobResponse) => void
  onJobUpdate?: (job: ProcessingJobResponse) => void
}

const PDF_TOOLKIT_JOB_TYPE = 'PDF_TOOLKIT'

export async function openPdfToolkitSource(
  file: File,
  reportProgress?: (message: string) => void,
): Promise<OpenPdfToolkitSourceResult> {
  const directSource = await resolvePdfToolkitDirectSource(file.name, file.type)
  if (directSource?.available) {
    return {
      document: await loadPreparedPdfDocument(
        file,
        directSource.routeLabel,
        directSource.routeKind,
        reportProgress,
      ),
      importedFrom: null,
    }
  }

  const importSource = await resolvePdfToolkitImportSource(file.name, file.type)
  if (!importSource?.available) {
    throw new Error(
      importSource?.availabilityDetail ||
        'PDF Toolkit принимает PDF и форматы, которые можно сначала перевести в PDF.',
    )
  }

  const importedPdf = await convertSourceToPdf(file, importSource, reportProgress)
  return {
    document: await loadPreparedPdfDocument(
      importedPdf.file,
      importSource.routeLabel,
      importSource.routeKind,
      reportProgress,
    ),
    importedFrom: importSource,
  }
}

export async function runServerPdfToolkitJob(
  input: RunPdfToolkitJobInput,
): Promise<ServerPdfToolkitResult> {
  input.reportProgress?.('Проверяю доступность инструментов PDF...')
  await ensureProcessingCapability('pdf-toolkit', PDF_TOOLKIT_JOB_TYPE)

  input.reportProgress?.('Загружаю основной PDF...')
  const primaryUpload = await uploadProcessingFile(input.file)

  let additionalUploadIds: string[] | undefined
  if (input.additionalPdfFiles?.length) {
    input.reportProgress?.('Загружаю дополнительные PDF...')
    const uploads = await Promise.all(
      input.additionalPdfFiles.map((file) => uploadProcessingFile(file)),
    )
    additionalUploadIds = uploads.map((upload) => upload.id)
  }

  let signatureImageUploadId: string | undefined
  if (input.signatureImageFile) {
    input.reportProgress?.('Загружаю изображение подписи...')
    const upload = await uploadProcessingFile(input.signatureImageFile)
    signatureImageUploadId = upload.id
  }

  input.reportProgress?.('Запускаю обработку PDF...')
  const createdJob = await createProcessingJob({
    uploadId: primaryUpload.id,
    jobType: PDF_TOOLKIT_JOB_TYPE,
    parameters: {
      operation: input.operation,
      additionalUploadIds,
      signatureImageUploadId,
      ...input.parameters,
    },
  })
  input.onJobCreated?.(createdJob)

  const completedJob = await awaitProcessingJob(createdJob.id, {
    reportProgress: input.reportProgress,
    timeoutMessage:
      'Обработка PDF заняла слишком много времени. Попробуй меньший файл или повтори позже.',
    onUpdate: input.onJobUpdate,
  })

  input.reportProgress?.('Загружаю готовый PDF и материалы результата...')
  return downloadServerPdfToolkitArtifacts(completedJob)
}

export async function downloadServerPdfToolkitArtifacts(
  job: ProcessingJobResponse,
): Promise<ServerPdfToolkitResult> {
  const manifestArtifact = job.artifacts.find(
    (artifact) => artifact.kind === 'pdf-toolkit-manifest',
  )
  const resultArtifact = job.artifacts.find((artifact) => artifact.kind === 'pdf-toolkit-binary')
  const previewArtifact = job.artifacts.find((artifact) => artifact.kind === 'pdf-toolkit-preview')
  const textArtifact =
    job.artifacts.find((artifact) => artifact.kind === 'pdf-toolkit-text') || null

  if (!manifestArtifact || !resultArtifact || !previewArtifact) {
    throw new Error('Операция завершилась без обязательных файлов результата.')
  }

  const [manifest, resultBlob, previewBlob, textBlob] = await Promise.all([
    requestProcessingJson<ServerPdfToolkitManifest>(manifestArtifact.downloadPath),
    requestProcessingBlob(resultArtifact.downloadPath),
    requestProcessingBlob(previewArtifact.downloadPath),
    textArtifact ? requestProcessingBlob(textArtifact.downloadPath) : Promise.resolve(null),
  ])

  return {
    job,
    manifestArtifact,
    resultArtifact,
    previewArtifact,
    textArtifact,
    manifest,
    resultBlob,
    previewBlob,
    textBlob,
  }
}

async function convertSourceToPdf(
  file: File,
  importSource: PdfToolkitSourceDefinition,
  reportProgress?: (message: string) => void,
): Promise<{ file: File }> {
  reportProgress?.(`Подготавливаю ${importSource.label} к работе с PDF...`)

  if (importSource.requiredJobTypes.includes('IMAGE_CONVERT')) {
    const result = await runServerImageConvert({
      file,
      targetExtension: 'pdf',
      maxWidth: null,
      maxHeight: null,
      presetLabel: 'Импорт в PDF',
      reportProgress,
    })

    return {
      file: new File([result.resultBlob], result.resultArtifact.fileName, {
        type: 'application/pdf',
      }),
    }
  }

  const result = await runServerOfficeConvert({
    file,
    targetExtension: 'pdf',
    maxWidth: null,
    maxHeight: null,
    presetLabel: 'Импорт в PDF',
    reportProgress,
  })

  return {
    file: new File([result.resultBlob], result.resultArtifact.fileName, {
      type: 'application/pdf',
    }),
  }
}

async function loadPreparedPdfDocument(
  file: File,
  sourceLabel: string,
  sourceRouteKind: 'direct-pdf' | 'convert-to-pdf',
  reportProgress?: (message: string) => void,
): Promise<PdfToolkitDocumentPreview> {
  reportProgress?.('Подготавливаю предпросмотр документа...')
  const preview = await resolveServerViewerPreview(file, reportProgress)

  if (preview.kind !== 'document' || preview.layout.mode !== 'pdf') {
    throw new Error('Не удалось получить корректный PDF для предпросмотра.')
  }

  return {
    file,
    sourceLabel,
    sourceRouteKind,
    summary: preview.summary,
    searchableText: preview.searchableText,
    warnings: preview.warnings,
    pageCount: preview.layout.pageCount,
    previewLabel: preview.previewLabel,
    objectUrl: preview.layout.objectUrl,
  }
}
