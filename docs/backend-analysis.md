# Backend Analysis

См. также: `docs/backend-migration-plan.md`

## Цель

Свести Jack от `browser-first everywhere` к модели, где backend не просто дублирует код на Java, а:

- снимает тяжёлые вычисления с клиента
- делает результат стабильнее между браузерами и устройствами
- упрощает безопасность и ограничения по форматам
- открывает batch/job-based сценарии, которые фронтенд сам по себе не тянет

## Текущее Состояние

### Backend

Сейчас backend уже перестал быть пустым bootstrap:

- есть processing foundation с upload/job/artifact/capability API
- есть async media job `MEDIA_PREVIEW` поверх `ffprobe`/`ffmpeg`
- есть image-processing service и async job `IMAGE_CONVERT` для heavy image preview/conversion
- есть document-processing service и async job `DOCUMENT_PREVIEW` для document intelligence payload
- есть metadata-processing service и async job `METADATA_EXPORT` для image/audio metadata inspect и image metadata export
- есть server-owned capability matrix для viewer/converter format-, scenario- и preset-contracts
- backend уже умеет сохранять source upload, считать `sha256`, отдавать artifacts и репортить capability state
- из production-grade частей всё ещё отсутствуют постоянное хранилище, cleanup policy, retries, queueing и специализированные processing domains

Вывод: backend уже стал участником продукта и забрал media, heavy imaging, document intelligence, metadata, capability-matrix домены, backend-first converter route и unified viewer route; следующая крупная миграция теперь смещается на reuse platform для новых модулей.

### Frontend

Frontend уже выполняет роль orchestration/UI слоя, но platform-решения теперь заметно тоньше:

- больше не держит локальный registry/preset catalog как единственную правду
- получает capability/source-target/preset matrix с backend и резолвит UI поверх неё
- сам отвечает за формы, локальную фильтрацию и download UX вокруг metadata/document/media payload
- для legacy media, heavy imaging, document preview и metadata уже общается с backend processing API

Это видно по зависимостям и runtime-слою:

- browser-heavy metadata deps уже удалены из active runtime после `Phase 4`
- supported formats/scenarios/presets теперь приходят через backend capability API и кэшируются на frontend только как thin data layer

## Что Уже Нельзя Считать Только UI-Логикой

### 1. Media Transcode Bridge

Статус: закрыто в `Phase 1`.

Во viewer legacy video/audio контейнеры уже больше не нормализуются в браузере. Теперь frontend
идёт в backend `MEDIA_PREVIEW` flow и получает готовый browser-friendly artifact.

Файлы:

- `frontend/src/features/viewer/application/viewer-server-preview.ts`
- `backend/src/main/java/com/keykomi/jack/processing/application/MediaPreviewService.java`

Почему это кандидат на backend:

- высокая CPU и memory нагрузка на клиент
- тяжёлый initial payload и долгий cold start
- сильная зависимость от устройства пользователя
- нет централизованного контроля над timeout, лимитами, retry и диагностикой
- нельзя нормально построить очередь задач, повторное использование артефактов и batch

Решение:

- перенесено: transcode/normalize-preview теперь работает как backend job-based media service
- фронтенду оставить playback UI, poster capture, local subtitles и session state

Приоритет: `P0`

### 2. Heavy Image Decode/Encode Pipeline

Статус: закрыто в `Phase 2`.

До миграции конвертер и viewer тащили тяжёлые image pipelines в браузер:

- HEIC decode
- TIFF/RAW preview extraction
- PSD composite decode
- AI/EPS preview decode
- AVIF encode
- TIFF encode
- ICO assembly
- bitmap tracing в SVG
- raster PDF generation

Ключевые файлы до миграции:

