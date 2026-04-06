# <img src="./assets/brand/logo.svg" alt="Логотип Jack" width="196">

**Jack** (`Jack of all trades`) — это веб-приложение-мультитул, в котором повседневные операции с файлами, текстом и дев-инструментами собираются в одном удобном интерфейсе.

Сейчас репозиторий оформлен как монорепо:

- `backend/` — Spring Boot 4.0.5, Java 26
- `frontend/` — Vue 3 на актуальной стабильной ветке
- `assets/brand/` — исходники логотипа и favicon

## Для Чего Нужен Jack

Jack задуман как практичный рабочий набор инструментов для:

- просмотра и предпросмотра файлов
- конвертации между популярными форматами
- сжатия файлов с ограничением по размеру
- PDF-сценариев
- текстовых редакторов с live preview
- дев-утилит: кодировок, декодеров, хешей, валидаторов, JWT-инструментов, коротких ссылок и не только

## Что Уже Есть

На текущем этапе собран стартовый bootstrap-слой:

- backend и frontend разнесены по отдельным каталогам
- backend теперь уже не только health-check bootstrap: есть первый processing foundation с upload/job/artifact/capability API
- есть новый UI foundation с neumorphic home-dashboard и крупной навигационной сеткой модулей
- home уже ведёт в четыре живых маршрута: `viewer`, `converter`, `compression` и `pdf-toolkit`
- есть server-assisted processing layer: backend уже закрывает legacy media preview, heavy image-processing jobs, document intelligence preview и metadata operations
- есть reusable processing-platform: backend отдаёт viewer/converter/compression/pdf-toolkit capability matrix и `platform`-matrix для следующих queued-модулей
- подготовлены логотип и favicon для дальнейшего использования
- есть `docker compose`-окружение для локального старта
- задокументированы workflow-правила, финальная platform-архитектура и roadmap для будущих итераций

## Быстрый Старт Через Docker Compose

Запуск всех сервисов:

```bash
docker compose up -d --build
```

После старта будут доступны:

- frontend: `http://localhost:5173`
- backend: `http://localhost:8080`
- backend health: `http://localhost:8080/actuator/health`
- postgres: `localhost:5432`

Остановка:

```bash
docker compose down
```

Остановка с удалением томов:

```bash
docker compose down -v
```

## Переменные Окружения

При необходимости можно создать локальный `.env` на основе [.env.example](/home/mattoyudzuru/IdeaProjects/Jack/.env.example).

Основные переменные:

- `JACK_FRONTEND_PORT`
- `JACK_BACKEND_PORT`
- `JACK_DB_PORT`
- `JACK_DB_NAME`
- `JACK_DB_USERNAME`
- `JACK_DB_PASSWORD`
- `JACK_API_BASE_URL`
- `JACK_WEB_ALLOWED_ORIGINS`
- `JACK_PROCESSING_STORAGE_ROOT`
- `JACK_PROCESSING_MAX_UPLOAD_SIZE_BYTES`
- `JACK_PROCESSING_FFMPEG_EXECUTABLE`
- `JACK_PROCESSING_FFPROBE_EXECUTABLE`
- `JACK_PROCESSING_MEDIA_PREVIEW_TIMEOUT_SECONDS`
- `JACK_PROCESSING_IMAGE_CONVERT_EXECUTABLE`
- `JACK_PROCESSING_POTRACE_EXECUTABLE`
- `JACK_PROCESSING_RAW_PREVIEW_EXECUTABLE`
- `JACK_PROCESSING_IMAGE_PROCESSING_TIMEOUT_SECONDS`
- `JACK_PROCESSING_TESSERACT_EXECUTABLE`
- `JACK_PROCESSING_PDF_TOOLKIT_TIMEOUT_SECONDS`
- `JACK_PROCESSING_PDF_TOOLKIT_DEFAULT_OCR_LANGUAGE`

## Backend Processing Platform

Текущий backend-слой теперь уже поднимает общий processing workflow:

