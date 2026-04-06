import { computed, reactive, ref, watch } from 'vue'
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

type HashSourceMode = 'text' | 'file'

interface PersistedDevToolsState {
  activeToolId?: DevToolId
  encodingStrategyId?: EncodingStrategyId
  encodingMode?: EncodingMode
  encodingInput?: string
  jwtInput?: string
  hashSourceMode?: HashSourceMode
  hashTextInput?: string
  hashSecret?: string
  linkInput?: string
  linkStripTracking?: boolean
  linkRemoveFragment?: boolean
  linkSortParams?: boolean
  validationFormatId?: ValidationFormatId
  validationInput?: string
  timestampInput?: string
  basicAuthUsername?: string
  basicAuthPassword?: string
}

const STORAGE_KEY = 'jack.dev-tools.workspace.v1'

export function useDevToolsWorkspace() {
  const tools = listDevTools()
  const activeToolId = ref<DevToolId>('encoding')

  const encodingStrategyId = ref<EncodingStrategyId>('base64')
  const encodingMode = ref<EncodingMode>('encode')
  const encodingInput = ref('Hello Jack\n{"mode":"dev"}')

  const jwtInput = ref(
    'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImRldi1rZXkifQ.eyJpc3MiOiJqYWNrLmxvY2FsIiwic3ViIjoiZGV2LXVzZXIiLCJhdWQiOiJ3ZWItdG9vbHMiLCJpYXQiOjE3MDAwMDAwMDAsIm5iZiI6MTcwMDAwMDAwMCwiZXhwIjo0MTAyNDQ0ODAwLCJzY29wZSI6InJlYWQ6dG9vbHMgd3JpdGU6dG9vbHMifQ.signature',
  )

  const hashSourceMode = ref<HashSourceMode>('text')
  const hashTextInput = ref('Jack integrity payload')
  const hashSecret = ref('')
  const hashFile = ref<File | null>(null)
  const hashReport = ref<HashReport | null>(null)
  const hashErrorMessage = ref('')
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
  const validationInput = ref('{\n  "service": "jack",\n  "iteration": 7,\n  "ready": true\n}')

  const quickUuid = ref(generateUuid())
  const quickUlid = ref(generateUlid())
  const timestampInput = ref(String(Date.now()))
  const basicAuthUsername = ref('jack-user')
  const basicAuthPassword = ref('local-secret')

  const actionMessage = ref('')

  restoreFromStorage({
    activeToolId,
    encodingStrategyId,
    encodingMode,
    encodingInput,
    jwtInput,
    hashSourceMode,
    hashTextInput,
    hashSecret,
    linkInput,
    linkOptions,
    validationFormatId,
    validationInput,
    timestampInput,
    basicAuthUsername,
    basicAuthPassword,
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

  let hashRunId = 0
  watch(
    [hashSourceMode, hashTextInput, hashSecret, hashFile],
    async () => {
      const currentRunId = ++hashRunId

      if (hashSourceMode.value === 'file' && !hashFile.value) {
        hashReport.value = null
        hashErrorMessage.value = ''
        isHashing.value = false
        return
      }

      isHashing.value = true
      hashErrorMessage.value = ''

      try {
        const report = await buildHashReport(
          hashSourceMode.value === 'file' ? hashFile.value! : hashTextInput.value,
          hashSecret.value,
        )
        if (currentRunId !== hashRunId) {
          return
        }
        hashReport.value = report
      } catch (error) {
        if (currentRunId !== hashRunId) {
          return
        }
        hashReport.value = null
        hashErrorMessage.value =
          error instanceof Error ? error.message : 'Не удалось собрать hash report.'
      } finally {
        if (currentRunId === hashRunId) {
          isHashing.value = false
        }
      }
    },
    { immediate: true },
  )

  watch(
    () => ({
      activeToolId: activeToolId.value,
      encodingStrategyId: encodingStrategyId.value,
      encodingMode: encodingMode.value,
      encodingInput: encodingInput.value,
      jwtInput: jwtInput.value,
      hashSourceMode: hashSourceMode.value,
      hashTextInput: hashTextInput.value,
      hashSecret: hashSecret.value,
      linkInput: linkInput.value,
      linkStripTracking: linkOptions.stripTracking,
      linkRemoveFragment: linkOptions.removeFragment,
      linkSortParams: linkOptions.sortParams,
      validationFormatId: validationFormatId.value,
      validationInput: validationInput.value,
      timestampInput: timestampInput.value,
      basicAuthUsername: basicAuthUsername.value,
      basicAuthPassword: basicAuthPassword.value,
    }),
    (payload) => persistState(payload),
    { deep: true },
  )

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
      actionMessage.value = `${label}: nothing to copy`
      return
    }

    if (!navigator.clipboard) {
      actionMessage.value = 'Clipboard API недоступен в этом браузере.'
      return
    }

    await navigator.clipboard.writeText(value)
    actionMessage.value = `${label} copied to clipboard`
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
    actionMessage.value = `${fileName} downloaded`
  }

  return {
    tools,
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

  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
}

function restoreFromStorage(state: {
  activeToolId: { value: DevToolId }
  encodingStrategyId: { value: EncodingStrategyId }
  encodingMode: { value: EncodingMode }
  encodingInput: { value: string }
  jwtInput: { value: string }
  hashSourceMode: { value: HashSourceMode }
  hashTextInput: { value: string }
  hashSecret: { value: string }
  linkInput: { value: string }
  linkOptions: { stripTracking: boolean; removeFragment: boolean; sortParams: boolean }
  validationFormatId: { value: ValidationFormatId }
  validationInput: { value: string }
  timestampInput: { value: string }
  basicAuthUsername: { value: string }
  basicAuthPassword: { value: string }
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
    if (typeof parsed.jwtInput === 'string') {
      state.jwtInput.value = parsed.jwtInput
    }
    if (parsed.hashSourceMode) {
      state.hashSourceMode.value = parsed.hashSourceMode
    }
    if (typeof parsed.hashTextInput === 'string') {
      state.hashTextInput.value = parsed.hashTextInput
    }
    if (typeof parsed.hashSecret === 'string') {
      state.hashSecret.value = parsed.hashSecret
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
    if (typeof parsed.basicAuthPassword === 'string') {
      state.basicAuthPassword.value = parsed.basicAuthPassword
    }
  } catch {
    window.localStorage.removeItem(STORAGE_KEY)
  }
}