- `frontend/src/features/imaging/application/image-raster-codecs.ts`
- `frontend/src/features/imaging/application/illustration-raster.ts`
- `frontend/src/features/imaging/application/psd-raster.ts`
- `frontend/src/features/imaging/application/avif-image.ts`
- `frontend/src/features/imaging/application/tiff-image.ts`
- `frontend/src/features/imaging/application/ico-image.ts`
- `frontend/src/features/imaging/application/vectorized-svg.ts`
- `frontend/src/features/imaging/application/pdf-document.ts`
- `frontend/src/features/converter/application/converter-runtime.ts`

Ключевые файлы после миграции:

- `backend/src/main/java/com/keykomi/jack/processing/application/ImageProcessingService.java`
- `frontend/src/features/converter/application/converter-server-runtime.ts`
- `frontend/src/features/viewer/application/viewer-server-preview.ts`
- `frontend/src/features/processing/application/processing-client.ts`

Почему это кандидат на backend:

- часть форматов декодируется эвристически и не даёт гарантированно одинаковый результат
- большие изображения и RAW/TIFF легко выбивают память браузера
- encode/trace/preview сложно масштабировать на мобильных устройствах
- невозможно делать persistent cache и переиспользование промежуточных артефактов
- будущие batch-конверсии и target-size сценарии логично серверные

Решение:

- перенесено: backend `IMAGE_CONVERT` теперь собирает preview/result artifacts для HEIC, TIFF, RAW, PSD, AI/EPS, AVIF, TIFF, ICO, traced SVG и raster PDF
- frontend viewer использует server-assisted preview для `heic`/`tiff`/`raw`
- frontend converter переведён в backend-first route: любой supported conversion scenario идёт через `IMAGE_CONVERT` jobs, а browser держит orchestration, preview, retry/cancel и artifact reuse
- контейнерный backend сам ставит `ffmpeg`, `ImageMagick`, `Ghostscript`, `potrace` и `libraw`, так что server imaging path работает и в `docker compose`

Приоритет: `P0`

### 3. Document Parsing And Text Extraction

Статус: закрыто в `Phase 3`.

До миграции документный viewer парсил всё в браузере:

- PDF text extraction и page stats
- DOCX/XLSX/PPTX через OOXML parsing
- DOC/XLS через legacy binary adapters
- ODT/EPUB через zip/xml parsing
- CSV/HTML/RTF sanitization/extraction
- SQLite schema/sample preview

Ключевые файлы после миграции:

- `backend/src/main/java/com/keykomi/jack/processing/application/DocumentPreviewService.java`
- `backend/src/main/java/com/keykomi/jack/processing/domain/DocumentPreviewPayload.java`
- `frontend/src/features/viewer/application/viewer-server-preview.ts`
- `frontend/src/features/processing/application/processing-client.ts`

Почему это кандидат на backend:

- document parsing уже является business logic, а не presentation
- HTML sanitization и document inspection лучше централизовать и тестировать на сервере
- большие документы, книги и базы данных будут упираться в RAM/CPU клиента
- будущие OCR, PDF toolkit, DOCX/PDF roundtrip и export уже почти невозможно делать только на фронте
- backend сможет давать единый search/extract contract для viewer, converter, PDF toolkit и editor

Решение:

- перенесено: parsing/extraction теперь живут в backend document service
- фронту отдаётся готовый structured payload:
  - `summary`
  - `warnings`
  - `searchableText`
  - `outline`
  - `table/sheet/slide/database preview`
- frontend viewer больше не тянет `pdfjs-dist`, `jszip`, `xlsx`, `cfb`, `sql.js` ради active document runtime

Приоритет: `P0`

### 4. Metadata Extraction And Mutation

Статус: закрыто в `Phase 4`.

До миграции metadata логика жила в браузере:

- image metadata extraction и grouping
- audio tag extraction
- JPEG EXIF patch export
- fallback sidecar generation

Ключевые файлы после миграции:

- `backend/src/main/java/com/keykomi/jack/processing/application/MetadataProcessingService.java`
- `backend/src/main/java/com/keykomi/jack/processing/domain/MetadataPayloads.java`
- `backend/src/main/java/com/keykomi/jack/processing/domain/MetadataProcessingRequest.java`
- `frontend/src/features/viewer/application/viewer-metadata-client.ts`
- `frontend/src/features/viewer/application/viewer-preview.ts`
- `frontend/src/features/viewer/application/viewer-metadata-writer.ts`

