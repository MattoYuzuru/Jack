import type { ProcessingCapabilityScope } from '../processing-client'

export function createViewerCapabilityScopeFixture(): ProcessingCapabilityScope {
  return {
    scope: 'viewer',
    phase: 'viewer-backend-first',
    jobTypes: [
      { jobType: 'UPLOAD_INTAKE_ANALYSIS', implemented: true },
      { jobType: 'MEDIA_PREVIEW', implemented: true },
      { jobType: 'IMAGE_CONVERT', implemented: true },
      { jobType: 'DOCUMENT_PREVIEW', implemented: true },
      { jobType: 'METADATA_EXPORT', implemented: true },
      { jobType: 'VIEWER_RESOLVE', implemented: true },
    ],
    notes: [],
    viewerMatrix: {
      acceptAttribute:
        '.jpg,.jpeg,.png,.webp,.avif,.gif,.bmp,.svg,.ico,.heic,.heif,.tiff,.tif,.raw,.dng,.cr2,.cr3,.nef,.arw,.raf,.rw2,.orf,.pef,.srw,.pdf,.txt,.csv,.html,.htm,.rtf,.doc,.docx,.odt,.xls,.xlsx,.pptx,.epub,.db,.sqlite,.mp4,.mov,.webm,.avi,.mkv,.wmv,.flv,.mp3,.wav,.ogg,.opus,.aac,.flac,.aiff,.aif',
      formats: [
        viewerFormat('jpg', [], 'JPG', 'image', ['image/jpeg'], 'browser-native', 'native-image'),
        viewerFormat('jpeg', [], 'JPEG', 'image', ['image/jpeg'], 'browser-native', 'native-image'),
        viewerFormat('png', [], 'PNG', 'image', ['image/png'], 'browser-native', 'native-image'),
        viewerFormat('webp', [], 'WebP', 'image', ['image/webp'], 'browser-native', 'native-image'),
        viewerFormat('avif', [], 'AVIF', 'image', ['image/avif'], 'browser-native', 'native-image'),
        viewerFormat('gif', [], 'GIF', 'image', ['image/gif'], 'browser-native', 'native-image'),
        viewerFormat('bmp', [], 'BMP', 'image', ['image/bmp'], 'browser-native', 'native-image'),
        viewerFormat('svg', [], 'SVG', 'image', ['image/svg+xml'], 'browser-native', 'native-image'),
        viewerFormat('ico', [], 'ICO', 'image', ['image/x-icon'], 'browser-native', 'native-image'),
        viewerFormat('heic', ['heif'], 'HEIC', 'image', ['image/heic', 'image/heif'], 'server-assisted', 'server-viewer', ['VIEWER_RESOLVE', 'IMAGE_CONVERT', 'METADATA_EXPORT']),
        viewerFormat('tiff', ['tif'], 'TIFF', 'image', ['image/tiff'], 'server-assisted', 'server-viewer', ['VIEWER_RESOLVE', 'IMAGE_CONVERT', 'METADATA_EXPORT']),
        viewerFormat('raw', ['dng', 'cr2', 'cr3', 'nef', 'arw', 'raf', 'rw2', 'orf', 'pef', 'srw'], 'RAW', 'image', [], 'server-assisted', 'server-viewer', ['VIEWER_RESOLVE', 'IMAGE_CONVERT', 'METADATA_EXPORT']),
        viewerFormat('pdf', [], 'PDF', 'document', ['application/pdf'], 'server-assisted', 'server-viewer', ['VIEWER_RESOLVE', 'DOCUMENT_PREVIEW']),
        viewerFormat('txt', [], 'TXT', 'document', ['text/plain'], 'server-assisted', 'server-viewer', ['VIEWER_RESOLVE', 'DOCUMENT_PREVIEW']),
        viewerFormat('csv', [], 'CSV', 'document', ['text/csv'], 'server-assisted', 'server-viewer', ['VIEWER_RESOLVE', 'DOCUMENT_PREVIEW']),
        viewerFormat('html', ['htm'], 'HTML', 'document', ['text/html'], 'server-assisted', 'server-viewer', ['VIEWER_RESOLVE', 'DOCUMENT_PREVIEW']),
        viewerFormat('rtf', [], 'RTF', 'document', ['application/rtf'], 'server-assisted', 'server-viewer', ['VIEWER_RESOLVE', 'DOCUMENT_PREVIEW']),
        viewerFormat('doc', [], 'DOC', 'document', ['application/msword'], 'server-assisted', 'server-viewer', ['VIEWER_RESOLVE', 'DOCUMENT_PREVIEW']),
        viewerFormat('docx', [], 'DOCX', 'document', ['application/vnd.openxmlformats-officedocument.wordprocessingml.document'], 'server-assisted', 'server-viewer', ['VIEWER_RESOLVE', 'DOCUMENT_PREVIEW']),
        viewerFormat('odt', [], 'ODT', 'document', ['application/vnd.oasis.opendocument.text'], 'server-assisted', 'server-viewer', ['VIEWER_RESOLVE', 'DOCUMENT_PREVIEW']),
        viewerFormat('xls', [], 'XLS', 'document', ['application/vnd.ms-excel'], 'server-assisted', 'server-viewer', ['VIEWER_RESOLVE', 'DOCUMENT_PREVIEW']),
        viewerFormat('xlsx', [], 'XLSX', 'document', ['application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'], 'server-assisted', 'server-viewer', ['VIEWER_RESOLVE', 'DOCUMENT_PREVIEW']),
        viewerFormat('pptx', [], 'PPTX', 'document', ['application/vnd.openxmlformats-officedocument.presentationml.presentation'], 'server-assisted', 'server-viewer', ['VIEWER_RESOLVE', 'DOCUMENT_PREVIEW']),
        viewerFormat('epub', [], 'EPUB', 'document', ['application/epub+zip'], 'server-assisted', 'server-viewer', ['VIEWER_RESOLVE', 'DOCUMENT_PREVIEW']),
        viewerFormat('db', [], 'DB', 'document', ['application/octet-stream'], 'server-assisted', 'server-viewer', ['VIEWER_RESOLVE', 'DOCUMENT_PREVIEW']),
        viewerFormat('sqlite', [], 'SQLite', 'document', ['application/x-sqlite3'], 'server-assisted', 'server-viewer', ['VIEWER_RESOLVE', 'DOCUMENT_PREVIEW']),
        viewerFormat('mp4', [], 'MP4', 'media', ['video/mp4'], 'browser-native', 'native-video'),
        viewerFormat('mov', [], 'MOV', 'media', ['video/quicktime'], 'browser-native', 'native-video'),
        viewerFormat('webm', [], 'WebM', 'media', ['video/webm'], 'browser-native', 'native-video'),
        viewerFormat('avi', [], 'AVI', 'media', ['video/x-msvideo'], 'server-assisted', 'server-viewer', ['VIEWER_RESOLVE', 'MEDIA_PREVIEW']),
        viewerFormat('mkv', [], 'MKV', 'media', ['video/x-matroska'], 'server-assisted', 'server-viewer', ['VIEWER_RESOLVE', 'MEDIA_PREVIEW']),
        viewerFormat('wmv', [], 'WMV', 'media', ['video/x-ms-wmv'], 'server-assisted', 'server-viewer', ['VIEWER_RESOLVE', 'MEDIA_PREVIEW']),
        viewerFormat('flv', [], 'FLV', 'media', ['video/x-flv'], 'server-assisted', 'server-viewer', ['VIEWER_RESOLVE', 'MEDIA_PREVIEW']),
        viewerFormat('mp3', [], 'MP3', 'audio', ['audio/mpeg'], 'browser-native', 'native-audio'),
        viewerFormat('wav', [], 'WAV', 'audio', ['audio/wav'], 'browser-native', 'native-audio'),
        viewerFormat('ogg', [], 'OGG', 'audio', ['audio/ogg'], 'browser-native', 'native-audio'),
        viewerFormat('opus', [], 'OPUS', 'audio', ['audio/opus'], 'browser-native', 'native-audio'),
        viewerFormat('aac', [], 'AAC', 'audio', ['audio/aac'], 'server-assisted', 'server-viewer', ['VIEWER_RESOLVE', 'MEDIA_PREVIEW', 'METADATA_EXPORT']),
        viewerFormat('flac', [], 'FLAC', 'audio', ['audio/flac'], 'server-assisted', 'server-viewer', ['VIEWER_RESOLVE', 'MEDIA_PREVIEW', 'METADATA_EXPORT']),
        viewerFormat('aiff', ['aif'], 'AIFF', 'audio', ['audio/aiff'], 'server-assisted', 'server-viewer', ['VIEWER_RESOLVE', 'MEDIA_PREVIEW', 'METADATA_EXPORT']),
      ],
    },
    converterMatrix: null,
  }
}