- `POST /api/uploads` — сохраняет файл во временное backend storage
- `GET /api/uploads/{id}` — возвращает metadata по upload
- `POST /api/jobs` — создаёт processing job
- `GET /api/jobs/{id}` — возвращает статус, progress и artifacts
- `GET /api/jobs/{id}/artifacts/{artifactId}` — скачивает artifact
- `GET /api/capabilities/viewer`
- `GET /api/capabilities/converter`
- `GET /api/capabilities/compression`
- `GET /api/capabilities/pdf-toolkit`
- `GET /api/capabilities/platform`

В текущем срезе реально реализованы десять job type:

- `UPLOAD_INTAKE_ANALYSIS` — подтверждает upload/storage/job flow и собирает manifest artifact
- `MEDIA_PREVIEW` — через `ffprobe` и `ffmpeg` собирает browser-friendly preview для legacy video/audio контейнеров и кладёт в artifacts и binary preview, и manifest
- `MEDIA_CONVERT` — через `ffprobe` и `ffmpeg` собирает preview/result artifacts для video/audio conversion, delivery export и container/codec transcode сценариев
- `IMAGE_CONVERT` — через server imaging toolchain собирает preview/result artifacts для heavy image preview и conversion scenarios
- `FILE_COMPRESS` — через size-first orchestration переиспользует `IMAGE_CONVERT` и `MEDIA_CONVERT` как внутренние candidate jobs и возвращает единый compression manifest/result/preview contract
- `PDF_TOOLKIT` — через page-aware PDF orchestration закрывает merge/split/rotate/reorder, OCR searchable export, visible signature stamps, term redaction и password protect/unlock flows
- `OFFICE_CONVERT` — через backend document/presentation/spreadsheet adapters собирает office/pdf conversion artifacts, manifest и preview для narrative, table и slide сценариев
- `DOCUMENT_PREVIEW` — через backend document intelligence service собирает `summary`, `warnings`, `searchableText`, `layout payload` и при необходимости PDF preview artifact
- `METADATA_EXPORT` — через backend metadata service читает image/audio metadata и собирает validated export artifact: embedded JPEG EXIF там, где это безопасно, и sidecar JSON для остальных контейнеров
- `VIEWER_RESOLVE` — собирает unified viewer manifest/artifact contract для server-assisted image/document/video/audio preview поверх уже существующих processing services

Контейнерный backend теперь сам ставит `ffmpeg`, `ffprobe`, `ImageMagick`, `Ghostscript`, `potrace`, `libraw`, `qpdf` и `tesseract`, поэтому `MEDIA_PREVIEW`, `MEDIA_CONVERT`, `IMAGE_CONVERT`, `FILE_COMPRESS`, `OFFICE_CONVERT` и `PDF_TOOLKIT` работают внутри `docker compose` без внешней подготовки образа.
Frontend viewer уже использует unified backend route для `avi`, `mkv`, `wmv`, `flv`, `aac`, `flac`, `aiff`, `heic`, `tiff`, `raw` family и всего document stack (`pdf`, `txt`, `csv`, `html`, `rtf`, `doc`, `docx`, `odt`, `xls`, `xlsx`, `pptx`, `epub`, `db`, `sqlite`), metadata read/export остаётся за `METADATA_EXPORT`, а converter теперь гонит через backend и heavy image scenarios, и office/pdf conversions, и video/audio delivery-сценарии, поэтому для локальной разработки backend также должен разрешать origin из `JACK_WEB_ALLOWED_ORIGINS`.
Compression теперь работает как отдельный backend-first route: image/video/audio файл отправляется в `FILE_COMPRESS`, а backend сам подбирает maximum-reduction, target-size или custom candidate ladder и возвращает итоговый artifact вместе с attempt history и preview.
PDF toolkit теперь тоже работает как отдельный backend-first route: прямой `PDF` intake reuse'ит `VIEWER_RESOLVE`, а совместимые image/office sources сначала проходят backend conversion в `PDF`, после чего тот же workspace закрывает merge/split/rotate, visible signature stamps, term redaction, OCR searchable export и password flows через `PDF_TOOLKIT`.
Отдельный `platform` capability scope теперь показывает, как следующие модули (`Multi-Format Editor`, `Batch Conversion`, `OCR`, `Office/PDF Conversion`) должны reuse'ить уже существующий processing stack, а не заводить новый browser-heavy runtime.

