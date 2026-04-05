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
- home уже ведёт в два живых маршрута: `viewer` и первый `converter`
- есть shared imaging-слой для тяжёлых image decode-сценариев, который переиспользуют модули просмотра и конвертации
- подготовлены логотип и favicon для дальнейшего использования
- есть `docker compose`-окружение для локального старта
- задокументированы workflow-правила и roadmap для будущих итераций

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
- `JACK_PROCESSING_STORAGE_ROOT`
- `JACK_PROCESSING_MAX_UPLOAD_SIZE_BYTES`
- `JACK_PROCESSING_FFMPEG_EXECUTABLE`
- `JACK_PROCESSING_FFPROBE_EXECUTABLE`
- `JACK_PROCESSING_MEDIA_PREVIEW_TIMEOUT_SECONDS`

## Backend Processing Foundation

Первый backend-срез теперь уже поднимает базовый processing workflow:

- `POST /api/uploads` — сохраняет файл во временное backend storage
- `GET /api/uploads/{id}` — возвращает metadata по upload
- `POST /api/jobs` — создаёт processing job
- `GET /api/jobs/{id}` — возвращает статус, progress и artifacts
- `GET /api/jobs/{id}/artifacts/{artifactId}` — скачивает artifact
- `GET /api/capabilities/viewer`
- `GET /api/capabilities/converter`

В текущем срезе реально реализованы два job type:

- `UPLOAD_INTAKE_ANALYSIS` — подтверждает upload/storage/job flow и собирает manifest artifact
- `MEDIA_PREVIEW` — через `ffprobe` и `ffmpeg` собирает browser-friendly preview для legacy video/audio контейнеров и кладёт в artifacts и binary preview, и manifest

Для `MEDIA_PREVIEW` backend-окружение должно видеть исполняемые `ffmpeg` и `ffprobe`. Текущий `backend/Dockerfile` их ещё не включает, поэтому в контейнерном режиме эта фаза требует отдельной подготовки образа или внешних бинарей.

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
`heic` декодируется в клиенте через HEIC adapter, `tiff` проходит через TIFF decode-layer, а `raw` закрывается через RAW-family preview extraction (`raw`, `dng`, `cr2`, `cr3`, `nef`, `arw`, `raf`, `rw2`, `orf`, `pef`, `srw`).
Поверх preview viewer теперь поднимает metadata payload с summary/groups/editable draft, даёт EXIF/ICC inspection, экспорт metadata patch и image analysis tooling прямо в той же рабочей зоне.

#### 2.2 Office Documents

- [x] Foundation для document runtime, capability map и единого workspace-контракта
- [x] Первый рабочий preview и удобная навигация для `pdf`, `txt`, `csv`, `html`, `rtf`
- [x] Search layer, outline/table preview и format-specific summary для поддержанных форматов
- [x] OOXML adapters для `docx`, `xlsx`, `pptx` с preview поверх общего document contract
- [x] Полировка UX для поддержанных документов: quick actions, sheet tabs, slide focus и более ясный search flow
- [x] Legacy/open/database adapters для `doc`, `odt`, `xls`, `epub`, `db`, `sqlite`
- [ ] Частичное редактирование содержимого там, где формат это позволяет
- [x] Поддержка: `doc`, `docx`, `pdf`, `txt`, `rtf`, `odt`, `xls`, `xlsx`, `csv`, `pptx`, `html`, `epub`, `db`, `sqlite`

Document viewer теперь использует тот же registry/strategy foundation, что и image layer, но сводит
`pdf`, `txt`, `csv`, `html`, `rtf`, `doc`, `docx`, `odt`, `xls`, `xlsx`, `pptx`, `epub`, `db`,
`sqlite` к общему document contract:
`summary + search layer + layout mode + warnings`.
`pdf` открывается в browser embed и дополнительно поднимает page/search stats, `csv` получает table preview,
`html` рендерится через sandbox `srcdoc`, `rtf` и `doc` идут через text extraction path, `docx` и `odt`
собираются как structured document HTML, `xls` и `xlsx` как workbook/sheet preview, `pptx` как slide
text deck, `epub` как reflow reading layer, а `db/sqlite` как schema-aware database preview.
Поверх этого viewer уже даёт copy/download для extracted text, внятные active states для sheets/slides
и более читаемый search UX прямо внутри общего workspace.

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
workspace через legacy decode bridge на базе browser-side ffmpeg.wasm: viewer собирает
browser-playable preview container и затем отдаёт его в тот же video contract без отдельной ветки UI.
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
compatibility bridge через browser-side ffmpeg.wasm и затем сводятся к тому же audio contract.
Поверх foundation viewer даёт waveform preview, cover-art display, tag inspector с common/native
groups, timeline/volume/rate controls, loop и keyboard flow для быстрых playback-check сценариев.

