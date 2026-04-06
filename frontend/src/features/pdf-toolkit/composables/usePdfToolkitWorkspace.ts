import { computed, onBeforeUnmount, ref, shallowRef } from 'vue'
import {
  openPdfToolkitSource,
  runServerPdfToolkitJob,
  type OpenPdfToolkitSourceResult,
  type PdfToolkitDocumentPreview,
  type PdfToolkitFact,
  type ServerPdfToolkitManifest,
  type ServerPdfToolkitResult,
} from '../application/pdf-toolkit-server-runtime'
import {
  getPdfToolkitAcceptAttribute,
  getPdfToolkitImportAcceptAttribute,
  getPdfToolkitOperations,
  type PdfToolkitOperationDefinition,
} from '../domain/pdf-toolkit-registry'
import {
  cancelProcessingJob,
  ProcessingJobCancelledError,
  type ProcessingJobStatus,
} from '../../processing/application/processing-client'

interface PdfToolkitResultViewModel {
  id: string
  operation: ServerPdfToolkitManifest['operation']
  fileName: string
  previewFileName: string
  resultMimeType: string
  previewMimeType: string
  warnings: string[]
  sourceFacts: PdfToolkitFact[]
  resultFacts: PdfToolkitFact[]
  operationFacts: PdfToolkitFact[]
  backendJobId: string
  runtimeLabel: string
  createdAt: string
  resultObjectUrl: string
  previewObjectUrl: string
  textObjectUrl: string | null
  textFileName: string | null
  resultBlob: Blob
  previewBlob: Blob
  textBlob: Blob | null
}

type SignaturePlacement = 'bottom-right' | 'bottom-left' | 'top-right' | 'top-left' | 'center'

const MAX_RESULT_HISTORY = 8
const SIGNATURE_PLACEMENTS: Array<{ value: SignaturePlacement; label: string }> = [
  { value: 'bottom-right', label: 'Bottom right' },
  { value: 'bottom-left', label: 'Bottom left' },
  { value: 'top-right', label: 'Top right' },
  { value: 'top-left', label: 'Top left' },
  { value: 'center', label: 'Center' },
]
const ROTATION_OPTIONS = ['90', '180', '270'] as const