Почему это кандидат на backend:

- metadata edit/export это уже файловая доменная операция
- server-side проще валидировать допустимые поля и хранить audit trail
- легче поддержать больше форматов записи, а не только JPEG-sidecar split
- безопаснее централизовать обработку пользовательских файлов

Что оставить на фронте:

- фильтр групп
- форма редактирования
- optimistic UX вокруг editable common fields
- download UX вокруг уже готового export artifact

Приоритет: `P1`

Решение:

- перенесено: image metadata read, audio tag read и image metadata export теперь живут в backend metadata service
- backend валидирует supported editable fields и хранит export mode в manifest artifact
- frontend больше не тянет `exifreader`, `piexifjs`, `music-metadata-browser`, `utif2`

### 5. Capability Registry И Scenario Registry Как Общий Контракт

Статус: закрыто в `Phase 5`.

Раньше registry был определён только во frontend:

- `viewer-registry.ts`
- `converter-registry.ts`
- `converter-presets.ts`

Проблема:

- фронтенд решает, что продукт умеет, а backend ничего об этом не знает
- при появлении server jobs фронт и бэк начнут расходиться по поддержке форматов и ограничений

Решение:

- перенесено: backend теперь отдаёт viewer/converter matrix через capability API
- matrix включает format/scenario/preset definitions, required job types, accept rules и explicit availability details
- frontend больше не использует локальный registry/preset catalog как единственный источник правды и только резолвит UI поверх server-owned contract

Приоритет: `P1`

### 6. Viewer Route Flip

Статус: закрыто в `Phase 7`.

Последний крупный остаток после media/imaging/document/metadata миграций был уже не в raw decode,
а в самом viewer route: фронтенд всё ещё дёргал разные backend jobs по семействам и сам
дособирал итоговый preview contract.

Решение:

- перенесено: backend теперь держит единый `VIEWER_RESOLVE` job поверх `MEDIA_PREVIEW`, `IMAGE_CONVERT`, `DOCUMENT_PREVIEW` и `METADATA_EXPORT`
- перенесено: frontend viewer схлопнут до native strategies и одного `server-viewer` adapter, который получает unified manifest и связанные artifacts
- перенесено: waveform для server-assisted audio теперь тоже собирается на backend, так что browser больше не декодирует legacy/lossless preview ради waveform rail
- итог: viewer route теперь backend-first для всех non-native тяжёлых форматов, а локально остались только rendering, state, interaction tooling и metadata export UX

Приоритет: `P1`

## Что Не Нужно Переносить На Backend

Это должно остаться client-side:

- zoom/rotation/fullscreen
- playback controls, mute, seek, loop, speed
- временные subtitle sidecars в рамках одной сессии
- poster capture из уже готового video element
- color picker, loupe, swatches, histogram
- локальный search/filter по уже готовому structured payload
- download/copy/share UX

Это даёт мгновенный отклик и не требует server round-trip, если backend уже отдал подготовленный результат.

## Почему Перенос Даст Реальную Пользу Продукту

### Performance

После закрытия `Phase 1`, `Phase 2` и `Phase 3` frontend уже перестал тащить `ffmpeg-core` wasm,
document parsers и heavy image adapters вроде `pdf.worker`, `sql.js`, `xlsx`, `cpexcel`, `heic2any`,
`ag-psd`, `imagetracerjs`, `@jsquash/avif`.

Оставшиеся тяжёлые зоны уже заметно уже:

- большой `ViewerWorkspaceView`
- будущие editor/pdf-toolkit сценарии, если их снова начать строить browser-first
- capability/scenario matrix, пока она живёт только на frontend

Следствие:

