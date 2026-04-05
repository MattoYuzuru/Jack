import type { ViewerEditableMetadata } from './viewer-metadata'
import {
  canEmbedMetadata,
  exportViewerMetadata as exportViewerMetadataFromBackend,
  type ViewerMetadataExportResult,
} from './viewer-metadata-client'

export { canEmbedMetadata }
export type { ViewerMetadataExportResult }

export async function exportViewerMetadataDraft(
  file: File,
  metadata: ViewerEditableMetadata,
): Promise<ViewerMetadataExportResult> {
  return exportViewerMetadataFromBackend(file, metadata)
}

export { exportViewerMetadataDraft as exportViewerMetadata }
