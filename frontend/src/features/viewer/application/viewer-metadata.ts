export interface ViewerMetadataItem {
  label: string
  value: string
}

export interface ViewerMetadataGroup {
  id: string
  label: string
  entries: ViewerMetadataItem[]
}

export interface ViewerEditableMetadata {
  description: string
  artist: string
  copyright: string
  capturedAt: string
}

export interface ViewerMetadataPayload {
  summary: ViewerMetadataItem[]
  groups: ViewerMetadataGroup[]
  editable: ViewerEditableMetadata
  thumbnailDataUrl: string | null
}

export function createEmptyEditableMetadata(): ViewerEditableMetadata {
  return {
    description: '',
    artist: '',
    copyright: '',
    capturedAt: '',
  }
}

export function createEmptyMetadataPayload(): ViewerMetadataPayload {
  return {
    summary: [],
    groups: [],
    editable: createEmptyEditableMetadata(),
    thumbnailDataUrl: null,
  }
}
