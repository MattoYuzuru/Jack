import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import {
  createConverterRuntime,
  type ConverterPreparedSource,
  type ConverterResult,
} from '../application/converter-runtime'
import { type ConverterTargetFormatDefinition } from '../domain/converter-registry'

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
  const selectedTargetExtension = ref('')
  const quality = ref(0.9)
  const backgroundColor = ref('#fffaf0')

  const availableTargets = computed(() => prepared.value?.targets ?? [])
  const activeTarget = computed<ConverterTargetFormatDefinition | null>(
    () =>
      availableTargets.value.find((target) => target.extension === selectedTargetExtension.value) ??
      null,
  )

  watch(activeTarget, (target) => {
    if (!target) {
      return
    }

    quality.value = target.defaultQuality ?? quality.value
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
    isLoading.value = true

    try {
      const inspected = converterRuntime.inspect(file)

      if (!inspected || !inspected.targets.length) {
        throw new Error(
          'Для выбранного файла пока нет зарегистрированного browser-first сценария конвертации.',
        )
      }

      prepared.value = inspected
      selectedTargetExtension.value = inspected.targets[0]?.extension ?? ''
    } catch (error) {
      errorMessage.value =
        error instanceof Error
          ? error.message
          : 'Не удалось подготовить конвертер к выбранному файлу.'
    } finally {
      isLoading.value = false
    }
  }

  function clearSelection() {
    releaseResult()
    prepared.value = null
    result.value = null
    errorMessage.value = ''
    isLoading.value = false
    isConverting.value = false
    selectedTargetExtension.value = ''
  }

  async function convert() {
    if (!prepared.value || !selectedTargetExtension.value) {
      return
    }

    releaseResult()
    result.value = null
    errorMessage.value = ''
    isConverting.value = true

    try {
      const converted = await converterRuntime.convert({
        prepared: prepared.value,
        targetExtension: selectedTargetExtension.value,
        quality: quality.value,
        backgroundColor: backgroundColor.value,
      })

      result.value = {
        ...converted,
        objectUrl: URL.createObjectURL(converted.blob),
      }
    } catch (error) {
      errorMessage.value =
        error instanceof Error ? error.message : 'Не удалось выполнить конвертацию.'
    } finally {
      isConverting.value = false
    }
  }

  function downloadResult() {
    if (!result.value) {
      return
    }

    const anchor = document.createElement('a')
    anchor.href = result.value.objectUrl
    anchor.download = result.value.fileName
    anchor.click()
  }

  onBeforeUnmount(() => {
    releaseResult()
  })

  return {
    prepared,
    result,
    availableTargets,
    activeTarget,
    isLoading,
    isConverting,
    errorMessage,
    selectedTargetExtension,
    quality,
    backgroundColor,
    selectFile,
    clearSelection,
    convert,
    downloadResult,
  }
}