export function createConverterCapabilityScopeFixture(): ProcessingCapabilityScope {
  return {
    scope: 'converter',
    phase: 'converter-backend-first',
    jobTypes: [
      { jobType: 'UPLOAD_INTAKE_ANALYSIS', implemented: true },
      { jobType: 'IMAGE_CONVERT', implemented: true },
      { jobType: 'MEDIA_PREVIEW', implemented: true },
      { jobType: 'DOCUMENT_PREVIEW', implemented: true },
      { jobType: 'METADATA_EXPORT', implemented: true },
    ],
    notes: [],
    viewerMatrix: null,
    converterMatrix: {
      acceptAttribute:
        '.jpg,.jpeg,.png,.webp,.bmp,.svg,.psd,.ai,.eps,.ps,.heic,.heif,.tiff,.tif,.raw,.dng,.cr2,.cr3,.nef,.arw,.raf,.rw2,.orf,.pef,.srw',
      sourceFormats: [
        converterSource('jpg', ['jpeg'], 'JPG', 'native-raster', ['image/jpeg'], ['IMAGE_CONVERT']),
        converterSource('png', [], 'PNG', 'native-raster', ['image/png'], ['IMAGE_CONVERT']),
        converterSource('webp', [], 'WebP', 'native-raster', ['image/webp'], ['IMAGE_CONVERT']),
        converterSource('bmp', [], 'BMP', 'native-raster', ['image/bmp'], ['IMAGE_CONVERT']),
        converterSource('svg', [], 'SVG', 'native-raster', ['image/svg+xml'], ['IMAGE_CONVERT']),
        converterSource('psd', [], 'PSD', 'psd-raster', [], ['IMAGE_CONVERT']),
        converterSource('ai', [], 'AI', 'illustration-raster', [], ['IMAGE_CONVERT']),
        converterSource('eps', ['ps'], 'EPS', 'illustration-raster', [], ['IMAGE_CONVERT']),
        converterSource('heic', ['heif'], 'HEIC', 'heic-raster', ['image/heic', 'image/heif'], ['IMAGE_CONVERT']),
        converterSource('tiff', ['tif'], 'TIFF', 'tiff-raster', ['image/tiff'], ['IMAGE_CONVERT']),
        converterSource('raw', ['dng', 'cr2', 'cr3', 'nef', 'arw', 'raf', 'rw2', 'orf', 'pef', 'srw'], 'RAW', 'raw-raster', [], ['IMAGE_CONVERT']),
      ],
      targetFormats: [
        converterTarget('jpg', 'JPG', 'image', 'image/jpeg', 'jpeg-encoder', true, false, 0.9, ['IMAGE_CONVERT']),
        converterTarget('png', 'PNG', 'image', 'image/png', 'png-encoder', false, true, null, ['IMAGE_CONVERT']),
        converterTarget('webp', 'WebP', 'image', 'image/webp', 'webp-encoder', true, true, 0.9, ['IMAGE_CONVERT']),
        converterTarget('avif', 'AVIF', 'image', 'image/avif', 'avif-encoder', true, true, 0.78, ['IMAGE_CONVERT']),
        converterTarget('svg', 'SVG', 'image', 'image/svg+xml', 'svg-vectorizer', false, true, null, ['IMAGE_CONVERT']),
        converterTarget('ico', 'ICO', 'image', 'image/x-icon', 'ico-image', false, true, null, ['IMAGE_CONVERT']),
        converterTarget('pdf', 'PDF', 'document', 'application/pdf', 'pdf-document', true, false, 0.92, ['IMAGE_CONVERT']),
        converterTarget('tiff', 'TIFF', 'image', 'image/tiff', 'tiff-image', false, true, null, ['IMAGE_CONVERT']),
      ],
      scenarios: [
        converterScenario('heic', 'jpg', 'image', 'server-assisted'),
        converterScenario('heic', 'avif', 'image', 'server-assisted'),
        converterScenario('heic', 'tiff', 'image', 'server-assisted'),
        converterScenario('png', 'jpg', 'image', 'server-assisted'),
        converterScenario('png', 'webp', 'image', 'server-assisted'),
        converterScenario('png', 'avif', 'image', 'server-assisted'),
        converterScenario('png', 'svg', 'image', 'server-assisted'),
        converterScenario('png', 'ico', 'image', 'server-assisted'),
        converterScenario('png', 'tiff', 'image', 'server-assisted'),
        converterScenario('jpg', 'png', 'image', 'server-assisted'),
        converterScenario('jpg', 'webp', 'image', 'server-assisted'),
        converterScenario('jpg', 'avif', 'image', 'server-assisted'),
        converterScenario('jpg', 'tiff', 'image', 'server-assisted'),
        converterScenario('webp', 'jpg', 'image', 'server-assisted'),
        converterScenario('webp', 'png', 'image', 'server-assisted'),
        converterScenario('webp', 'tiff', 'image', 'server-assisted'),
        converterScenario('bmp', 'jpg', 'image', 'server-assisted'),
        converterScenario('bmp', 'png', 'image', 'server-assisted'),
        converterScenario('bmp', 'tiff', 'image', 'server-assisted'),
        converterScenario('psd', 'jpg', 'image', 'server-assisted'),
        converterScenario('psd', 'png', 'image', 'server-assisted'),
        converterScenario('psd', 'webp', 'image', 'server-assisted'),
        converterScenario('tiff', 'jpg', 'image', 'server-assisted'),
        converterScenario('tiff', 'pdf', 'document', 'server-assisted'),
        converterScenario('tiff', 'tiff', 'image', 'server-assisted'),
        converterScenario('raw', 'jpg', 'image', 'server-assisted'),
        converterScenario('raw', 'pdf', 'document', 'server-assisted'),
        converterScenario('raw', 'tiff', 'image', 'server-assisted'),
        converterScenario('jpg', 'pdf', 'document', 'server-assisted'),
        converterScenario('png', 'pdf', 'document', 'server-assisted'),
        converterScenario('webp', 'pdf', 'document', 'server-assisted'),
        converterScenario('bmp', 'pdf', 'document', 'server-assisted'),
        converterScenario('heic', 'pdf', 'document', 'server-assisted'),
        converterScenario('svg', 'png', 'image', 'server-assisted'),
        converterScenario('svg', 'ico', 'image', 'server-assisted'),
        converterScenario('svg', 'tiff', 'image', 'server-assisted'),
        converterScenario('svg', 'pdf', 'document', 'server-assisted'),
        converterScenario('ai', 'png', 'image', 'server-assisted'),
        converterScenario('ai', 'pdf', 'document', 'server-assisted'),
        converterScenario('eps', 'png', 'image', 'server-assisted'),
        converterScenario('eps', 'pdf', 'document', 'server-assisted'),
      ],
      presets: [
        converterPreset('original', 'Original', null, null, null, '#ffffff'),
        converterPreset('web-balanced', 'Web Balanced', 2560, 2560, 0.86, '#ffffff'),
        converterPreset('email-attachment', 'Email Attachment', 1600, 1600, 0.78, '#fffaf0'),
        converterPreset('thumbnail', 'Thumbnail', 512, 512, 0.72, '#f3ede3'),
      ],
    },
  }
}

