# Processing Platform

## Зачем Это Нужно

Jack больше не строится как продукт, где весь тяжёлый runtime живёт в браузере.
Backend теперь является processing-platform, которая:

- принимает исходные файлы
- оркестрирует job lifecycle
- собирает preview/result artifacts
- держит capability matrix как источник правды
- отдаёт frontend уже продуктовые payload-контракты

Идея простая: frontend остаётся быстрым interaction/UI слоем, а backend забирает вычисления,
валидацию, sanitzation, format intelligence и long-running processing.

## Что Уже Работает

### Foundation

- `POST /api/uploads`
- `GET /api/uploads/{id}`
- `GET /api/uploads/{id}/table-range?cursor=&offset=&limit=&delimiter=&headerMode=`
- `GET /api/uploads/{id}/workbook-range?sheetIndex=&startRow=&startColumn=&rows=&columns=`
- `GET /api/uploads/{id}/database-range?table=&cursor=&offset=&limit=`
- `POST /api/jobs`
- `GET /api/jobs/{id}`
- `DELETE /api/jobs/{id}`
- `GET /api/jobs/{id}/artifacts/{artifactId}`
- `GET /api/capabilities/viewer`
- `GET /api/capabilities/converter`
- `GET /api/capabilities/compression`
- `GET /api/capabilities/pdf-toolkit`
- `GET /api/capabilities/editor`
- `GET /api/capabilities/platform`
- `GET /actuator/health`, `/liveness`, `/readiness` (metrics не exposed externally)

### Активные Job Type

- `UPLOAD_INTAKE_ANALYSIS`
- `MEDIA_PREVIEW`
- `MEDIA_CONVERT`
- `IMAGE_CONVERT`
- `FILE_COMPRESS`
- `PDF_TOOLKIT`
- `OFFICE_CONVERT`
- `DOCUMENT_PREVIEW`
- `METADATA_EXPORT`
- `VIEWER_RESOLVE`
- `EDITOR_PROCESS`

### Processing Domains

- media processing через `ffprobe` / `ffmpeg`
- heavy imaging через `ImageMagick`, `Ghostscript`, `potrace`, `libraw`
- dedicated compression orchestration для image/video/audio size-first flows
- dedicated PDF toolkit orchestration для merge/split/rotate/reorder, OCR, redaction и protection flows
- dedicated editor diagnostics/export orchestration для markdown/html/css/javascript/json/yaml/txt drafts
- document intelligence для PDF / office / archive / SQLite preview
- office/pdf conversion для narrative docs, spreadsheet exports, slide decks и slideshow media outputs
- metadata inspect/export для image/audio flows
- unified viewer resolve route
- owner-bound durable metadata в PostgreSQL с Flyway migrations
- signed anonymous session cookie, owner predicates для upload/job/artifact и CSRF intent header
- bounded global queue, per-session concurrency/storage quota и public request rate limit
- TTL cleanup policy для upload / artifact / job storage с durable state и physical file cleanup
- internal job-state/cleanup metrics без внешней actuator exposure
- server-owned capability matrix для viewer, converter, compression, pdf-toolkit, editor и future modules
- browser-native dev-tools route для instant text/url engineering utilities вне queued processing path

## Разделение Ответственности

### Backend

Backend должен владеть тем, что реально является продуктовой и файловой логикой:

- file intake и hash-based identity
- preview / convert / extract / sanitize / inspect pipelines
- metadata read/write policy
- format/scenario/preset capability rules
- job status, progress, retry/cancel contract
- artifact storage и повторное скачивание результата

### Frontend

Frontend должен оставаться thin, но удобным слоем:

- workspace state
- local interaction и form UX
- browser-native rendering там, где он уже достаточен
- zoom / rotate / playback / subtitle / search / copy / download UX
- progress presentation поверх backend job contract
- и отдельные мгновенные dev utilities там, где server orchestration ничего не даёт кроме лишней латентности

## Активные Маршруты

### Viewer

Viewer теперь использует backend-first contract для всех server-assisted форматов:

- legacy video/audio контейнеры идут через `VIEWER_RESOLVE`
- HEIC / TIFF / RAW идут через `VIEWER_RESOLVE`
- document stack идёт через `VIEWER_RESOLVE`
- `markdown/json/yaml/xml/.env/tsv/log/sql` идут в тот же document intelligence слой
- metadata inspect/export переиспользует `METADATA_EXPORT`
- CSV/TSV получает paged row ranges; XLS/XLSX/XLSM/ODS — lazy sheet rectangles; SQLite — только
  read-only table ranges без arbitrary SQL

