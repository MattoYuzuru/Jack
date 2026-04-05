import { inspectViewerImageMetadata } from './viewer-metadata-client'
import {
  createEmptyMetadataPayload,
  type ViewerMetadataPayload,
} from './viewer-metadata'

export interface ViewerBinaryPreview {
  bytes: Uint8Array
  mimeType: string
  metadata: ViewerMetadataPayload
  previewLabel: string
}

export async function loadStructuredMetadata(
  file: File,
  reportProgress?: (message: string) => void,
): Promise<ViewerMetadataPayload> {
  try {
    return await inspectViewerImageMetadata(file, reportProgress)
  } catch {
    return createEmptyMetadataPayload()
  }
}