export function createPlatformCapabilityScopeFixture(): ProcessingCapabilityScope {
  return {
    scope: 'platform',
    phase: 'processing-platform',
    jobTypes: [
      { jobType: 'UPLOAD_INTAKE_ANALYSIS', implemented: true },
      { jobType: 'MEDIA_PREVIEW', implemented: true },
      { jobType: 'IMAGE_CONVERT', implemented: true },
      { jobType: 'DOCUMENT_PREVIEW', implemented: true },
      { jobType: 'METADATA_EXPORT', implemented: true },
      { jobType: 'VIEWER_RESOLVE', implemented: true },
    ],
    notes: [],
    viewerMatrix: null,
    converterMatrix: null,
    platformMatrix: {
      modules: [
        platformModule('compression', 'Compression', ['Target size', 'Quality', 'Batch'], ['IMAGE_CONVERT', 'MEDIA_PREVIEW']),
        platformModule('pdf-toolkit', 'PDF Toolkit', ['Merge', 'Rotate', 'Protect'], ['DOCUMENT_PREVIEW', 'IMAGE_CONVERT', 'VIEWER_RESOLVE']),
        platformModule('multi-format-editor', 'Multi-Format Editor', ['Preview', 'Validate', 'Export'], ['DOCUMENT_PREVIEW', 'METADATA_EXPORT']),
        platformModule('batch-conversion', 'Batch Conversion', ['Queue', 'Reuse', 'Artifacts'], ['IMAGE_CONVERT']),
        platformModule('ocr', 'OCR', ['Extract', 'Search', 'Scan'], ['DOCUMENT_PREVIEW', 'IMAGE_CONVERT']),
        platformModule('office-pdf-conversion', 'Office/PDF Conversion', ['DOCX', 'PDF', 'Office'], ['DOCUMENT_PREVIEW', 'VIEWER_RESOLVE']),
      ],
    },
  }
}

