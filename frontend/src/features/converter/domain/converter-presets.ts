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
}

const presetDefinitions: ConverterPresetDefinition[] = [
  {
    id: 'original',
    label: 'Original',
    detail: 'Не меняет размерность и оставляет runtime только target-specific encode-решения.',
    statusLabel: 'No resize',
    accents: ['Original size', 'Safe base'],
    maxWidth: null,
    maxHeight: null,
    preferredQuality: null,
    defaultBackgroundColor: '#ffffff',
  },
  {
    id: 'web-balanced',
    label: 'Web Balanced',
    detail:
      'Практичный пресет для веба: ограничивает большую графику, чтобы файл оставался управляемым без грубого даунскейла.',
    statusLabel: '2560 px cap',
    accents: ['Web', 'Balanced'],
    maxWidth: 2560,
    maxHeight: 2560,
    preferredQuality: 0.86,
    defaultBackgroundColor: '#ffffff',
  },
  {
    id: 'email-attachment',
    label: 'Email Attachment',
    detail:
      'Более агрессивный профиль для вложений и быстрой пересылки: уменьшает габариты и даёт чуть более компактный lossy encode.',
    statusLabel: '1600 px cap',
    accents: ['Email', 'Compact'],
    maxWidth: 1600,
    maxHeight: 1600,
    preferredQuality: 0.78,
    defaultBackgroundColor: '#fffaf0',
  },
  {
    id: 'thumbnail',
    label: 'Thumbnail',
    detail:
      'Миниатюрный профиль для быстрых превью, карточек и лёгких публикаций с заметным downscale.',
    statusLabel: '512 px cap',
    accents: ['Preview', 'Small'],
    maxWidth: 512,
    maxHeight: 512,
    preferredQuality: 0.72,
    defaultBackgroundColor: '#f3ede3',
  },
]

const presetById = new Map(presetDefinitions.map((preset) => [preset.id, preset]))

export function listConverterPresets(): ConverterPresetDefinition[] {
  return presetDefinitions
}

export function resolveConverterPreset(presetId?: string | null): ConverterPresetDefinition {
  if (!presetId) {
    return presetDefinitions[0]!
  }

  return presetById.get(presetId as ConverterPresetId) ?? presetDefinitions[0]!
}
