import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import {
  runEncodingTool,
  type EncodingMode,
  type EncodingStrategyId,
} from '../application/encoding-tools'
import { buildHashReport, type HashReport } from '../application/hash-tools'
import { inspectJwt } from '../application/jwt-tools'
import { analyzeLink } from '../application/link-tools'
import {
  analyzeTimestamp,
  buildBasicAuthHeader,
  generateUlid,
  generateUuid,
} from '../application/quick-utils'
import { validateStructuredText, type ValidationFormatId } from '../application/validation-tools'
import { listDevTools, resolveDevTool, type DevToolId } from '../domain/dev-tools-registry'
import { createProcessingTaskController } from '../../processing/application/processing-task-controller'

type HashSourceMode = 'text' | 'file'

interface PersistedDevToolsState {
  persistenceEnabled?: boolean
  activeToolId?: DevToolId
  encodingStrategyId?: EncodingStrategyId
  encodingMode?: EncodingMode
  encodingInput?: string
  hashSourceMode?: HashSourceMode
  hashTextInput?: string
  linkInput?: string
  linkStripTracking?: boolean
  linkRemoveFragment?: boolean
  linkSortParams?: boolean
  validationFormatId?: ValidationFormatId
  validationInput?: string
  timestampInput?: string
  basicAuthUsername?: string
}

const STORAGE_KEY = 'jack.dev-tools.workspace.v1'

