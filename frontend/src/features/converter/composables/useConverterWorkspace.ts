import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import {
  createConverterRuntime,
  type ConverterPreparedSource,
  type ConverterResult,
} from '../application/converter-runtime'
import {
  getConverterCapabilityMatrix,
  type ConverterScenarioDefinition,
  type ConverterTargetFormatDefinition,
} from '../domain/converter-registry'
import {
  resolveConverterPresetFromDefinitions,
  type ConverterPresetDefinition,
} from '../domain/converter-presets'

interface ConverterResultViewModel extends ConverterResult {
  objectUrl: string
}

const converterRuntime = createConverterRuntime()

export function useConverterWorkspace() {
  const prepared = shallowRef<ConverterPreparedSource | null>(null)
  const result = shallowRef<ConverterResultViewModel | null>(null)
  const isLoading = ref(false)
  const isConverting = ref(false)
  const errorMessage = ref('')
  const processingMessage = ref('')
  const converterAcceptAttribute = ref('')
  const availablePresets = ref<ConverterPresetDefinition[]>([])
  const imageScenarios = ref<ConverterScenarioDefinition[]>([])
  const documentScenarios = ref<ConverterScenarioDefinition[]>([])
  const selectedTargetExtension = ref('')
  const selectedPresetId = ref<ConverterPresetDefinition['id']>('original')
  const quality = ref(0.9)
  const backgroundColor = ref('#fffaf0')
  let capabilityMatrixRequest: Promise<void> | null = null

  const availableTargets = computed(() => prepared.value?.targets ?? [])
  const activeTarget = computed<ConverterTargetFormatDefinition | null>(
    () =>
      availableTargets.value.find((target) => target.extension === selectedTargetExtension.value) ??
      null,
  )
  const activePreset = computed(() =>
    resolveConverterPresetFromDefinitions(availablePresets.value, selectedPresetId.value),
  )

  async function ensureCapabilityMatrix(): Promise<void> {
    if (!capabilityMatrixRequest) {
      capabilityMatrixRequest = loadCapabilityMatrix().catch((error) => {
        capabilityMatrixRequest = null
        throw error
      })
    }

    return capabilityMatrixRequest
  }

  async function loadCapabilityMatrix(): Promise<void> {
    const matrix = await getConverterCapabilityMatrix()
    converterAcceptAttribute.value = matrix.acceptAttribute
    availablePresets.value = (matrix.presets as ConverterPresetDefinition[]).filter(
      (preset) => preset.available,
    )
    imageScenarios.value = matrix.scenarios.filter(
      (scenario) => scenario.family === 'image' && scenario.available,
    )
    documentScenarios.value = matrix.scenarios.filter(
      (scenario) => scenario.family === 'document' && scenario.available,
    )
  }

  void ensureCapabilityMatrix().catch(() => undefined)

  watch([activeTarget, activePreset], ([target, preset]) => {
    if (!target || !preset) {
      return
    }

    quality.value = preset.preferredQuality ?? target.defaultQuality ?? quality.value
    backgroundColor.value = preset.defaultBackgroundColor ?? backgroundColor.value
  })

  function releaseResult() {
    if (result.value) {
      URL.revokeObjectURL(result.value.objectUrl)
    }
  }

  async function selectFile(file: File) {
    releaseResult()
    prepared.value = null
    result.value = null
    errorMessage.value = ''
    processingMessage.value = 'Подготавливаю source-сценарии для выбранного файла...'
    isLoading.value = true

    try {
      await ensureCapabilityMatrix()
      const inspected = await converterRuntime.inspect(file)

      if (!inspected || !inspected.targets.length) {
        throw new Error('Для выбранного файла пока нет зарегистрированного сценария конвертации.')
      }

      prepared.value = inspected
      selectedTargetExtension.value = inspected.targets[0]?.extension ?? ''
      selectedPresetId.value = 'original'
    } catch (error) {
      errorMessage.value =
        error instanceof Error
          ? error.message
          : 'Не удалось подготовить конвертер к выбранному файлу.'
    } finally {
      isLoading.value = false
      if (!prepared.value) {
        processingMessage.value = ''
      }
    }
  }

  function clearSelection() {
    releaseResult()
    prepared.value = null
    result.value = null
    errorMessage.value = ''
    processingMessage.value = ''
    isLoading.value = false
    isConverting.value = false
    selectedTargetExtension.value = ''
    selectedPresetId.value = 'original'
  }

  async function convert() {
    if (!prepared.value || !selectedTargetExtension.value) {
      return
    }

    releaseResult()
    result.value = null
    errorMessage.value = ''
    processingMessage.value = 'Собираю итоговый target через processing pipeline...'
    isConverting.value = true

    try {
      const converted = await converterRuntime.convert({
        prepared: prepared.value,
        targetExtension: selectedTargetExtension.value,
        presetId: selectedPresetId.value,
        quality: quality.value,
        backgroundColor: backgroundColor.value,
        onProgress(message) {
          processingMessage.value = message
        },
      })

      result.value = {
        ...converted,
        objectUrl: URL.createObjectURL(converted.previewBlob),
      }
    } catch (error) {
      errorMessage.value =
        error instanceof Error ? error.message : 'Не удалось выполнить конвертацию.'
    } finally {
      isConverting.value = false
      if (!errorMessage.value) {
        processingMessage.value = ''
      }
    }
  }

  function downloadResult() {
    if (!result.value) {
      return
    }

    const downloadUrl = URL.createObjectURL(result.value.blob)
    const anchor = document.createElement('a')
    anchor.href = downloadUrl
    anchor.download = result.value.fileName
    anchor.click()
    URL.revokeObjectURL(downloadUrl)
  }

  onBeforeUnmount(() => {
    releaseResult()
  })

  return {
    prepared,
    result,
    availablePresets,
    availableTargets,
    activeTarget,
    activePreset,
    isLoading,
    isConverting,
    errorMessage,
    processingMessage,
    selectedTargetExtension,
    selectedPresetId,
    quality,
    backgroundColor,
    converterAcceptAttribute,
    imageScenarios,
    documentScenarios,
    selectFile,
    clearSelection,
    convert,
    downloadResult,
  }
}