## Локальный Запуск Без Docker

Backend:

```bash
cd backend
./gradlew bootRun
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

## Структура Репозитория

- `compose.yaml` — единый контейнерный запуск
- `backend/Dockerfile` — контейнер backend-сервиса
- `frontend/Dockerfile` — контейнер frontend-сервиса
- `docs/processing-platform.md` — актуальное описание processing-platform и границы backend/frontend логики
- `AGENTS.md` — правила работы для будущих агентных итераций
- `assets/brand/` — логотип, favicon и сопутствующие ассеты

## Roadmap

### 0. Initial Bootstrap

- [x] Пустой, но рабочий bootstrap проекта
- [x] Контейнеризированный локальный запуск через `docker compose up -d --build`
- [x] Документация, правила репозитория и базовый workflow
- [x] Готовая основа для следующих итераций

### 1. UI/UX Foundation

- [x] Задать визуальную систему для всего продукта
- [x] Зафиксировать палитру, типографику, отступы и паттерны взаимодействия
- [x] Подготовить фундамент, на который дальше будут добавляться новые страницы, блоки и компоненты

### 2. File Viewer

- [x] Унифицированная загрузка файлов и рабочая зона предпросмотра
- [ ] Поддержка изображений, аудио, видео, офисных документов, файлов БД и других полезных форматов

#### 2.1 Image Viewer

- [x] Удобный интерфейс просмотра изображений
- [x] Масштаб, zoom, fullscreen, rotation
- [x] Просмотр и редактирование метаданных
- [x] Color picker с увеличительным стеклом
- [x] EXIF / ICC inspector, thumbnail preview и grouped metadata browser
- [x] Histogram, saved swatches и transparency checker как часть viewer workspace
- [x] Поддержка: `jpg`, `jpeg`, `png`, `webp`, `avif`, `heic`, `gif`, `bmp`, `tiff`, `svg`, `raw`, `ico`

Viewer уже даёт browser-native preview для `jpg`, `jpeg`, `png`, `webp`, `avif`, `gif`, `bmp`, `svg`, `ico`.
`heic`, `tiff` и `raw` family (`raw`, `dng`, `cr2`, `cr3`, `nef`, `arw`, `raf`, `rw2`, `orf`, `pef`, `srw`) теперь проходят через backend `VIEWER_RESOLVE`, который сам оркестрирует `IMAGE_CONVERT` и `METADATA_EXPORT`, собирает unified preview artifact и metadata payload и возвращает их в тот же image workspace.
Поверх этого viewer даёт EXIF/ICC inspection, backend-validated export и image analysis tooling прямо в той же рабочей зоне, но orchestration уже не размазана по отдельным client-side image adapters.

#### 2.2 Office Documents

- [x] Foundation для document runtime, capability map и единого workspace-контракта
- [x] Первый рабочий preview и удобная навигация для `pdf`, `txt`, `csv`, `html`, `rtf`
- [x] Search layer, outline/table preview и format-specific summary для поддержанных форматов
- [x] OOXML adapters для `docx`, `xlsx`, `pptx` с preview поверх общего document contract
- [x] Полировка UX для поддержанных документов: quick actions, sheet tabs, slide focus и более ясный search flow
- [x] Legacy/open/database adapters для `doc`, `odt`, `xls`, `epub`, `db`, `sqlite`
- [ ] Частичное редактирование содержимого там, где формат это позволяет
- [x] Поддержка: `doc`, `docx`, `pdf`, `txt`, `rtf`, `odt`, `xls`, `xlsx`, `csv`, `pptx`, `html`, `epub`, `db`, `sqlite`

Document viewer теперь использует тот же registry/strategy foundation, что и image layer, но сами
document container'ы больше не парсятся в браузере. Backend `VIEWER_RESOLVE` сводит их к единому viewer contract, а внутри переиспользует `DOCUMENT_PREVIEW`, который уже собирает
`pdf`, `txt`, `csv`, `html`, `rtf`, `doc`, `docx`, `odt`, `xls`, `xlsx`, `pptx`, `epub`, `db`, `sqlite`
к общему document contract:
`summary + search layer + layout mode + warnings`.
`pdf` открывается через backend-prepared PDF artifact и получает page/search stats, `csv` получает table preview,
`html` приходит после backend sanitization в sandbox `srcdoc`, `rtf` и `doc` идут через server text extraction path,
`docx` и `odt` собираются как structured document HTML, `xls` и `xlsx` как workbook/sheet preview, `pptx` как slide
text deck, `epub` как reflow reading layer, а `db/sqlite` как schema-aware database preview.
Поверх этого viewer даёт copy/download для extracted text, внятные active states для sheets/slides
и более читаемый search UX прямо внутри общего workspace, но тяжёлый parsing/runtime уже принадлежит backend.

#### 2.3 Video Viewer

- [x] Foundation для media runtime, capability map и video selection contract
- [x] Базовый видеоплеер внутри viewer workspace
- [x] Перемотка, скорость воспроизведения и стандартные инструменты проигрывания
- [x] Frame stepping, loop, keyboard shortcuts и timestamp helpers
- [x] Subtitle sidecars (`.vtt`, `.srt`) и session-level track switching
- [x] Poster extraction из текущего кадра и gallery/export flow
- [x] Более богатый metadata inspector: aspect ratio, orientation, estimated bitrate
- [x] Поддержка: `mp4`, `mov`, `avi`, `mkv`, `webm`, `wmv`, `flv`

Video layer теперь заводится тем же registry/strategy путём, что и image/document:
`mp4`, `mov`, `webm` идут в browser-native playback path с metadata inspection, timeline, volume,
speed, fullscreen и picture-in-picture. `avi`, `mkv`, `wmv`, `flv` теперь тоже заведены в тот же
workspace через backend `VIEWER_RESOLVE`: viewer отправляет исходный контейнер на backend,
получает unified manifest и browser-playable preview artifact и затем отдаёт его в тот же video contract без отдельной ветки UI.
Поверх foundation viewer даёт precision controls для frame-by-frame stepping с явной fps-assumption,
loop/timestamp helpers, session-level subtitle sidecars для `.vtt/.srt`, poster capture rail и
richer metadata inspector с aspect ratio, orientation и estimated bitrate.

#### 2.4 Audio Viewer

- [x] Работа с метаданными
- [x] Удобный аудиоплеер с нужной навигацией
- [x] Waveform, artwork preview, keyboard shortcuts и timestamp helpers
- [x] Compatibility bridge для legacy/lossless контейнеров
- [x] Поддержка: `mp3`, `wav`, `aac`, `flac`, `ogg`, `opus`, `aiff`

Audio layer теперь поднимается тем же registry/strategy путём, что и остальные viewer-семьи:
`mp3`, `wav`, `ogg`, `opus` идут в browser-native audio path, а `aac`, `flac`, `aiff` получают
server-assisted preview через backend `VIEWER_RESOLVE`, который сам переиспользует `MEDIA_PREVIEW`
и metadata inspect и затем сводит всё к тому же audio contract.
Поверх foundation viewer даёт waveform preview, cover-art display, tag inspector с common/native
groups, timeline/volume/rate controls, loop и keyboard flow для быстрых playback-check сценариев,
но audio tag extraction и waveform для non-native контейнеров теперь тоже приходят с backend, а не из browser-only parser'ов.

#### 2.5 Другие Форматы

- [ ] Дополнительные viewer-сценарии для нишевых и служебных форматов

### 3. Конвертация Файлов

- [x] Широкое покрытие конвертаций с фокусом на реальные пользовательские сценарии
- [x] Конвертация изображений, документов, таблиц, презентаций, видео и аудио
- [x] Трансформации контейнеров, кодеков, размеров и параметров качества

Конвертер теперь работает как backend-first processing route. Архитектура всё ещё построена через
`scenario registry -> source strategy -> target strategy`, но сам route больше не выбирает
между "простыми локальными" и "тяжёлыми серверными" ветками: любой поддержанный сценарий
уходит в backend `IMAGE_CONVERT`, `OFFICE_CONVERT` или `MEDIA_CONVERT`, а браузер держит orchestration,
progress UI, retry/cancel, artifact reuse и preview уже готового результата.
Backend собирает preview/result artifacts и централизованно применяет resize/quality baseline,
чтобы дальнейшие batch- и delivery-сценарии не размазывались по UI-настройкам.
Дополнительно capability/source-target/preset matrix теперь тоже приходит с backend `GET /api/capabilities/converter`:
frontend больше не держит локальный registry как единственный source of truth и только резолвит UI вокруг server-owned правил.
`JPG`, `PNG`, `WebP`, single-page `PDF`, single-frame `TIFF`, `AVIF`, `ICO`, traced `SVG`,
`PSD` composite decode, `AI/EPS` raster intake, `HEIC`, `TIFF`, `RAW`, narrative office formats,
spreadsheets, `PDF` table/text exports, `PPTX`-based document/media outputs, а также media container/audio delivery
теперь закрываются через единый server-owned job lifecycle без browser-heavy decode/encode runtime.

#### 3.1 Частые Сценарии Для Изображений

- [x] `HEIC -> JPG`
- [x] `PNG -> JPG`
- [x] `JPG -> PNG`
- [x] `JPG/PNG -> WebP`
- [x] `JPG/PNG -> AVIF`
- [x] `WebP -> JPG/PNG`
- [x] `BMP -> JPG/PNG`
- [x] `TIFF -> JPG/PDF`
- [x] `PNG <-> WebP`
- [x] `SVG -> PNG`
- [x] `PNG -> SVG` через трассировку / векторизацию
- [x] `RAW -> JPG`
- [x] `RAW -> TIFF`
- [x] `PSD -> JPG/PNG/WebP`
- [x] `AI/EPS/SVG -> PNG/PDF`
- [x] `PNG -> ICO`
- [x] `SVG -> ICO`

Сейчас в converter-роуте реально работают `jpg`, `png`, `webp`, `bmp`, `svg`, `heic`, `tiff`,
`psd`, `ai`, `eps` и `raw`/camera-alias family (`dng`, `cr2`, `cr3`, `nef`, `arw`, `raf`, `rw2`,
`orf`, `pef`, `srw`).
Для них уже есть practical outputs в `JPG`, `PNG`, `WebP`, `AVIF`, traced `SVG`, `ICO`,
single-frame `TIFF` и single-page `PDF`.
PDF в этой итерации собирается как raster document без редактируемого текстового/векторного слоя, TIFF
идёт как single-frame RGBA image без multi-page контейнера и без переноса исходных metadata-блоков,
а `PNG -> SVG` закрывается через bitmap tracing, а не через semantic vector reconstruction.
На том же runtime это уже открывает `JPG/PNG -> AVIF`, `PNG -> SVG`, `PNG/SVG -> ICO`,
`PSD -> JPG/PNG/WebP`, `AI/EPS -> PNG/PDF`, `JPG/PNG/WebP/BMP/HEIC/SVG -> TIFF`,
`RAW -> TIFF`, `TIFF -> TIFF refresh`, `JPG/PNG -> PDF`, `TIFF -> PDF`, `SVG -> PDF`,
`HEIC -> PDF` и `RAW -> PDF`.
Поверх target-слоя уже заведены пресеты `Original`, `Web Balanced`, `Email Attachment` и `Thumbnail`,
которые централизованно управляют размерностью и базовым quality-profile до encode-шага.
Для `AI/EPS` есть честное ограничение: backend строит raster intake через server imaging stack,
поэтому на выходе это preview/conversion path, а не faithful Illustrator/PostScript interpreter.

#### 3.2 Частые Сценарии Для Офисных Форматов

- [x] `DOC -> DOCX`
- [x] `DOCX -> PDF`
- [x] `PDF -> DOCX`
- [x] `DOCX -> TXT`
- [x] `DOCX -> HTML`
- [x] `RTF <-> DOCX`
- [x] `ODT <-> DOCX`
- [x] `PDF -> JPG/PNG`
- [x] `JPG/PNG -> PDF`
- [x] `PDF -> TXT`
- [x] `PDF -> XLSX`
- [x] `PDF -> PPTX`
- [x] `DOCX/XLSX/PPTX -> PDF`
- [x] `XLSX -> CSV`
- [x] `CSV -> XLSX`
- [x] `PDF -> CSV/XLSX`
- [x] `XLSX -> PDF`
- [x] `ODS <-> XLSX`
- [x] `PPTX -> PDF`
- [x] `PDF -> PPTX`
- [x] `PPTX -> JPG/PNG`
- [x] `PPTX -> video`

Office-блок в итерации 3 теперь закрыт целиком через `OFFICE_CONVERT`: narrative exports покрывают
`DOC/DOCX/RTF/ODT/PDF`, spreadsheet exports покрывают `CSV/XLSX/ODS/PDF`, а slide exports покрывают
`PPTX/PDF` с preview/result artifacts для document, image и media outputs.
Ограничения остаются честно backend-driven: `PDF -> DOCX/TXT/XLSX/CSV/PPTX` строится из доступного
text/table/page layer без OCR, `PPTX -> PDF/JPG/PNG/MP4` идёт через rasterization/slideshow path,
а `DOCX <-> ODT/RTF` и `XLSX <-> ODS` сохраняют содержимое и sheet order, но не обещают полный
roundtrip стилей, embedded media, formulas и animations.

#### 3.3 Частые Сценарии Для Видео И Аудио

- [x] `MOV -> MP4`
- [x] `MKV -> MP4`
- [x] `AVI -> MP4`
- [x] `WebM -> MP4`
- [x] `MP4 -> WebM`
- [x] `video -> GIF`
- [x] `video -> MP3/WAV/AAC`
- [x] `4K -> 1080p/720p`
- [x] `H.265 -> H.264`
- [x] `H.264 -> AV1`
- [x] `WAV -> MP3`
- [x] `FLAC -> MP3`
- [x] `MP4 -> MP3`
- [x] `M4A <-> MP3`
- [x] `WAV <-> FLAC`

Media delivery-блок теперь закрывается через отдельный backend `MEDIA_CONVERT` route.
Сценарии `mov/mkv/avi/webm -> mp4`, `mp4 -> webm`, `mov/mkv/avi/webm/mp4 -> gif`,
`mov/mkv/avi/webm/mp4 -> mp3/wav/aac`, `wav -> mp3/flac`, `flac -> mp3/wav`, `m4a -> mp3`
и `mp3 -> m4a` идут через тот же upload/job/artifact lifecycle, что и остальные converter-семьи.
Workspace отдельно показывает container-target, resolved audio codec, bitrate, resolution и FPS,
а backend manifest возвращает source/result facts и предупреждения по потере аудио или смене delivery-профиля.

#### 3.4 Известные Ограничения Конвертации

- [x] Учитывать потери верстки в `PDF -> Word`
- [x] Обрабатывать сценарии, где для сканированных PDF сначала нужен OCR
- [x] Ясно объяснять ограничения CSV
- [x] Разделять контейнер, кодек, битрейт, разрешение и FPS

Эти ограничения теперь подняты на уровень backend matrix и converter workspace:
`PDF -> DOCX` заранее предупреждает про возможную потерю сложной вёрстки,
`PDF -> DOCX/TXT/XLSX/CSV/PPTX` для сканов честно требует OCR как отдельный следующий шаг,
`CSV` остаётся flattened single-sheet export без formulas/styles/comments,
а media conversion больше не прячет всё в одном выборе формата и явно разводит контейнер,
video/audio codec, bitrate, resolution и FPS как разные оси конфигурации.

### 4. Сжатие

- [x] Сжатие для разных групп файлов
- [x] Режим максимального практического уменьшения
- [x] Режим сжатия до целевого размера
- [x] Пользовательские настройки качества и лимитов

Compression в итерации 4 закрыт как отдельный backend-first route, а не как UI-надстройка над converter.
Новый `FILE_COMPRESS` job type принимает image/video/audio upload и сам решает size-first orchestration:
для image sources route перебирает quality/resize/target ladder поверх `IMAGE_CONVERT`,
а для video/audio sources reuse'ит `MEDIA_CONVERT` как candidate builder с bitrate/resolution/FPS control.
На фронте compression получил отдельный workspace с тремя режимами:
`Maximum reduction`, `Target size` и `Custom controls`.
Первый ищет минимальный practical result, второй останавливается на первом artifact, который укладывается
в заданный размер, а третий даёт ручной target, quality, bitrate и limit controls без смешивания этой задачи с обычной конвертацией.
Текущий production slice покрывает image family (`jpg`, `png`, `webp`, `bmp`, `svg`, `psd`, `ai`, `eps`, `heic`, `tiff`, `raw` family),
video family (`mp4`, `mov`, `mkv`, `avi`, `webm`) и audio family (`wav`, `flac`, `mp3`, `m4a`, `aac`).
В результате пользователь получает не только итоговый artifact, но и compression manifest с source/result facts,
warnings и attempt ladder, чтобы size-targeting оставался прозрачным.

### 5. PDF Toolkit

- [x] Переходы из совместимых сценариев в конвертацию в PDF
- [x] Переходы в сценарии просмотра и редактирования PDF
- [x] PDF workspace preview, search layer и result history для follow-up операций
- [x] `merge PDF`
- [x] `split PDF`
- [x] `rotate PDF`
- [x] `OCR`
- [x] `e-sign`
- [x] `visible stamp / signature preset`
- [x] `redact sensitive data`
- [x] `page extract/reorder`
- [x] `password protect / unlock`

PDF toolkit в итерации 5 теперь закрыт как отдельный backend-first route.
Workspace принимает либо прямой `PDF`, либо совместимый image/office source и сначала ведёт его в `PDF`
через уже существующие `IMAGE_CONVERT` / `OFFICE_CONVERT`, а затем переводит в page-aware workspace
с preview через `VIEWER_RESOLVE`.
Внутри route новый `PDF_TOOLKIT` job type закрывает `merge`, `split` по диапазонам, `rotate`,
`page extract/reorder`, OCR в `searchable PDF + TXT`, visible `e-sign / stamp`, term-based `redaction`
и `password protect / unlock` поверх того же upload/job/artifact lifecycle.
Ограничения здесь честно backend-driven: `e-sign` в этой итерации является видимым stamp-mark, а не
certificate-based digital signature; redaction пересобирает страницы как raster PDF, чтобы скрытый текст
не оставался под overlay; OCR по умолчанию работает на `tesseract` language profile `eng`, если в runtime
не подключены дополнительные traineddata.

### 6. Multi-Format Editor

- [ ] Полноценный редактор для нескольких текстовых форматов
- [ ] Знакомые шорткаты и их UI-аналог
- [ ] Форматно-специфичные помощники для Markdown, HTML, CSS, JS и других форматов
- [ ] Подсветка синтаксиса и live preview
- [ ] Безопасная валидация против вредоносных payload и очевидных уязвимостей
- [ ] Экспорт в plain text или готовые файлы
- [ ] Встроенное форматирование по аналогии с IDE

### 7. Dev Tools And Utils

- [ ] Кодировки и декодеры
- [ ] JWT-декодер и сопутствующие утилиты
- [ ] Генераторы хешей
- [ ] Инструменты для коротких ссылок
- [ ] Валидаторы текстовых форматов
- [ ] Дополнительные ежедневные дев-утилиты

## Модель Итераций

Репозиторий специально подготовлен так, чтобы в новом чате можно было дать задачу вида:

> "Сделай итерацию 1 для viewer, начни с таких-то форматов"

После этого агент должен суметь сам:

- создать новую ветку от свежего `main`
- реализовать нужный срез
- добавить тесты
- обновить roadmap и документацию
- закончить работу в состоянии, готовом к MR