export function usePdfToolkitWorkspace() {
  const document = shallowRef<PdfToolkitDocumentPreview | null>(null)
  const lockedDocumentFile = shallowRef<File | null>(null)
  const importedFromLabel = ref('')
  const resultHistory = ref<PdfToolkitResultViewModel[]>([])
  const selectedResultId = ref<string | null>(null)
  const availableOperations = ref<PdfToolkitOperationDefinition[]>([])
  const pdfAcceptAttribute = ref('.pdf')
  const importAcceptAttribute = ref('')
  const isLoading = ref(false)
  const isProcessing = ref(false)
  const isCancelling = ref(false)
  const errorMessage = ref('')
  const processingMessage = ref('')
  const selectedOperationId = ref<PdfToolkitOperationDefinition['id']>('merge')
  const mergeFiles = ref<File[]>([])
  const splitRangesInput = ref('1-2\n3')
  const pageSelection = ref('')
  const rotationDegrees = ref<(typeof ROTATION_OPTIONS)[number]>('90')
  const pageOrderInput = ref('')
  const ocrLanguage = ref('eng')
  const signatureText = ref('Jack QA')
  const signaturePlacement = ref<SignaturePlacement>('bottom-right')
  const includeSignatureDate = ref(true)
  const signatureImageFile = shallowRef<File | null>(null)
  const redactTermsInput = ref('')
  const currentPassword = ref('')
  const userPassword = ref('')
  const ownerPassword = ref('')
  const allowPrinting = ref(true)
  const allowCopying = ref(true)
  const allowModifying = ref(false)
  const activeJobId = ref('')
  const activeJobStatus = ref<ProcessingJobStatus | ''>('')
  const activeJobProgressPercent = ref(0)
  let capabilityRequest: Promise<void> | null = null

  const activeOperation = computed(
    () =>
      availableOperations.value.find((operation) => operation.id === selectedOperationId.value) ??
      null,
  )
  const result = computed(() => {
    const selectedId = selectedResultId.value
    if (!selectedId) {
      return resultHistory.value[0] ?? null
    }

    return resultHistory.value.find((entry) => entry.id === selectedId) ?? null
  })
  const isLockedDocument = computed(() => !!lockedDocumentFile.value && !document.value)
  const currentFile = computed(() => document.value?.file ?? lockedDocumentFile.value ?? null)
  const hasDocument = computed(() => !!document.value || !!lockedDocumentFile.value)
  const hasResultHistory = computed(() => resultHistory.value.length > 0)
  const canRunOperation = computed(() => {
    if (!activeOperation.value || !currentFile.value || isProcessing.value || isLoading.value) {
      return false
    }

    if (
      isLockedDocument.value &&
      activeOperation.value.id !== 'unlock' &&
      activeOperation.value.id !== 'protect'
    ) {
      return false
    }

    return activeOperation.value.available
  })

  async function hydrateCapabilities(): Promise<void> {
    if (capabilityRequest) {
      return capabilityRequest
    }

    capabilityRequest = Promise.all([
      getPdfToolkitAcceptAttribute(),
      getPdfToolkitImportAcceptAttribute(),
      getPdfToolkitOperations(),
    ])
      .then(([acceptAttribute, importAttribute, operations]) => {
        pdfAcceptAttribute.value = acceptAttribute
        importAcceptAttribute.value = importAttribute
        availableOperations.value = operations.filter((operation) => operation.available)
        if (
          !availableOperations.value.some((operation) => operation.id === selectedOperationId.value)
        ) {
          selectedOperationId.value = availableOperations.value[0]?.id ?? 'merge'
        }
      })
      .finally(() => {
        capabilityRequest = null
      })

    return capabilityRequest
  }

  async function openSource(file: File): Promise<void> {
    isLoading.value = true
    errorMessage.value = ''
    processingMessage.value = 'Подготавливаю документ к работе в PDF Toolkit...'

    try {
      await hydrateCapabilities()
      const prepared = await openPdfToolkitSource(file, (message) => {
        processingMessage.value = message
      })
      applyPreparedDocument(prepared)
      resetOperationStateForNewDocument()
      processingMessage.value = ''
    } catch (error) {
      await handleOpenFailure(file, error)
    } finally {
      isLoading.value = false
    }
  }

  async function runOperation(): Promise<void> {
    if (!activeOperation.value || !currentFile.value) {
      return
    }

    const splitRanges = splitRangesInput.value
      .split('\n')
      .map((value) => value.trim())
      .filter(Boolean)
    const redactTerms = redactTermsInput.value
      .split('\n')
      .map((value) => value.trim())
      .filter(Boolean)
    const pageOrder = pageOrderInput.value
      .split(',')
      .map((value) => value.trim())
      .filter(Boolean)
      .map((value) => Number.parseInt(value, 10))
      .filter((value) => Number.isFinite(value))

    errorMessage.value = ''
    isProcessing.value = true
    processingMessage.value = 'Запускаю операцию с PDF...'

    try {
      const response = await runServerPdfToolkitJob({
        file: currentFile.value,
        operation: activeOperation.value.id,
        additionalPdfFiles: activeOperation.value.id === 'merge' ? mergeFiles.value : [],
        signatureImageFile: activeOperation.value.id === 'sign' ? signatureImageFile.value : null,
        parameters: {
          splitRanges: activeOperation.value.id === 'split' ? splitRanges : undefined,
          pageSelection: supportsPageSelection(activeOperation.value.id)
            ? normalizedPageSelection()
            : undefined,
          rotationDegrees:
            activeOperation.value.id === 'rotate'
              ? Number.parseInt(rotationDegrees.value, 10)
              : undefined,
          pageOrder: activeOperation.value.id === 'reorder' ? pageOrder : undefined,
          ocrLanguage:
            activeOperation.value.id === 'ocr' ? ocrLanguage.value.trim() || 'eng' : undefined,
          signatureText:
            activeOperation.value.id === 'sign' ? signatureText.value.trim() : undefined,
          signaturePlacement:
            activeOperation.value.id === 'sign' ? signaturePlacement.value : undefined,
          includeSignatureDate:
            activeOperation.value.id === 'sign' ? includeSignatureDate.value : undefined,
          redactTerms: activeOperation.value.id === 'redact' ? redactTerms : undefined,
          currentPassword: currentPassword.value.trim() || undefined,
          userPassword:
            activeOperation.value.id === 'protect'
              ? userPassword.value.trim() || undefined
              : undefined,
          ownerPassword:
            activeOperation.value.id === 'protect'
              ? ownerPassword.value.trim() || undefined
              : undefined,
          allowPrinting: activeOperation.value.id === 'protect' ? allowPrinting.value : undefined,
          allowCopying: activeOperation.value.id === 'protect' ? allowCopying.value : undefined,
          allowModifying: activeOperation.value.id === 'protect' ? allowModifying.value : undefined,
        },
        reportProgress: (message) => {
          processingMessage.value = message
        },
        onJobCreated: (job) => {
          activeJobId.value = job.id
          activeJobStatus.value = job.status
          activeJobProgressPercent.value = job.progressPercent
        },
        onJobUpdate: (job) => {
          activeJobStatus.value = job.status
          activeJobProgressPercent.value = job.progressPercent
        },
      })

      const resultEntry = createResultEntry(response)
      resultHistory.value = [resultEntry, ...resultHistory.value].slice(0, MAX_RESULT_HISTORY)
      selectedResultId.value = resultEntry.id
      while (resultHistory.value.length > MAX_RESULT_HISTORY) {
        const removed = resultHistory.value.pop()
        if (removed) {
          revokeResultEntry(removed)
        }
      }

      if (response.manifest.resultMediaType === 'application/pdf') {
        await replaceCurrentDocument(
          new File([response.resultBlob], response.resultArtifact.fileName, {
            type: 'application/pdf',
          }),
          response.manifest.targetAdapterLabel,
          'direct-pdf',
        )
      }

      if (activeOperation.value.id === 'unlock' || activeOperation.value.id === 'protect') {
        currentPassword.value = activeOperation.value.id === 'unlock' ? '' : currentPassword.value
      }

      processingMessage.value =
        response.manifest.operation === 'SPLIT'
          ? 'Разделение завершено: архив готов, а первый фрагмент уже доступен для просмотра.'
          : 'Операция с PDF завершена.'
    } catch (error) {
      if (error instanceof ProcessingJobCancelledError) {
        processingMessage.value = 'Операция с PDF остановлена.'
      } else {
        errorMessage.value =
          error instanceof Error ? error.message : 'PDF toolkit завершился с ошибкой.'
        processingMessage.value = ''
      }
    } finally {
      isProcessing.value = false
    }
  }

  async function cancelOperation(): Promise<void> {
    if (!activeJobId.value || isCancelling.value) {
      return
    }

    isCancelling.value = true
    errorMessage.value = ''
    try {
      const cancelledJob = await cancelProcessingJob(activeJobId.value)
      activeJobStatus.value = cancelledJob.status
      activeJobProgressPercent.value = cancelledJob.progressPercent
      processingMessage.value = cancelledJob.message || 'Операция с PDF отменена.'
    } catch (error) {
      errorMessage.value =
        error instanceof Error ? error.message : 'Не удалось отменить операцию с PDF.'
    } finally {
      isCancelling.value = false
    }
  }

  async function loadResultAsCurrent(
    entry: PdfToolkitResultViewModel | null = result.value,
  ): Promise<void> {
    if (!entry) {
      return
    }

    const targetBlob =
      entry.resultMimeType === 'application/pdf'
        ? entry.resultBlob
        : entry.previewMimeType === 'application/pdf'
          ? entry.previewBlob
          : null

    if (!targetBlob) {
      return
    }

    await replaceCurrentDocument(
      new File(
        [targetBlob],
        entry.resultMimeType === 'application/pdf' ? entry.fileName : entry.previewFileName,
        {
          type: 'application/pdf',
        },
      ),
      entry.runtimeLabel,
      'direct-pdf',
    )
  }

  function downloadResult(entry: PdfToolkitResultViewModel | null = result.value): void {
    if (!entry) {
      return
    }

    downloadBlob(entry.resultObjectUrl, entry.fileName)
  }

  function downloadPreview(entry: PdfToolkitResultViewModel | null = result.value): void {
    if (!entry) {
      return
    }

    downloadBlob(entry.previewObjectUrl, entry.previewFileName)
  }

  function downloadTextArtifact(entry: PdfToolkitResultViewModel | null = result.value): void {
    if (!entry?.textObjectUrl || !entry.textFileName) {
      return
    }

    downloadBlob(entry.textObjectUrl, entry.textFileName)
  }

  function selectResult(id: string): void {
    selectedResultId.value = id
  }

  function setMergeFiles(files: File[]): void {
    mergeFiles.value = files
  }

  function setSignatureImageFile(file: File | null): void {
    signatureImageFile.value = file
  }

  function clearDocument(): void {
    disposeCurrentDocument()
    lockedDocumentFile.value = null
    importedFromLabel.value = ''
    errorMessage.value = ''
    processingMessage.value = ''
  }

  onBeforeUnmount(() => {
    disposeCurrentDocument()
    for (const entry of resultHistory.value) {
      revokeResultEntry(entry)
    }
  })

  return {
    document,
    lockedDocumentFile,
    importedFromLabel,
    result,
    resultHistory,
    availableOperations,
    activeOperation,
    pdfAcceptAttribute,
    importAcceptAttribute,
    isLoading,
    isProcessing,
    isCancelling,
    isLockedDocument,
    hasDocument,
    hasResultHistory,
    canRunOperation,
    errorMessage,
    processingMessage,
    selectedOperationId,
    mergeFiles,
    splitRangesInput,
    pageSelection,
    rotationDegrees,
    rotationOptions: ROTATION_OPTIONS,
    pageOrderInput,
    ocrLanguage,
    signatureText,
    signaturePlacement,
    signaturePlacements: SIGNATURE_PLACEMENTS,
    includeSignatureDate,
    signatureImageFile,
    redactTermsInput,
    currentPassword,
    userPassword,
    ownerPassword,
    allowPrinting,
    allowCopying,
    allowModifying,
    activeJobId,
    activeJobStatus,
    activeJobProgressPercent,
    openSource,
    runOperation,
    cancelOperation,
    loadResultAsCurrent,
    downloadResult,
    downloadPreview,
    downloadTextArtifact,
    selectResult,
    setMergeFiles,
    setSignatureImageFile,
    clearDocument,
  }

  async function replaceCurrentDocument(
    file: File,
    sourceLabel: string,
    sourceRouteKind: 'direct-pdf' | 'convert-to-pdf',
  ): Promise<void> {
    disposeCurrentDocument()
    lockedDocumentFile.value = null
    const prepared = await openPdfToolkitSource(file)
    document.value = prepared.document
    document.value.sourceLabel = sourceLabel
    document.value.sourceRouteKind = sourceRouteKind
    importedFromLabel.value = prepared.importedFrom?.label || ''
  }

  function applyPreparedDocument(prepared: OpenPdfToolkitSourceResult): void {
    disposeCurrentDocument()
    document.value = prepared.document
    lockedDocumentFile.value = null
    importedFromLabel.value = prepared.importedFrom?.label || ''
  }

  async function handleOpenFailure(file: File, error: unknown): Promise<void> {
    disposeCurrentDocument()
    if (file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf')) {
      lockedDocumentFile.value = file
      errorMessage.value =
        error instanceof Error
          ? `${error.message} Если это защищённый PDF, используй Unlock flow и укажи current password.`
          : 'Не удалось открыть PDF preview. Если файл защищён, используй Unlock flow.'
      processingMessage.value = ''
      return
    }

    lockedDocumentFile.value = null
    importedFromLabel.value = ''
    errorMessage.value = error instanceof Error ? error.message : 'Не удалось подготовить source.'
    processingMessage.value = ''
  }

  function disposeCurrentDocument(): void {
    if (document.value) {
      URL.revokeObjectURL(document.value.objectUrl)
      document.value = null
    }
  }

  function resetOperationStateForNewDocument(): void {
    mergeFiles.value = []
    pageSelection.value = ''
    pageOrderInput.value = ''
    currentPassword.value = ''
    errorMessage.value = ''
  }

  function normalizedPageSelection(): string | undefined {
    const normalized = pageSelection.value.trim()
    return normalized || undefined
  }
}

