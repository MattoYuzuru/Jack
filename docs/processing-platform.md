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
- `GET /actuator/metrics`

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
- TTL cleanup policy для upload / artifact / job storage
- actuator metrics для job-state и cleanup counters
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

На клиенте остались native rendering, state и interaction tooling, а для тексто-ориентированных
document layouts появился quick-edit handoff в editor без мутации исходного файла.

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

## Что Ещё Не Production-Grade

Текущая processing-platform уже рабочая, но ещё не закрывает весь production hardening:

- постоянное хранилище вместо локального temp storage
- очередь и retry policy
- quota / rate limit / audit
- постоянное artifact-хранилище вместо локального temp storage для крупных PDF/OCR bundles
- отдельный certificate-based e-sign layer и richer OCR language packs поверх уже поднятого PDF toolkit route