На клиенте остались native rendering, state и interaction tooling, а для тексто-ориентированных
document layouts появился quick-edit handoff в editor без мутации исходного файла.

Typed image/document/data/video/audio renderers не владеют скрытым transport. Root session
остаётся единственной точкой revision/abort/object-URL cleanup, а data renderer удерживает не
более 200 строк в DOM.

### Converter

Converter работает как backend-first route:

- supported image сценарии уходят в `IMAGE_CONVERT`
- supported office/pdf сценарии уходят в `OFFICE_CONVERT`
- supported video/audio delivery и transcode сценарии уходят в `MEDIA_CONVERT`
- frontend держит progress, retry, cancel и artifact reuse
- capability/source-target/preset matrix приходит с backend
- media controls на фронте теперь только задают container target, codec, bitrate, resolution и FPS, а итоговый artifact и warnings собираются server-side

### Compression

Compression теперь тоже работает как отдельный backend-first route:

- image scenarios уходят в `FILE_COMPRESS`, который внутри reuse'ит `IMAGE_CONVERT` как candidate builder
- video/audio scenarios уходят в `FILE_COMPRESS`, который внутри reuse'ит `MEDIA_CONVERT`
- frontend держит mode selection, target-size input, custom limit controls и result visualization
- backend возвращает единый compression manifest с source/result facts, warnings и attempt ladder
- compression больше не смешивается с converter: задача route теперь size-first, а не format-first

### PDF Toolkit

PDF toolkit теперь тоже работает как отдельный backend-first route:

- прямой PDF intake идёт в `PDF_TOOLKIT` и reuse'ит `VIEWER_RESOLVE` для preview/stats/search layer
- совместимые image/office sources сначала идут в `IMAGE_CONVERT` или `OFFICE_CONVERT` с target `pdf`, а потом переводятся в тот же page-aware workspace
- `PDF_TOOLKIT` закрывает merge/split/rotate/reorder, OCR searchable export, visible signature stamps, term redaction и password protect/unlock
- frontend держит только workspace state, page/range forms, result history и download/load-as-current UX
- backend возвращает единый PDF toolkit manifest, preview artifact, result artifact и при необходимости OCR text export

### Editor

Editor теперь тоже работает как отдельный backend-first route:

- frontend держит split-view, templates, snippets, formatting, local draft persistence и live preview
- backend `EDITOR_PROCESS` собирает diagnostics, outline и ready/plain-text export artifacts
- `DOCUMENT_PREVIEW` reuse'ится для html/plain-text outline и text contract там, где это уже готово
- validate/export больше не живут как ad-hoc local blob flow: они идут через тот же upload/job/artifact lifecycle

### Dev Tools

Dev tools в текущем срезе сознательно остаётся browser-native route:

- encoding/decoding, JWT inspect, hashes, URL cleanup, validators и quick helpers считаются локально
- browser хранит persisted state, copy/download UX и быстрые form transitions
- route не использует upload/job/artifact foundation, потому что эти операции не строят server-owned artifacts и не выигрывают от queue/retry
- это не отменяет backend-first правило для file/business logic; это отдельный класс мгновенных инженерных утилит

## Platform Reuse Для Следующих Модулей

`GET /api/capabilities/platform` описывает, как следующие roadmap-модули должны стартовать
не с нуля, а поверх уже существующей processing-platform:

- `Batch Conversion`
  - reuse: upload/job/artifact foundation и `IMAGE_CONVERT`
- `OCR`
  - reuse: `DOCUMENT_PREVIEW`, `IMAGE_CONVERT`, общий job/artifact flow
- `Office/PDF Conversion`
  - reuse: `OFFICE_CONVERT`, `DOCUMENT_PREVIEW`, `VIEWER_RESOLVE`, capability-driven routing

## Осознанные Ограничения Текущего Среза

