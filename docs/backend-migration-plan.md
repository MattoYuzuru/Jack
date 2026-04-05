# Backend Migration Plan

## Задача Плана

Постепенно перевести Jack от модели, где почти весь processing runtime живёт в браузере, к модели:

- backend владеет тяжёлыми и рискованными операциями
- frontend остаётся быстрым orchestration/UI слоем
- новые модули вроде `compression`, `pdf toolkit`, `editor` и `batch conversion` строятся поверх уже готовой processing-platform

План намеренно разбит так, чтобы не делать big-bang переписывание viewer/converter за один проход.

## Главный Принцип

Не переписывать клиентские helper'ы один в один на Java.

Нужно строить backend как platform:

- upload + artifact storage
- capability contract
- async jobs
- processing adapters
- preview/result DTO
- progress/status API

И только после этого переводить конкретные сценарии с фронта на backend.

## Целевое Разделение Ответственности

### Backend

Backend отвечает за:

- file intake и валидацию
- heavy decode/encode/transcode
- metadata read/write
- document parsing/extraction
- progress, retries, limits, diagnostics
- хранение preview/result артефактов
- capability matrix продукта

### Frontend

Frontend отвечает за:

- file picker / drag-and-drop
- orchestration запросов к backend
- playback controls, zoom, rotate, fullscreen
- local session state
- subtitle rail, poster rail, swatches, loupe, filters
- rendering уже готовых structured payload и артефактов

## Фазы

### Phase 0. Processing Foundation

Цель:

- сделать backend технически готовым к file-processing задачам

Что сделать на backend:

- добавить модульную структуру под processing domains
- ввести `job` сущность и статусы:
  - `queued`
  - `running`
  - `completed`
  - `failed`
  - `cancelled`
- добавить file upload intake
- добавить временное хранилище исходников и результатов
- добавить cleanup policy по TTL
- добавить capability API
- добавить progress/status API
- добавить лимиты на размер, MIME, длительность, число страниц

Минимальные API:

- `POST /api/uploads`
- `POST /api/jobs`
- `GET /api/jobs/{id}`
- `GET /api/jobs/{id}/artifacts/{artifactId}`
- `GET /api/capabilities/viewer`
- `GET /api/capabilities/converter`

Что сделать на frontend:

- выделить API client слой
- перестать создавать runtime напрямую в composables
- подготовить `local runtime` и `server runtime` как взаимозаменяемые adapters

Критерий готовности:

- frontend умеет отправить файл и дождаться mock/real job result без прямой обработки файла в браузере

Приоритет: `P0`

### Phase 1. FFmpeg Service First

Цель:

- снять с браузера самые тяжёлые media-задачи

Что переносим:

- `viewer-video-transcode.ts`
- `viewer-audio-transcode.ts`
- часть `viewer-ffmpeg.ts`

Что сделать на backend:

- поднять media-processing service поверх `ffmpeg` + `ffprobe`
- реализовать:
  - codec/container probe
  - legacy video -> browser-playable preview
  - legacy audio -> browser-playable preview
  - preview warnings и technical metadata
- нормализовать output artifacts:
  - preview blob
  - metadata payload
  - warnings

Почему начинать отсюда:

- это самый тяжёлый browser payload
- это самый заметный user-facing выигрыш
- это создаёт reusable ffmpeg foundation для converter, compression и future PDF/video tasks

Что остаётся на frontend:

- native `<video>` / `<audio>`
- seek/rate/mute/loop
- subtitle sidecars
- poster capture
- timeline UX

Критерий готовности:

- AVI/MKV/WMV/FLV и AAC/FLAC/AIFF больше не требуют `ffmpeg.wasm` в браузере

Приоритет: `P0`

### Phase 2. Imaging Processing Service

Цель:

- перевести тяжёлый image decode/encode на backend

Что переносим:

