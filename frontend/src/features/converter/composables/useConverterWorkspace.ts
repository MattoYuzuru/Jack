import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import {
  createConverterRuntime,
  type ConverterPreparedSource,
  type ConverterResult,
} from '../application/converter-runtime'
import {
  listConverterPresets,
  resolveConverterPreset,
  type ConverterPresetDefinition,
} from '../domain/converter-presets'
import { type ConverterTargetFormatDefinition } from '../domain/converter-registry'

interface ConverterResultViewModel extends ConverterResult {
  objectUrl: string
}

const converterRuntime = createConverterRuntime()
const converterPresets = listConverterPresets()

export function useConverterWorkspace() {
  const prepared = shallowRef<ConverterPreparedSource | null>(null)
  const result = shallowRef<ConverterResultViewModel | null>(null)
  const isLoading = ref(false)
  const isConverting = ref(false)
  const errorMessage = ref('')
  const processingMessage = ref('')
  const selectedTargetExtension = ref('')
  const selectedPresetId = ref<ConverterPresetDefinition['id']>('original')
  const quality = ref(0.9)
  const backgroundColor = ref('#fffaf0')

  const availablePresets = converterPresets
  const availableTargets = computed(() => prepared.value?.targets ?? [])
  const activeTarget = computed<ConverterTargetFormatDefinition | null>(
    () =>
      availableTargets.value.find((target) => target.extension === selectedTargetExtension.value) ??
      null,
  )
  const activePreset = computed(() => resolveConverterPreset(selectedPresetId.value))

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
      const inspected = converterRuntime.inspect(file)

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
    selectFile,
    clearSelection,
    convert,
    downloadResult,
  }
}