- долгий cold start
- тяжёлый first use для viewer/converter
- деградация на ноутбуках среднего класса и особенно на mobile

### Security

Server-side processing позволит:

- ограничивать размеры, длительность, количество страниц и сложность задач
- централизованно санитизировать HTML и document payload
- изолировать обработку недоверенных файлов
- ввести rate limit, quota, audit и observability

### Product Surface

Backend открывает то, что фронт сам не доберёт:

- очередь задач
- progress polling / SSE / websocket progress
- повторное скачивание результатов
- batch conversion
- shared preview cache
- OCR
- faithful PDF/office pipelines
- long-running jobs и ретраи

## Рекомендуемая Backend Архитектура

### 1. Processing Job API

Нужен единый job contract:

- `POST /api/jobs`
- `GET /api/jobs/{id}`
- `GET /api/jobs/{id}/result`
- `DELETE /api/jobs/{id}`

Полезные поля:

- `jobType`
- `sourceFormat`
- `targetFormat`
- `requestedOperations`
- `status`
- `progress`
- `warnings`
- `artifacts`
- `metrics`

### 2. File Processing Domains

Разделить backend по доменам:

- `media-processing`
- `image-processing`
- `document-processing`
- `metadata-processing`
- `capabilities`

### 3. Artifact Storage

Нужен слой хранения:

- временное хранилище исходников
- хранилище preview/result артефактов
- TTL cleanup
- dedup/cache по file hash + operation signature

### 4. Shared Output Contracts

Backend должен отдавать не “сырые байты и разбирайся сам”, а продуктовые DTO:

- `ViewerImagePayload`
- `ViewerDocumentPayload`
- `ViewerVideoPayload`
- `ViewerAudioPayload`
- `ConverterResultPayload`

Тогда UI реально станет thin-client.

## План Переноса По Этапам

### Этап 1. Сделать Backend Вообще Участником Потока

- capability API
- upload/result job foundation
- artifact storage
- progress/status model
- базовые limits/validation

Ожидаемый эффект:

- backend начинает управлять жизненным циклом операций
- фронт перестаёт быть единственным источником правды

### Этап 2. Вынести Media And Heavy Imaging

- legacy video preview bridge
- legacy audio preview bridge
- HEIC/TIFF/RAW/PSD/AI/EPS decode
- AVIF/TIFF/PDF/ICO/SVG encode

Ожидаемый эффект:

- самый заметный выигрыш по производительности и bundle weight

### Этап 3. Вынести Document Intelligence

- backend `DOCUMENT_PREVIEW`
- PDF extraction
- OOXML/legacy/archive parsing
- SQLite introspection
- HTML sanitization

Ожидаемый эффект:

- backend становится основой для PDF toolkit, editor и OCR

### Этап 4. Вынести Metadata Operations

- backend `METADATA_EXPORT`
- единый metadata read/write service
- validation/export policy
- подготовка к server-owned rules для следующих editor/converter flows

### Этап 5. Перевести Converter На Server-First

- sync для мелких задач
- async jobs для тяжёлых
- batch support
- reuse cached previews/results

## Практический Приоритет

### Переносить Сразу

1. legacy audio/video transcode
2. heavy image decode/encode
3. document parsing/extraction

### Переносить После Базы

1. metadata write/read
2. capability/scenario registry
3. search/index enrichment

### Оставлять На Клиенте

1. playback/viewport controls
2. color lab
3. local filtering
4. session-only subtitle/poster UX

## Итог

Главная проблема проекта не в том, что часть логики случайно уехала на фронт. Главная проблема в том, что почти весь продуктовый runtime уже живёт в браузере, а backend пока не влияет ни на производительность, ни на безопасность, ни на масштаб сценариев.

Лучший следующий шаг: строить не “Java-версию того же кода”, а backend как processing platform с capability API, job orchestration, artifact storage и server-owned preview/conversion contracts. Тогда фронтенд сохранит быстрый UX, но перестанет быть единственной средой исполнения для тяжёлых и рискованных операций.