- `image-raster-codecs.ts`
- `illustration-raster.ts`
- `psd-raster.ts`
- `avif-image.ts`
- `tiff-image.ts`
- `ico-image.ts`
- `vectorized-svg.ts`
- `pdf-document.ts`
- heavy branches из `converter-runtime.ts`

Что сделать на backend:

- image-processing service
- unified raster contract на server side
- adapters для:
  - HEIC
  - TIFF / RAW preview extraction
  - PSD composite preview
  - AI / EPS preview path
- encoders/targets для:
  - JPG
  - PNG
  - WebP
  - AVIF
  - TIFF
  - ICO
  - traced SVG
  - single-page raster PDF

Что сделать на frontend:

- перевести converter на server-assisted mode
- использовать server result DTO вместо local `createConverterRuntime()` для heavy paths
- browser-native trivial paths можно временно оставить как fallback

Критерий готовности:

- конвертер умеет выполнять heavy scenarios через backend jobs
- viewer image preview для HEIC/TIFF/RAW больше не зависит от browser-heavy decode

Приоритет: `P0`

### Phase 3. Document Intelligence Service

Цель:

- вынести document parsing из браузера

Что переносим:

- `viewer-document-preview.ts`
- `viewer-document-legacy.ts`
- `viewer-document-archive.ts`
- `viewer-document-database.ts`
- `viewer-ooxml.ts`

Что сделать на backend:

- document-processing service
- единый contract для:
  - `summary`
  - `warnings`
  - `searchableText`
  - `outline`
  - `layout payload`
- document adapters:
  - PDF
  - TXT
  - CSV
  - HTML sanitize + outline
  - RTF text extraction
  - DOC / XLS legacy extraction
  - DOCX / XLSX / PPTX OOXML
  - ODT / EPUB archive parsing
  - SQLite schema/sample introspection

Почему не делать это раньше foundation:

- document payload лучше сразу строить поверх общих job/artifact/capability contracts
- этот слой потом станет базой для PDF toolkit, editor и OCR

Что остаётся на frontend:

- search box поверх уже готового `searchableText`
- sheet/slide/table navigation
- visual panels

Критерий готовности:

- viewer document route получает всё необходимое для preview по API и почти не парсит формат локально

Приоритет: `P0`

### Phase 4. Metadata Service

Цель:

- вынести metadata extraction/mutation в отдельный backend domain

Что переносим:

- `viewer-metadata.ts`
- `viewer-preview.ts` metadata branches
- `viewer-metadata-writer.ts`
- `viewer-audio-metadata.ts`

Что сделать на backend:

- image metadata read service
- audio tag read service
- metadata patch validation
- metadata export modes:
  - embedded where supported
  - sidecar where safer
- единая политика supported fields и audit trail

Что остаётся на frontend:

- metadata forms
- local filtering по groups
- UX around save/export/download

Критерий готовности:

- metadata operations больше не требуют frontend-only parsers и JPEG-only patch logic

Приоритет: `P1`

### Phase 5. Server-Owned Capability Matrix

Цель:

- сделать backend источником правды для supported formats и scenarios

Что переносим:

- `viewer-registry.ts`
- `converter-registry.ts`
- `converter-presets.ts` как server-validated rules

Что сделать на backend:

- capability registry
- scenario matrix
- target constraints
- preset rules
- explicit fallback reasons

Что сделать на frontend:

- получать capability matrix с backend
- убрать жёсткую привязку UI к локальному registry как единственной правде

Критерий готовности:

- новый format/scenario можно включить на backend без ручного расхождения с frontend registry

Приоритет: `P1`

### Phase 6. Converter Route Flip

Цель:

- сделать converter backend-first по умолчанию

Что сделать:

- перевести `useConverterWorkspace` на job workflow
- добавить progress UI
- добавить retry/cancel
- добавить artifact reuse и повторное скачивание
- сохранить browser preview для показа результата, а не для вычислений

Критерий готовности:

- converter больше не зависит от local heavy runtime для основных сценариев

Приоритет: `P1`

### Phase 7. Viewer Route Flip

Цель:

- сделать viewer server-assisted для всех тяжёлых non-native formats

