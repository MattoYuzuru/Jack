import JSZip from 'jszip'

export interface ViewerOoxmlPackage {
  readText(path: string): Promise<string | null>
  readXml(path: string): Promise<XMLDocument | null>
}

export async function loadViewerOoxmlPackage(file: File): Promise<ViewerOoxmlPackage> {
  const zip = await JSZip.loadAsync(await file.arrayBuffer())
  const readText = async (path: string): Promise<string | null> => {
    const entry = zip.file(normalizeOoxmlPath(path))
    return entry ? entry.async('text') : null
  }

  return {
    readText,
    async readXml(path) {
      const content = await readText(path)
      if (!content) {
        return null
      }

      return parseXmlDocument(content)
    },
  }
}

export function readOoxmlRelationships(documentRoot: XMLDocument): Map<string, string> {
  const relationMap = new Map<string, string>()
  const relationships = documentRoot.getElementsByTagNameNS('*', 'Relationship')

  for (const relationship of Array.from(relationships)) {
    const id = readXmlAttribute(relationship, 'Id')
    const target = readXmlAttribute(relationship, 'Target')

    if (id && target) {
      relationMap.set(id, target)
    }
  }

  return relationMap
}

export function resolveOoxmlPath(basePath: string, targetPath: string): string {
  const normalizedTarget = normalizeOoxmlPath(targetPath)

  if (!normalizedTarget.startsWith('.')) {
    if (!normalizedTarget.includes('../')) {
      const baseDirectory = basePath.includes('/') ? basePath.slice(0, basePath.lastIndexOf('/') + 1) : ''
      return normalizeOoxmlPath(`${baseDirectory}${normalizedTarget}`)
    }
  }

  const baseSegments = basePath.split('/').slice(0, -1)
  const targetSegments = normalizedTarget.split('/')

  for (const segment of targetSegments) {
    if (!segment || segment === '.') {
      continue
    }

    if (segment === '..') {
      baseSegments.pop()
      continue
    }

    baseSegments.push(segment)
  }

  return baseSegments.join('/')
}

export function readXmlAttribute(node: Element, attributeName: string): string | null {
  for (const attribute of Array.from(node.attributes)) {
    if (
      attribute.name === attributeName ||
      attribute.localName === attributeName.toLowerCase() ||
      attribute.localName === attributeName
    ) {
      return attribute.value
    }
  }

  return null
}

export function findXmlChildren(node: ParentNode, localName: string): Element[] {
  return Array.from(node.children).filter((child) => child.localName === localName)
}

export function findFirstXmlChild(node: ParentNode, localName: string): Element | null {
  return findXmlChildren(node, localName)[0] ?? null
}

export function findFirstXmlDescendant(node: ParentNode, localName: string): Element | null {
  return (node as Element | Document).getElementsByTagNameNS('*', localName)[0] ?? null
}

export function findXmlDescendants(node: ParentNode, localName: string): Element[] {
  return Array.from((node as Element | Document).getElementsByTagNameNS('*', localName))
}

export function readXmlText(node: ParentNode, localName: string): string {
  return findXmlDescendants(node, localName)
    .map((element) => element.textContent?.trim() ?? '')
    .filter(Boolean)
    .join(' ')
    .trim()
}

export function escapeHtml(value: string): string {
  return value
    .replace(/&/gu, '&amp;')
    .replace(/</gu, '&lt;')
    .replace(/>/gu, '&gt;')
    .replace(/"/gu, '&quot;')
}

export function wrapViewerDocumentHtml(body: string): string {
  return [
    '<!doctype html>',
    '<html lang="en">',
    '<head>',
    '<meta charset="utf-8" />',
    '<style>',
    'html,body{margin:0;padding:0;background:#fffaf1;color:#102426;font-family:Manrope,Segoe UI,sans-serif;line-height:1.65;}',
    'body{padding:24px;}',
    'h1,h2,h3,h4,h5,h6{font-family:"Space Grotesk",Manrope,sans-serif;line-height:1.05;color:#102426;}',
    'p,li{margin:0 0 14px;}',
    'table{width:100%;border-collapse:collapse;margin:18px 0;}',
    'td,th{border:1px solid rgba(16,36,38,.14);padding:8px 10px;text-align:left;vertical-align:top;}',
    '.docx-list{padding-left:20px;}',
    '.docx-list li{margin-bottom:8px;}',
    '.slide-card{margin:0 0 18px;padding:18px;border-radius:18px;background:rgba(245,238,228,.88);border:1px solid rgba(16,36,38,.08);}',
    '.slide-card h2{margin:0 0 12px;}',
    '.sheet-chip{display:inline-block;margin:0 10px 10px 0;padding:8px 12px;border-radius:999px;background:rgba(29,92,85,.08);}',
    '</style>',
    '</head>',
    `<body>${body}</body>`,
    '</html>',
  ].join('')
}

function parseXmlDocument(content: string): XMLDocument {
  return new DOMParser().parseFromString(content, 'application/xml')
}

function normalizeOoxmlPath(path: string): string {
  return path.replace(/^\/+/u, '').replace(/\\/gu, '/')
}
