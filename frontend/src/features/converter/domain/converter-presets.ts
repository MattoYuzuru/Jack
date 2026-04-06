import { getConverterCapabilityMatrix } from './converter-registry'

export type ConverterPresetId = 'original' | 'web-balanced' | 'email-attachment' | 'thumbnail'

export interface ConverterPresetDefinition {
  id: ConverterPresetId
  label: string
  detail: string
  statusLabel: string
  accents: string[]
  maxWidth: number | null
  maxHeight: number | null
  preferredQuality: number | null
  defaultBackgroundColor: string | null
  available: boolean
  availabilityDetail: string | null
}

export async function listConverterPresets(): Promise<ConverterPresetDefinition[]> {
  const matrix = await getConverterCapabilityMatrix()
  return matrix.presets as ConverterPresetDefinition[]
}

export function resolveConverterPresetFromDefinitions(
  presets: ConverterPresetDefinition[],
  presetId?: string | null,
): ConverterPresetDefinition {
  if (!presets.length) {
    return {
      id: 'original',
      label: 'Исходный размер',
      detail: 'Базовый профиль без изменения размеров и лишних ограничений.',
      statusLabel: 'Без уменьшения',
      accents: ['Исходное качество'],
      maxWidth: null,
      maxHeight: null,
      preferredQuality: null,
      defaultBackgroundColor: '#ffffff',
      available: true,
      availabilityDetail: null,
    }
  }

  if (!presetId) {
    return presets[0]!
  }

  return presets.find((preset) => preset.id === presetId) ?? presets[0]!
}

export async function resolveConverterPreset(
  presetId?: string | null,
): Promise<ConverterPresetDefinition> {
  const presets = await listConverterPresets()
  return resolveConverterPresetFromDefinitions(presets, presetId)
}