Что сделать:

- image/document/audio/video adapters дергать через backend payload API
- локально оставить только:
  - native rendering
  - state
  - interaction tooling
- убрать browser-only heavy fallback там, где сервер уже стабилен

Критерий готовности:

- viewer resolve pipeline в основном работает через backend-provided payload/artifact

Приоритет: `P1`

### Phase 8. Reuse For New Modules

Цель:

- использовать созданную platform base для следующих roadmap-модулей

Куда переиспользуем:

- `Compression`
- `PDF Toolkit`
- `Multi-Format Editor`
- batch conversion
- OCR
- future office/pdf conversion flows

Критерий готовности:

- новые модули стартуют не с нуля, а как thin features над существующей processing-platform

Приоритет: `P2`

## Технические Решения По Инструментам

### FFmpeg

Для media и части converter-пайплайнов ffmpeg нужен как server runtime, а не как browser runtime.

Рекомендация:

- использовать системный `ffmpeg`/`ffprobe` в backend container
- обернуть его в adapter/service слой
- не пытаться реализовать video/audio transform logic чисто библиотеками Java

Причина:

- ffmpeg уже естественно решает probe/transcode/extract задачи
- этот же слой потом пригодится для compression, waveform jobs, poster extraction, audio extraction из video и future delivery presets

### Image Toolchain

Для imaging-слоя важно не упереться в “всё на Java вручную”.

Рекомендация:

- оставить backend orchestration на Java
- тяжёлые format-specific операции выполнять через проверенный processing stack
- unified DTO и job lifecycle держать в Spring-приложении

Важный принцип:

- Java должна владеть orchestration и контрактами
- format runtimes могут быть внешними процессами/инструментами, если это даёт стабильный результат

### Document Layer

Для document parsing нужен отдельный domain service, а не разрозненные контроллеры на каждый формат.

Рекомендация:

- строить единый `DocumentPreviewPayload`
- уметь хранить warnings и degradation reasons явно
- не обещать faithful render там, где реально есть только extraction path

## Зависимости Между Фазами

- `Phase 0` обязательна перед всеми остальными
- `Phase 1` должна быть раньше полного media migration
- `Phase 2` и `Phase 3` могут идти частично параллельно после foundation
- `Phase 4` лучше делать после foundation и document/image services
- `Phase 5` можно делать после того, как хотя бы media + imaging backend paths уже реальны
- `Phase 6` зависит от `Phase 2` и части `Phase 5`
- `Phase 7` зависит от `Phase 1`, `Phase 3`, `Phase 4`, `Phase 5`

## Что Делать В Самом Ближайшем Цикле

Если идти прагматично, следующий рабочий порядок такой:

1. `Phase 0`
2. `Phase 1`
3. первая часть `Phase 2`
4. первая часть `Phase 3`
5. `Phase 5`
6. `Phase 6`
7. `Phase 7`

То есть ближайшая practical цепочка:

1. job/upload/artifact foundation
2. ffmpeg-service
3. heavy image processing
4. document extraction
5. capability matrix
6. converter flip
7. viewer flip

## Что Считать Успехом

Переход можно считать удачным, если:

- frontend bundle заметно худеет за счёт ухода `ffmpeg.wasm` и части heavy format adapters
- legacy media preview больше не зависит от мощности устройства пользователя
- converter и viewer умеют работать через jobs и progress
- backend становится общей базой для `converter`, `viewer`, `compression`, `pdf toolkit`
- добавление новых formats/scenarios происходит через server-owned capabilities, а не через логику в UI

## Риски

Основные риски:

- попытка мигрировать всё сразу
- переписывание всех локальных helper'ов на Java без platform design
- смешивание UI state и processing contracts
- отсутствие artifact storage и progress model в первых фазах
- слишком ранний отказ от client-side fallback до стабилизации backend paths

Правильная стратегия:

- foundation first
- ffmpeg first
- heavy runtimes next
- routes flip only after stable server contracts