function supportsPageSelection(operationId: PdfToolkitOperationDefinition['id']): boolean {
  return operationId === 'rotate' || operationId === 'sign' || operationId === 'redact'
}

function createResultEntry(response: ServerPdfToolkitResult): PdfToolkitResultViewModel {
  return {
    id: crypto.randomUUID(),
    operation: response.manifest.operation,
    fileName: response.resultArtifact.fileName,
    previewFileName: response.previewArtifact.fileName,
    resultMimeType: response.resultArtifact.mediaType,
    previewMimeType: response.previewArtifact.mediaType,
    warnings: response.manifest.warnings,
    sourceFacts: response.manifest.sourceFacts,
    resultFacts: response.manifest.resultFacts,
    operationFacts: response.manifest.operationFacts,
    backendJobId: response.job.id,
    runtimeLabel: response.manifest.runtimeLabel,
    createdAt: response.job.completedAt || new Date().toISOString(),
    resultObjectUrl: URL.createObjectURL(response.resultBlob),
    previewObjectUrl: URL.createObjectURL(response.previewBlob),
    textObjectUrl: response.textBlob ? URL.createObjectURL(response.textBlob) : null,
    textFileName: response.textArtifact?.fileName || null,
    resultBlob: response.resultBlob,
    previewBlob: response.previewBlob,
    textBlob: response.textBlob,
  }
}

function revokeResultEntry(entry: PdfToolkitResultViewModel): void {
  URL.revokeObjectURL(entry.resultObjectUrl)
  if (entry.previewObjectUrl !== entry.resultObjectUrl) {
    URL.revokeObjectURL(entry.previewObjectUrl)
  }
  if (entry.textObjectUrl) {
    URL.revokeObjectURL(entry.textObjectUrl)
  }
}

function downloadBlob(objectUrl: string, fileName: string): void {
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = fileName
  anchor.rel = 'noopener'
  document.body.append(anchor)
  anchor.click()
  anchor.remove()
}