function viewerFormat(
  extension: string,
  aliases: string[],
  label: string,
  family: string,
  mimeTypes: string[],
  previewPipeline: string,
  previewStrategyId: string,
  requiredJobTypes: string[] = [],
) {
  return {
    extension,
    aliases,
    label,
    family,
    mimeTypes,
    previewPipeline,
    previewStrategyId,
    statusLabel: previewPipeline === 'browser-native' ? 'Browser preview' : 'Server preview',
    notes: `${label} capability fixture`,
    accents: [label],
    available: true,
    availabilityDetail: null,
    requiredJobTypes,
  }
}

function converterSource(
  extension: string,
  aliases: string[],
  label: string,
  sourceStrategyId: string,
  mimeTypes: string[],
  requiredJobTypes: string[] = [],
) {
  return {
    extension,
    aliases,
    label,
    family: 'image',
    mimeTypes,
    sourceStrategyId,
    statusLabel: requiredJobTypes.length ? 'Backend intake' : 'Browser raster',
    notes: `${label} source fixture`,
    accents: [label],
    available: true,
    availabilityDetail: null,
    requiredJobTypes,
  }
}

function converterTarget(
  extension: string,
  label: string,
  family: string,
  mimeType: string,
  targetStrategyId: string,
  supportsQuality: boolean,
  supportsTransparency: boolean,
  defaultQuality: number | null,
  requiredJobTypes: string[] = [],
) {
  return {
    extension,
    label,
    family,
    mimeType,
    targetStrategyId,
    supportsQuality,
    supportsTransparency,
    defaultQuality,
    statusLabel: requiredJobTypes.length ? 'Backend encode' : 'Browser-native',
    notes: `${label} target fixture`,
    accents: [label],
    available: true,
    availabilityDetail: null,
    requiredJobTypes,
  }
}

