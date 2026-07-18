# Capability И Format Matrix

Дата актуализации: 17 июля 2026 года.

Source of truth — backend endpoints `GET /api/capabilities/{viewer,converter,compression,
pdf-toolkit,editor,platform}`. Frontend registry гидратируется этими payload и не должен
объявлять `available`, если required job/native runtime недоступен. Этот документ описывает
product profile и fidelity; полный список aliases/MIME/presets нужно читать из API.

## Viewer

| Profile      | Formats                                              | Execution / fidelity                                                                                    |
| ------------ | ---------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| Native image | JPG/JPEG, PNG, WebP, AVIF, GIF, BMP, ICO             | Browser viewport; backend всё равно владеет file/security policy для server operations                  |
| Server image | SVG, HEIC/HEIF, TIFF/TIF, RAW family                 | Safe intake; SVG active/external content rejected и preview rasterized; decoded-pixel limit             |
| Text/config  | TXT, Markdown, JSON, YAML/YML, XML, `.env`, LOG, SQL | Bounded server document preview, outline/search/quick-edit copy; Markdown profile `jack-markdown-1.1.0` |
| Tables       | CSV, TSV                                             | RFC-aware cursor/offset pages; delimiter/header metadata; max page и 200-row DOM window                 |
| Workbooks    | XLS, XLSX/XLSM, ODS                                  | Lazy sheet rectangles; formulas/macros/external connections не исполняются; limited style fidelity      |
| Database     | DB, SQLite                                           | Isolated read-only table ranges, cursor/offset; arbitrary SQL/ATTACH/PRAGMA/write disabled              |
| Narrative    | PDF, RTF, DOC/DOCX, ODT, PPTX, EPUB, sanitized HTML  | Bounded pages/entries; text/outline-first fidelity; EPUB/HTML sanitized with CSP                        |
| Native media | MP4, MOV, WebM; MP3, WAV, OGG, OPUS                  | Browser playback/viewport controls                                                                      |
| Server media | AVI, MKV, WMV, FLV; AAC, FLAC, AIFF/AIF              | `VIEWER_RESOLVE` + `MEDIA_PREVIEW`; bounded probe/process/output and normalized artifact                |

## Converter И Compression

Converter sources: `jpg, png, webp, bmp, svg, psd, ai, eps, heic, tiff, raw, pdf, doc, docx,
rtf, odt, csv, xlsx, ods, pptx, mp4, mov, mkv, avi, webm, wav, flac, mp3, m4a`.

Converter targets: `jpg, png, webp, avif, svg, ico, pdf, tiff, docx, txt, html, rtf, odt,
xlsx, csv, ods, pptx, mp4, webm, gif, mp3, wav, aac, m4a, flac`.

Compression sources: `jpg, png, webp, bmp, svg, psd, ai, eps, heic, tiff, raw, mp4, mov, mkv,
avi, webm, wav, flac, mp3, m4a, aac`; targets: `jpg, png, webp, avif, tiff, mp4, webm, m4a,
mp3, aac, flac, wav`.

- Conversion is format-first; compression is size-first and supports `maximum`, best-effort
  `target-size` and `custom` modes.
- Retry clones an immutable source request; timeout/cancel terminates the durable job and native
  process tree. Output is probed/decoded again before it becomes an artifact.
- PDF → DOCX is text-flow, CSV is flattened, office layouts/charts/macros may lose fidelity.
  Scanned documents do not silently promise OCR unless the explicit OCR capability is selected.
- Target-size may return the smallest valid candidate above budget with an explicit warning; it
  never labels an oversized result as successful fit.

## PDF Toolkit

Direct source: PDF. Import path additionally accepts TIFF/RAW/JPG/PNG/WebP/BMP/HEIC/SVG/AI/EPS
and DOCX/XLSX/PPTX through existing image/office conversion jobs.

Operations: merge, split, rotate, reorder, OCR, Visible stamp (`sign` API id), redact, protect and
unlock.

- `sign` is deliberately labelled Visible stamp and is not certificate-based signing.
- Redaction reconstructs a raster PDF; postconditions reject original terms, annotations,
  attachments and metadata. Selectable/vector content is intentionally lost.
- Encrypted input requires the correct password; corrupt/encrypted/box/rotation/page-range cases
  are negative fixtures. OCR availability additionally depends on installed Tesseract language
  data (`eng` default, `rus` in the production image).

## Editor И Dev Tools

Editor: Markdown, HTML, CSS, JavaScript, JSON, YAML and TXT. CodeMirror, format-aware commands and
local interaction stay client-side; validate/export and artifacts use `EDITOR_PROCESS`. HTML/CSS
preview cannot load arbitrary remote resources; Markdown uses the shared sanitized backend
contract.

Dev Tools text utilities remain browser-native. File hashing is streaming/chunked and bounded;
HMAC/Basic/JWT secret-like fields are volatile and are not persisted. Metadata file mutation and
export remains a backend `METADATA_EXPORT` concern.

## Executable Coverage

- Backend capability catalog integration tests ensure advertised entries are backed by available
  job types/native prerequisites.
- Frontend capability fixtures verify the server payload shape and registry precedence; generic
  MIME cannot override a more specific extension.
- Format suites cover tiny/representative/large-but-CI-safe/malformed/hostile tiers for intake,
  table/workbook/SQLite, documents, media, conversion, compression and PDF operations.
- `available=false` with `availabilityDetail` is the required fallback. UI must not imitate an
  unsupported conversion or parse a large file locally to bypass it.