export function useDevToolsWorkspace() {
  const tools = listDevTools()
  const persistenceEnabled = ref(false)
  const activeToolId = ref<DevToolId>('encoding')

  const encodingStrategyId = ref<EncodingStrategyId>('base64')
  const encodingMode = ref<EncodingMode>('encode')
  const encodingInput = ref('Привет, Jack\n{"channel":"share"}')

  const jwtInput = ref('')

  const hashSourceMode = ref<HashSourceMode>('text')
  const hashTextInput = ref('Подписываемый текст для сверки')
  const hashSecret = ref('')
  const hashFile = ref<File | null>(null)
  const hashReport = ref<HashReport | null>(null)
  const hashErrorMessage = ref('')
  const hashProgressMessage = ref('')
  const isHashing = ref(false)

  const linkInput = ref(
    'https://example.com/docs/getting-started?utm_source=jack&utm_medium=share&foo=bar#install',
  )
  const linkOptions = reactive({
    stripTracking: true,
    removeFragment: false,
    sortParams: true,
  })

  const validationFormatId = ref<ValidationFormatId>('json')
  const validationInput = ref('{\n  "project": "jack",\n  "env": "prod",\n  "ready": true\n}')

  const quickUuid = ref(generateUuid())
  const quickUlid = ref(generateUlid())
  const timestampInput = ref(String(Date.now()))
  const basicAuthUsername = ref('jack-user')
  const basicAuthPassword = ref('')

  const actionMessage = ref('')

  restoreFromStorage({
    persistenceEnabled,
    activeToolId,
    encodingStrategyId,
    encodingMode,
    encodingInput,
    hashSourceMode,
    hashTextInput,
    linkInput,
    linkOptions,
    validationFormatId,
    validationInput,
    timestampInput,
    basicAuthUsername,
  })

  const activeTool = computed(() => resolveDevTool(activeToolId.value) ?? tools[0])
  const encodingResult = computed(() =>
    runEncodingTool(encodingInput.value, encodingStrategyId.value, encodingMode.value),
  )
  const jwtResult = computed(() => inspectJwt(jwtInput.value))
  const linkResult = computed(() => analyzeLink(linkInput.value, linkOptions))
  const validationResult = computed(() =>
    validateStructuredText(validationInput.value, validationFormatId.value),
  )
  const timestampResult = computed(() => analyzeTimestamp(timestampInput.value))
  const basicAuthResult = computed(() =>
    buildBasicAuthHeader(basicAuthUsername.value, basicAuthPassword.value),
  )

  const hashTaskController = createProcessingTaskController<{
    sourceMode: HashSourceMode
    sourceLabel: string
  }>({ cloneSnapshot: (snapshot) => ({ ...snapshot }) })
  watch(
    [hashSourceMode, hashTextInput, hashSecret, hashFile],
    async () => {
      const task = hashTaskController.begin({
        sourceMode: hashSourceMode.value,
        sourceLabel:
          hashSourceMode.value === 'file' ? hashFile.value?.name || '' : 'Встроенный текст',
      })

      if (hashSourceMode.value === 'file' && !hashFile.value) {
        hashReport.value = null
        hashErrorMessage.value = ''
        isHashing.value = false
        hashTaskController.complete(task)
        return
      }

      isHashing.value = true
      hashErrorMessage.value = ''
      hashProgressMessage.value = 'Считаю хэши...'

      try {
        const report = await buildHashReport(
          hashSourceMode.value === 'file' ? hashFile.value! : hashTextInput.value,
          hashSecret.value,
          {
            signal: task.signal,
            reportProgress: (message) => {
              if (hashTaskController.isCurrent(task)) {
                hashProgressMessage.value = message
              }
            },
          },
        )
        if (!hashTaskController.isCurrent(task)) {
          return
        }
        hashReport.value = report
      } catch (error) {
        if (!hashTaskController.isCurrent(task)) {
          return
        }
        hashReport.value = null
        hashErrorMessage.value =
          error instanceof Error ? error.message : 'Не удалось собрать hash report.'
      } finally {
        if (hashTaskController.isCurrent(task)) {
          isHashing.value = false
          hashProgressMessage.value = ''
          hashTaskController.complete(task)
        }
      }
    },
    { immediate: true },
  )

  watch(
    () => ({
      persistenceEnabled: persistenceEnabled.value,
      activeToolId: activeToolId.value,
      encodingStrategyId: encodingStrategyId.value,
      encodingMode: encodingMode.value,
      encodingInput: encodingInput.value,
      hashSourceMode: hashSourceMode.value,
      hashTextInput: hashTextInput.value,
      linkInput: linkInput.value,
      linkStripTracking: linkOptions.stripTracking,
      linkRemoveFragment: linkOptions.removeFragment,
      linkSortParams: linkOptions.sortParams,
      validationFormatId: validationFormatId.value,
      validationInput: validationInput.value,
      timestampInput: timestampInput.value,
      basicAuthUsername: basicAuthUsername.value,
    }),
    (payload) => persistState(payload),
    { deep: true },
  )

  onBeforeUnmount(() => {
    hashTaskController.dispose()
    jwtInput.value = ''
    hashSecret.value = ''
    basicAuthPassword.value = ''
  })

  function selectTool(toolId: DevToolId): void {
    activeToolId.value = toolId
  }

  function setHashFile(file: File | null): void {
    hashFile.value = file
  }

  function clearHashFile(): void {
    hashFile.value = null
  }

  function regenerateUuid(): void {
    quickUuid.value = generateUuid()
  }

  function regenerateUlid(): void {
    quickUlid.value = generateUlid()
  }

  function useCurrentTimestamp(): void {
    timestampInput.value = String(Date.now())
  }

  async function copyText(value: string, label: string): Promise<void> {
    if (!value.trim()) {
      actionMessage.value = `${label}: пока нечего копировать`
      return
    }

    if (!navigator.clipboard) {
      actionMessage.value = 'Clipboard API недоступен в этом браузере.'
      return
    }

    await navigator.clipboard.writeText(value)
    actionMessage.value = `${label} скопирован в буфер`
  }

  function downloadText(
    fileName: string,
    value: string,
    mimeType = 'text/plain;charset=utf-8',
  ): void {
    const blob = new Blob([value], { type: mimeType })
    const objectUrl = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = objectUrl
    anchor.download = fileName
    document.body.append(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(objectUrl)
    actionMessage.value = `${fileName} сохранён`
  }

  return {
    tools,
    persistenceEnabled,
    activeToolId,
    activeTool,
    actionMessage,
    selectTool,

    encodingStrategyId,
    encodingMode,
    encodingInput,
    encodingResult,

    jwtInput,
    jwtResult,

    hashSourceMode,
    hashTextInput,
    hashSecret,
    hashFile,
    hashReport,
    hashErrorMessage,
    hashProgressMessage,
    isHashing,
    setHashFile,
    clearHashFile,

    linkInput,
    linkOptions,
    linkResult,

    validationFormatId,
    validationInput,
    validationResult,

    quickUuid,
    quickUlid,
    timestampInput,
    timestampResult,
    basicAuthUsername,
    basicAuthPassword,
    basicAuthResult,
    regenerateUuid,
    regenerateUlid,
    useCurrentTimestamp,

    copyText,
    downloadText,
  }
}

function persistState(payload: PersistedDevToolsState): void {
  if (typeof window === 'undefined') {
    return
  }

  if (payload.persistenceEnabled !== true) {
    window.localStorage.removeItem(STORAGE_KEY)
    return
  }

  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
}

function restoreFromStorage(state: {
  persistenceEnabled: { value: boolean }
  activeToolId: { value: DevToolId }
  encodingStrategyId: { value: EncodingStrategyId }
  encodingMode: { value: EncodingMode }
  encodingInput: { value: string }
  hashSourceMode: { value: HashSourceMode }
  hashTextInput: { value: string }
  linkInput: { value: string }
  linkOptions: { stripTracking: boolean; removeFragment: boolean; sortParams: boolean }
  validationFormatId: { value: ValidationFormatId }
  validationInput: { value: string }
  timestampInput: { value: string }
  basicAuthUsername: { value: string }
}): void {
  if (typeof window === 'undefined') {
    return
  }

  try {
    const rawValue = window.localStorage.getItem(STORAGE_KEY)
    if (!rawValue) {
      return
    }

    const parsed = JSON.parse(rawValue) as PersistedDevToolsState
    if (parsed.persistenceEnabled !== true) {
      window.localStorage.removeItem(STORAGE_KEY)
      return
    }

    state.persistenceEnabled.value = true
    if (parsed.activeToolId) {
      state.activeToolId.value = parsed.activeToolId
    }
    if (parsed.encodingStrategyId) {
      state.encodingStrategyId.value = parsed.encodingStrategyId
    }
    if (parsed.encodingMode) {
      state.encodingMode.value = parsed.encodingMode
    }
    if (typeof parsed.encodingInput === 'string') {
      state.encodingInput.value = parsed.encodingInput
    }
    if (parsed.hashSourceMode) {
      state.hashSourceMode.value = parsed.hashSourceMode
    }
    if (typeof parsed.hashTextInput === 'string') {
      state.hashTextInput.value = parsed.hashTextInput
    }
    if (typeof parsed.linkInput === 'string') {
      state.linkInput.value = parsed.linkInput
    }
    if (typeof parsed.linkStripTracking === 'boolean') {
      state.linkOptions.stripTracking = parsed.linkStripTracking
    }
    if (typeof parsed.linkRemoveFragment === 'boolean') {
      state.linkOptions.removeFragment = parsed.linkRemoveFragment
    }
    if (typeof parsed.linkSortParams === 'boolean') {
      state.linkOptions.sortParams = parsed.linkSortParams
    }
    if (parsed.validationFormatId) {
      state.validationFormatId.value = parsed.validationFormatId
    }
    if (typeof parsed.validationInput === 'string') {
      state.validationInput.value = parsed.validationInput
    }
    if (typeof parsed.timestampInput === 'string') {
      state.timestampInput.value = parsed.timestampInput
    }
    if (typeof parsed.basicAuthUsername === 'string') {
      state.basicAuthUsername.value = parsed.basicAuthUsername
    }

    // Перезаписываем legacy schema сразу после чтения: JWT, HMAC secret и
    // Basic Auth password никогда не должны переживать текущую вкладку.
    persistState({
      persistenceEnabled: true,
      activeToolId: state.activeToolId.value,
      encodingStrategyId: state.encodingStrategyId.value,
      encodingMode: state.encodingMode.value,
      encodingInput: state.encodingInput.value,
      hashSourceMode: state.hashSourceMode.value,
      hashTextInput: state.hashTextInput.value,
      linkInput: state.linkInput.value,
      linkStripTracking: state.linkOptions.stripTracking,
      linkRemoveFragment: state.linkOptions.removeFragment,
      linkSortParams: state.linkOptions.sortParams,
      validationFormatId: state.validationFormatId.value,
      validationInput: state.validationInput.value,
      timestampInput: state.timestampInput.value,
      basicAuthUsername: state.basicAuthUsername.value,
    })
  } catch {
    window.localStorage.removeItem(STORAGE_KEY)
  }
}