function converterScenario(
  sourceExtension: string,
  targetExtension: string,
  family = 'image',
  executionMode: 'browser-native' | 'server-assisted' = 'server-assisted',
) {
  return {
    id: `${sourceExtension}->${targetExtension}`,
    family,
    label: `${sourceExtension.toUpperCase()} -> ${targetExtension.toUpperCase()}`,
    sourceExtension,
    targetExtension,
    statusLabel: executionMode === 'server-assisted' ? 'Server-assisted' : 'Browser-native',
    notes: `${sourceExtension} -> ${targetExtension} scenario fixture`,
    accents: [sourceExtension.toUpperCase(), targetExtension.toUpperCase()],
    executionMode,
    available: true,
    availabilityDetail: null,
    requiredJobTypes: executionMode === 'server-assisted' ? ['IMAGE_CONVERT'] : [],
  }
}

function converterPreset(
  id: string,
  label: string,
  maxWidth: number | null,
  maxHeight: number | null,
  preferredQuality: number | null,
  defaultBackgroundColor: string,
) {
  return {
    id,
    label,
    detail: `${label} preset fixture`,
    statusLabel: label,
    accents: [label],
    maxWidth,
    maxHeight,
    preferredQuality,
    defaultBackgroundColor,
    available: true,
    availabilityDetail: null,
  }
}

function platformModule(
  id: string,
  label: string,
  accents: string[],
  reusedJobTypes: string[],
) {
  return {
    id,
    label,
    summary: `${label} platform fixture`,
    detail: `${label} detail fixture`,
    statusLabel: 'Foundation ready',
    accents,
    reusedDomains: ['artifact-storage', 'job-orchestration'],
    reusedJobTypes,
    nextSlices: ['slice-1', 'slice-2'],
    foundationReady: true,
    availabilityDetail: null,
  }
}