#### 2.5 Другие Форматы

- [ ] Дополнительные viewer-сценарии для нишевых и служебных форматов

### 3. Конвертация Файлов

- [ ] Широкое покрытие конвертаций с фокусом на реальные пользовательские сценарии
- [ ] Конвертация изображений, документов, таблиц, презентаций, видео и аудио
- [ ] Трансформации контейнеров, кодеков, размеров и параметров качества

Первая browser-first итерация конвертера уже заведена в отдельный маршрут. Архитектура построена через
`scenario registry -> source decode strategy -> unified raster contract -> target encode strategy`, чтобы
новые форматы добавлялись через расширение capability-слоя, а не через логику внутри UI.
Следующий срез уже поднял и первый document-target: single-page PDF собирается поверх того же raster
contract, без отдельной ветки UI и без дублирования source decode-логики.
Текущий слой поверх этого добавляет preset-профили с централизованным resize/quality baseline, чтобы
дальнейшие batch- и delivery-сценарии не размазывались по UI-настройкам.
Следующим шагом target-слой был расширен до single-frame `TIFF`, чтобы browser-first runtime закрывал
не только delivery-форматы, но и archive/edit-friendly raster output.
Текущий проход добирает оставшийся image-surface: lazy adapters для `AVIF`, `ICO`, traced `SVG`,
`PSD` composite decode и illustration intake для `AI/EPS` через PDF-compatible render path либо
embedded preview extraction.

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
Для `AI/EPS` есть честное ограничение: browser-first adapter сначала пробует PDF-compatible слой,
а затем embedded preview. Полноценного PostScript/Illustrator interpreter в проекте пока нет.

#### 3.2 Частые Сценарии Для Офисных Форматов

- [ ] `DOC -> DOCX`
- [ ] `DOCX -> PDF`
- [ ] `PDF -> DOCX`
- [ ] `DOCX -> TXT`
- [ ] `DOCX -> HTML`
- [ ] `RTF <-> DOCX`
- [ ] `ODT <-> DOCX`
- [ ] `PDF -> JPG/PNG`
- [x] `JPG/PNG -> PDF`
- [ ] `PDF -> TXT`
- [ ] `PDF -> XLSX`
- [ ] `PDF -> PPTX`
- [ ] `DOCX/XLSX/PPTX -> PDF`
- [ ] `XLSX -> CSV`
- [ ] `CSV -> XLSX`
- [ ] `PDF -> CSV/XLSX`
- [ ] `XLSX -> PDF`
- [ ] `ODS <-> XLSX`
- [ ] `PPTX -> PDF`
- [ ] `PDF -> PPTX`
- [ ] `PPTX -> JPG/PNG`
- [ ] `PPTX -> video`

#### 3.3 Частые Сценарии Для Видео И Аудио

- [ ] `MOV -> MP4`
- [ ] `MKV -> MP4`
- [ ] `AVI -> MP4`
- [ ] `WebM -> MP4`
- [ ] `MP4 -> WebM`
- [ ] `video -> GIF`
- [ ] `video -> MP3/WAV/AAC`
- [ ] `4K -> 1080p/720p`
- [ ] `H.265 -> H.264`
- [ ] `H.264 -> AV1`
- [ ] `WAV -> MP3`
- [ ] `FLAC -> MP3`
- [ ] `MP4 -> MP3`
- [ ] `M4A <-> MP3`
- [ ] `WAV <-> FLAC`

#### 3.4 Известные Ограничения Конвертации

- [ ] Учитывать потери верстки в `PDF -> Word`
- [ ] Обрабатывать сценарии, где для сканированных PDF сначала нужен OCR
- [ ] Ясно объяснять ограничения CSV
- [ ] Разделять контейнер, кодек, битрейт, разрешение и FPS

### 4. Сжатие

- [ ] Сжатие для разных групп файлов
- [ ] Режим максимального практического уменьшения
- [ ] Режим сжатия до целевого размера
- [ ] Пользовательские настройки качества и лимитов

### 5. PDF Toolkit

- [ ] Переходы из совместимых сценариев в конвертацию в PDF
- [ ] Переходы в сценарии просмотра и редактирования PDF
- [ ] `merge PDF`
- [ ] `split PDF`
- [ ] `rotate PDF`
- [ ] `OCR`
- [ ] `e-sign`
- [ ] `redact sensitive data`
- [ ] `page extract/reorder`
- [ ] `password protect / unlock`

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