- `PDF -> DOCX` остаётся text-flow export и может терять сложную вёрстку, positioned blocks и колонки
- scanned `PDF -> DOCX/TXT/XLSX/CSV/PPTX` пока не делает OCR автоматически и должен явно вести в OCR-слой следующей итерации
- `CSV` остаётся flattened single-sheet export без formulas, styles и comments
- media conversion теперь явно разделяет контейнер, codec, bitrate, resolution и FPS, чтобы delivery-ограничения не маскировались одним target-форматом
- compression target-size route остаётся best-effort: если текущий ladder не может честно уложить файл в budget, backend возвращает самый компактный найденный artifact и явно помечает это в manifest
- PDF toolkit `e-sign` в текущем срезе является видимым stamp-mark, а не certificate-based digital signature
- PDF toolkit redaction сознательно пересобирает страницы как raster PDF, поэтому selectable text/vector layer после этого не сохраняются
- PDF toolkit OCR по умолчанию зависит от доступного `tesseract` language profile и без дополнительных traineddata стартует с `eng`
- HTML/EPUB preview удаляет активное содержимое и внешние references; unsafe SVG не показывается
  напрямую и проходит server-side rasterization
- workbook preview не выполняет macros, formulas и external connections; charts, comments и
  advanced styling не обещаются capability contract
- SQLite preview не принимает произвольный SQL и не вычисляет дорогой полный `COUNT(*)`

Это значит, что новые модули должны добавлять свою product-specific orchestration,
а не заново собирать browser-heavy runtime.

## Правило Для Новых Фич

Новая логика должна уходить в backend, если она:

- изменяет файл или строит новый artifact
- валидирует, санитизирует или извлекает структурированные данные
- тяжёлая по CPU/RAM
- требует единых лимитов, retry, observability или audit
- должна одинаково работать между браузерами и устройствами
- влияет на capability rules и source of truth продукта

На frontend её стоит оставлять только тогда, когда это действительно interaction/presentation
или безопасный browser-native сценарий без заметной выгоды от server orchestration.

Dev Tools iteration 7 является именно таким исключением: текстовые encode/hash/JWT/link/validation
сценарии выполняются локально, не мутируют backend state и должны ощущаться как мгновенный toolbox,
а не как ещё один queued processing product.

## Limits, Retention И Privacy

Default policy `jack-processing-2`:

| Ограничение                 |                                                Default |
| --------------------------- | -----------------------------------------------------: |
| Upload                      |                                                 64 MiB |
| Result artifact             |                                                128 MiB |
| Decoded image               |                                      40,000,000 pixels |
| Document                    |                                              500 pages |
| Table intake                |                            20,000 rows / 400,000 cells |
| Archive                     | 2,048 entries / 128 MiB expanded / ratio 100 / depth 1 |
| Native stdout+stderr budget |                                                  1 MiB |
| Global jobs / queue         |                                                 4 / 16 |
| Concurrent jobs per session |                                                      2 |
| Storage per session         |                                                256 MiB |
| Public mutations            |                                     30 requests/minute |
| Upload/job/artifact TTL     |                                               24 hours |

Cookie содержит случайный owner UUID и HMAC, имеет `HttpOnly`/`SameSite=Strict`; production
включает `Secure`. Это anonymous isolation, а не account authentication. Artifact UUID сам по
себе не даёт доступ: download повторно проверяет owner и стримит файл без `readAllBytes`.
Processing API и frontend shell получают `no-store`; scheduled cleanup удаляет expired metadata
и physical files.

Все значения настраиваются через `JACK_PROCESSING_*`, но увеличение limit должно сопровождаться
resource/load review. Полная таблица угроз и проверок находится в
[processing-threat-model.md](processing-threat-model.md).

## Production Boundary И Deployment Prerequisites

Завершён single-replica/single-writer production boundary: PostgreSQL хранит durable state,
processing PVC — bytes, очередь bounded, контейнеры non-root/read-only, network policy
default-deny, images сканируются и подписываются.

Перед rollout обязательны:

- PostgreSQL/PVC backup и доступность Flyway migration;
- Kubernetes secret `jack-secrets` с `postgres-*`, `web-allowed-origins` и случайным
  `processing-session-secret` минимум 32 bytes;
- замена `sha-RELEASE_SHA` в manifests на опубликованный immutable image tag/digest;
- TLS ingress для Secure cookie/HSTS и разрешённый ingress namespace label;
- smoke `/actuator/health/readiness`, owner isolation, upload/job/artifact и cleanup после
  применения manifests.

Для horizontal scaling нужны shared object storage, distributed queue/lease и единый cleanup
coordinator. Account authentication/audit, certificate-based e-sign и дополнительные OCR packs
остаются отдельными product capabilities, а не скрытыми обещаниями текущей platform.
