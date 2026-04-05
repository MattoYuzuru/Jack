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
- есть первый async media job `MEDIA_PREVIEW` поверх `ffprobe`/`ffmpeg`
- backend уже умеет сохранять source upload, считать `sha256`, отдавать artifacts и репортить capability state
- из production-grade частей всё ещё отсутствуют постоянное хранилище, cleanup policy, retries, queueing и специализированные processing domains

Вывод: backend уже стал участником продукта, но пока покрывает только foundation и первый media-срез, а основная доменная глубина всё ещё находится на frontend runtime.

### Frontend

Frontend уже выполняет роль mini-backend:

- сам определяет capability map и стратегии форматов через registry/runtime
- сам парсит документы, архивы, изображения, аудио и видео
- сам делает heavy decode/encode в браузере для image/document-heavy веток
- сам редактирует metadata и собирает export-артефакты
- для legacy media уже общается с backend processing API, но остальные тяжёлые домены всё ещё живут локально

Это видно по зависимостям и runtime-слою:

- `pdfjs-dist` для PDF/illustration parsing
- `sql.js` для SQLite introspection
- `heic2any`, `utif2`, `ag-psd`, `imagetracerjs`, `@jsquash/avif`
- `jszip`, `xlsx`, `cfb`, `music-metadata-browser`, `exifreader`, `piexifjs`

## Что Уже Нельзя Считать Только UI-Логикой

### 1. Media Transcode Bridge

Статус: закрыто в `Phase 1`.

Во viewer legacy video/audio контейнеры уже больше не нормализуются в браузере. Теперь frontend
идёт в backend `MEDIA_PREVIEW` flow и получает готовый browser-friendly artifact.

Файлы:

- `frontend/src/features/viewer/application/viewer-media-preview.ts`
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

Конвертер и viewer уже тащат тяжёлые image pipelines в браузер:

- HEIC decode
- TIFF/RAW preview extraction
- PSD composite decode
- AI/EPS preview decode
- AVIF encode
- TIFF encode
- ICO assembly
- bitmap tracing в SVG
- raster PDF generation

Файлы:

- `frontend/src/features/imaging/application/image-raster-codecs.ts`
- `frontend/src/features/imaging/application/illustration-raster.ts`
- `frontend/src/features/imaging/application/psd-raster.ts`
- `frontend/src/features/imaging/application/avif-image.ts`
- `frontend/src/features/imaging/application/tiff-image.ts`
- `frontend/src/features/imaging/application/ico-image.ts`
- `frontend/src/features/imaging/application/vectorized-svg.ts`
- `frontend/src/features/imaging/application/pdf-document.ts`
- `frontend/src/features/converter/application/converter-runtime.ts`

Почему это кандидат на backend:

- часть форматов декодируется эвристически и не даёт гарантированно одинаковый результат
- большие изображения и RAW/TIFF легко выбивают память браузера
- encode/trace/preview сложно масштабировать на мобильных устройствах
- невозможно делать persistent cache и переиспользование промежуточных артефактов
- будущие batch-конверсии и target-size сценарии логично серверные

Решение:

- перевести converter в `server-assisted` режим с async job/result API
- оставить на фронте только быстрые browser-native операции и preview state

Приоритет: `P0`

### 3. Document Parsing And Text Extraction

Сейчас документный viewer парсит всё в браузере:

- PDF text extraction и page stats
- DOCX/XLSX/PPTX через OOXML parsing
- DOC/XLS через legacy binary adapters
- ODT/EPUB через zip/xml parsing
- CSV/HTML/RTF sanitization/extraction
- SQLite schema/sample preview

Файлы:

- `frontend/src/features/viewer/application/viewer-document-preview.ts`
- `frontend/src/features/viewer/application/viewer-document-legacy.ts`
- `frontend/src/features/viewer/application/viewer-document-archive.ts`
- `frontend/src/features/viewer/application/viewer-document-database.ts`
- `frontend/src/features/viewer/application/viewer-ooxml.ts`

Почему это кандидат на backend:

- document parsing уже является business logic, а не presentation
- HTML sanitization и document inspection лучше централизовать и тестировать на сервере
- большие документы, книги и базы данных будут упираться в RAM/CPU клиента
- будущие OCR, PDF toolkit, DOCX/PDF roundtrip и export уже почти невозможно делать только на фронте
- backend сможет давать единый search/extract contract для viewer, converter, PDF toolkit и editor

Решение:

- вынести parsing/extraction в backend document service
- фронту отдавать готовый structured payload:
  - `summary`
  - `warnings`
  - `searchableText`
  - `outline`
  - `table/sheet/slide/database preview`

Приоритет: `P0`

### 4. Metadata Extraction And Mutation

Сейчас metadata логика тоже живёт в браузере:

- image metadata extraction и grouping
- audio tag extraction
- JPEG EXIF patch export
- fallback sidecar generation

Файлы:

- `frontend/src/features/viewer/application/viewer-metadata.ts`
- `frontend/src/features/viewer/application/viewer-preview.ts`
- `frontend/src/features/viewer/application/viewer-metadata-writer.ts`
- `frontend/src/features/viewer/application/viewer-audio-metadata.ts`

Почему это кандидат на backend:

- metadata edit/export это уже файловая доменная операция
- server-side проще валидировать допустимые поля и хранить audit trail
- легче поддержать больше форматов записи, а не только JPEG-sidecar split
- безопаснее централизовать обработку пользовательских файлов

Что оставить на фронте:

- фильтр групп
- форма редактирования
- optimistic UX вокруг editable common fields

Приоритет: `P1`

### 5. Capability Registry И Scenario Registry Как Общий Контракт

Сейчас registry определён только во frontend:

- `viewer-registry.ts`
- `converter-registry.ts`
- `converter-presets.ts`

Проблема:

- фронтенд решает, что продукт умеет, а backend ничего об этом не знает
- при появлении server jobs фронт и бэк начнут расходиться по поддержке форматов и ограничений

Решение:

- перенести capability/source-target matrix в общий server-owned contract
- фронтенду отдавать матрицу через API или собирать из shared schema

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

После закрытия `Phase 1` frontend уже перестал тащить `ffmpeg-core` wasm, но в браузере всё ещё
остаются тяжёлые артефакты:

- `heic2any` bundle больше `1.3 MB`
- `pdf.worker` больше `2.1 MB`
- `avif` wasm-ассеты по нескольку мегабайт
- `sql.js`, `xlsx`, `cpexcel`, `ViewerWorkspaceView` тоже заметно раздувают клиент

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

- PDF extraction
- OOXML/legacy/archive parsing
- SQLite introspection
- HTML sanitization

Ожидаемый эффект:

- backend становится основой для PDF toolkit, editor и OCR

### Этап 4. Вынести Metadata Operations

- единый metadata read/write service
- расширение write-capabilities beyond JPEG
- validation/audit policy

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
