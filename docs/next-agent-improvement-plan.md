# Jack: подробный план улучшений и передачи следующему агенту

Дата актуализации: 17 июля 2026 года.

## 1. Назначение документа

Это исполнимый план для продолжения работы над Jack без потери контекста. Его цель — довести существующие workspace-модули до предсказуемого, безопасного и расширяемого состояния, сохранив заданный визуальный язык продукта: светлый `soft industrial neumorphism`, тёплые sand-поверхности, глубокий teal и amber/coral-акценты.

Главные ожидаемые результаты:

- Markdown Viewer и Markdown Editor корректно поддерживают заявленный формат, включая таблицы, без визуальных артефактов.
- Все уже доступные форматы в viewer и editor имеют явный контракт поддержки, понятные ограничения и проверяемый сценарий работы.
- Тяжёлая обработка файлов, валидация, конвертация и извлечение структурированных данных живут в processing-platform, а не дублируются в браузере.
- Frontend разделён на небольшие feature-модули, отменяет устаревшие операции, не удерживает blob URL и не перерисовывает большие данные целиком.
- CI воспроизводим, визуальные baseline-проверки осмысленны, качество кода автоматически контролируется.
- Для рисковых путей есть защита от XSS, zip-bomb/архивных атак, path traversal, SSRF, неограниченного потребления памяти и утечки чужих артефактов.

Документ дополняет [quality-hardening-roadmap.md](quality-hardening-roadmap.md), но намеренно детализирует порядок, контракты, критерии готовности и способ передачи работы.

Статус: этапы 0–8 и финальный Definition of Done выполнены в
`feat/complete-hardening-roadmap`. Детальные исходные требования ниже сохранены как contract и
audit trail. Следующий агент не должен повторять completed refactors; новые работы следует
начинать только из остаточных product/deployment ограничений:

- account authentication/audit для настоящего multi-user deployment;
- shared object storage + distributed queue/lease перед горизонтальным масштабированием;
- certificate-based PDF signature вместо видимого stamp;
- дополнительные OCR language packs и fidelity improvements как отдельные capabilities;
- server-side schema apply и rollout smoke уже в целевом Kubernetes cluster.

## 2. Точка старта и проверенные факты

### 2.1. Состояние ветки

- Completion branch: `feat/complete-hardening-roadmap`, base `713e844` (`origin/main` на старте).
- Этапы A–H execution plan выполнены логическими conventional commits без переписывания истории.
- Актуальный список commits и remote SHA следует брать из Git/MR, а не копировать в этот
  долгоживущий документ.
- Перед новой задачей снова синхронизировать `main` и создать новую feature-ветку.

### 2.2. Что уже было сделано

- Пройдены начальные этапы hardening: безопаснее обработка файлов, контракт Markdown, основа CodeMirror и часть тестов.
- Введены адаптивные UI-примитивы и локальные шрифты; стиль не заменён на generic SaaS-интерфейс.
- Viewer отменяет устаревшие preview-запросы, хранит передачу editor → viewer в памяти, ограничивает SQLite preview и вынес преобразование preview-модели в отдельный модуль.
- Viewer session lifecycle вынесен из Vue в тестируемый controller: revision, abort, освобождение
  поздних и заменённых Blob URL и dispose теперь имеют единый контракт.
- Markdown RenderContract `jack-markdown-1.1.0` отдаёт один безопасный `previewDocument` для Viewer
  и Editor. GFM-таблицы получают семантические заголовки, доступную keyboard-scroll область,
  выравнивание и адаптивное оформление.
- Visual baseline закреплён на Playwright `1.61.1` в Linux-контейнере; CI сохраняет report,
  screenshots и traces при падении, а локальная команда использует тот же образ.
- Пройдены локально: frontend type-check, ESLint, Prettier, unit tests, build, `npm audit --omit=dev --audit-level=high`, Playwright; backend Gradle tests.

### 2.3. Состояние GitHub Actions, которое нельзя игнорировать

Подраздел ниже — историческая диагностика исходного run. Completion branch должен подтвердить
текущее состояние новым CI run после push; старый failure не является известным дефектом.

Run [29449018690](https://github.com/MattoYuzuru/Jack/actions/runs/29449018690) был исходным сигналом и завершился ошибкой в `Verify → Run accessibility and responsive E2E tests`.

Факты из лога:

- прошли 80 тестов;
- упали ровно 5 тестов `home visual baseline` из `frontend/e2e/workspaces.spec.ts`;
- это снимки одной главной страницы при ширинах 1440, 1024, 768, 390 и 320 px;
- ошибки — pixel mismatch / различающаяся высота изображения, а не нарушение accessibility, overflow или пользовательского сценария;
- setup, lint, format, unit tests и Playwright installation в CI завершились успешно;
- у ветки нет PR, поэтому GitHub-контекст PR и комментарии не создавались.

Локальное воспроизведение в официальном Linux/Playwright image подтвердило различие метрик и
растеризации шрифта между macOS и Linux. Все пять desktop/mobile результатов были просмотрены
вручную; отдельно исправлен реальный перенос точки в mobile hero. Baselines обновлены только в
закреплённой Linux-среде. Следующее подтверждение должно прийти от CI после публикации ветки;
при новом падении workflow уже сохранит диагностические artifacts.

## 3. Обязательные правила выполнения

1. Перед каждым новым самостоятельным блоком синхронизировать remote, убедиться, что `main` равен `origin/main`, и начинать feature-ветку от свежего `main`, если работа не является прямым продолжением этой ветки.
2. Делать маленькие conventional commits, один связный результат на commit. Примеры сообщений приведены в разделе 14.
3. Не откатывать и не форматировать массово несвязанные пользовательские изменения.
4. Нетипичную и критичную логику комментировать по-русски: объяснять ограничение, модель угрозы или причину решения, а не пересказывать строку кода.
5. Новую обработку файлов сначала оценивать как backend-кандидата. Для неё в первую очередь переиспользовать `upload/job/artifact/capability`, `VIEWER_RESOLVE`, `DOCUMENT_PREVIEW`, `MEDIA_PREVIEW`, `IMAGE_CONVERT`, `METADATA_EXPORT` и capabilities platform API.
6. Во frontend оставлять presentation, interaction, viewport/playback, локальное UI-состояние и мгновенную browser-native обратную связь.
7. Не применять `v-html`, raw HTML Markdown, URL из непроверенного файла или `allow-same-origin` sandbox без явной модели безопасности и теста на обход.
8. После каждого этапа запускать только релевантный, но достаточный набор форматирования, линтеров, unit/integration/E2E и security-проверок. Перед handoff обновлять roadmap и этот документ при изменении scope.

## 4. Архитектурный ориентир

### 4.1. Границы слоёв

| Слой                     | Ответственность                                                                     | Не должен делать                                                            |
| ------------------------ | ----------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| Vue view/component       | Раскладка, доступность, пользовательские действия, отображение конечной модели      | Парсить большие файлы, владеть сетевой политикой, повторять правила backend |
| Composable/store feature | Жизненный цикл запроса, отмена, revision token, состояние загрузки/ошибки           | Содержать большой шаблон и CSS страницы                                     |
| API client               | Типизированный transport, abort signal, разбор ошибок                               | Принимать продуктовые решения о формате                                     |
| Backend resolve/preview  | Определение безопасной стратегии preview, лимиты, artifact reuse, capability policy | Формировать конкретную Vue-разметку                                         |
| Worker/job               | Тяжёлый parsing/conversion/extraction, retries, quota, очистка временных файлов     | Доверять расширению файла или пользовательскому имени                       |
| Shared contract          | DTO, status codes, feature capability, error code, limits                           | Содержать framework-specific code                                           |

### 4.2. Целевой жизненный цикл файла

```text
Выбор файла → intake (magic bytes + лимиты) → upload/artifact
    → viewer resolve (capability + preview strategy)
    → короткий preview/job или browser-native renderer
    → typed preview model / paged range
    → компонент формата
    → revoke/abort/cleanup при замене, уходе со страницы или ошибке
```

Любое звено должно получать `requestId`/`revision`, а результат разрешено применить только если он всё ещё соответствует текущему файлу и текущему экрану.

### 4.3. Единый контракт ошибок

Backend и frontend должны различать как минимум:

- `UNSUPPORTED_FORMAT` — формат не поддержан данным workspace;
- `INVALID_FILE` — содержимое не соответствует заявленному типу;
- `FILE_TOO_LARGE` / `PREVIEW_LIMIT_EXCEEDED` — файл принят или отклонён согласно policy, но preview ограничен;
- `PREVIEW_NOT_READY` — допустимо ждать job/artifact;
- `PREVIEW_EXPIRED` / `ACCESS_DENIED` — artifact недоступен владельцу или истёк;
- `PROCESSING_FAILED` — безопасное публичное сообщение плюс correlation id;
- `REQUEST_CANCELLED` — штатная отмена без тревожного UI;
- `RATE_LIMITED` — пользователь видит время/действие для повторной попытки.

Не выводить в UI stack trace, путь к файлу, ключи, детали инфраструктуры или сырые ошибки сторонних парсеров.

## 5. Этап 0 — сделать CI визуально воспроизводимым

Этот этап блокирует обновление visual baseline и должен идти первым.

### 5.1. Диагностика

1. Скачать из failed run `actual`, `expected` и `diff` screenshots либо включить их upload как action artifacts на ошибке.
2. Открыть все пять diff-изображений и вручную классифицировать разницу: ожидаемое изменение layout, другой font fallback, загрузка шрифта, scrollbar, viewport height, анимация или регрессия.
3. Запустить ту же версию Chromium в Linux-окружении CI. Предпочтительно — отдельный Docker image, совпадающий с Playwright image/версией в workflow, а не локальный macOS browser.
4. Проверить в trace/network, что `@font-face` загружается до screenshot и не происходит fallback. Проверить `font-display`, MIME type, веса, variable-font axes и путь ассета после production build.
5. Сверить параметры Playwright: браузер, device scale factor, locale, timezone, color scheme, reduced motion, viewport, политику анимаций и scrollbar.

### 5.2. Решение baseline-политики

Выбрать и зафиксировать один путь до обновления snapshot:

- **Предпочтительный:** snapshots генерируются и проверяются в закреплённом Linux/Playwright окружении. Локальная команда запускает тот же контейнер.
- **Допустимый:** отдельные platform-specific snapshots, если технически невозможно унифицировать окружение. Для каждого snapshot явно обозначить платформу, не подменять один другим.
- **Нежелательный:** снижать threshold так, чтобы скрыть реальное изменение layout. Допускается только для доказанного rasterization noise и с обоснованием в тесте/документации.

Не принимать команду обновления snapshots как достаточное доказательство. До commit проверить все desktop и mobile изображения по дизайну: крупные блоки, сетка, hero, навигация, контраст, отсутствие обрезки и корректность шрифтов.

### 5.3. Изменения в тестовой инфраструктуре

- В `playwright.config` выключить или стабилизировать motion для snapshot tests, установить детерминированные `locale`, `timezone`, `colorScheme` и viewport/device scale factor.
- Добавить ожидание готовности шрифтов: `await document.fonts.ready` до снимка, если оно отсутствует.
- В CI загрузить screenshot artifacts при fail независимо от последующих шагов.
- Указать в README/Testing, где и как обновляются baselines, и запретить неосмотренное массовое обновление.
- Если homepage динамичен, подменить время, случайные идентификаторы и внешние данные фиксированными fixtures.
- Оставить отдельные accessibility и responsive-overflow assertions, чтобы visual baseline не был единственной защитой адаптивности.

### 5.4. Критерий готовности

- Пять baseline снимков получены в целевой среде и визуально проверены.
- `npm run test:e2e` в совпадающем контейнере стабильно проходит два последовательных запуска.
- Failed run либо повторён с успехом, либо следующая CI job зелёная; artifacts доступны при следующем падении.
- В commit включены только осознанные baseline/config/docs изменения.

## 6. Этап 1 — закрепить frontend foundation и границы модулей

### 6.1. Инвентаризация до рефакторинга

Сначала составить небольшую таблицу «файл → ответственность → размер → зависимости → тест». Выделить:

- страницы, одновременно владеющие upload, запросами, preview-model, toolbar, инспектором и format-specific markup;
- повторяемую логику `loading/error/empty/progress/cancel`;
- все места `URL.createObjectURL`, таймеров, event listener и AbortController;
- дублирующиеся MIME/extension mappings и UI labels;
- компоненты с неявными `any`, большими inline type definitions и пропсами, меняющими смысл в зависимости от формата.

Не дробить файл только ради числа строк: модуль выделяется, если у него появляется ясный контракт и независимый тест.

### 6.2. Предлагаемая структура

Адаптировать имена к фактической структуре проекта, но стремиться к следующей форме:

```text
frontend/src/
  features/
    viewer/
      api/
      components/
      composables/
      formatters/
      renderers/
      types/
      viewer-capabilities.ts
    editor/
      api/
      components/
      composables/
      extensions/
      types/
    processing/
      api/
      task-lifecycle.ts
  shared/
    components/
    composables/
    format/
    ui/
```

Цель — не переносить всё одним огромным refactor commit. В каждом шаге переносить один связный кусок с unchanged behaviour, затем добавлять нужную функцию отдельным commit.

### 6.3. Общие примитивы

Создать или довести до единого вида:

- `useAbortableTask` — один controller на актуальную операцию, явная отмена, различение cancel/error, cleanup on unmount;
- `useObjectUrl` — создаёт URL только для выбранного blob, отзывает прошлый URL и URL при unmount;
- `useRequestRevision` — защищает UI от ответа предыдущего файла/настройки;
- `WorkspaceAsyncState` — typed state `idle | selecting | uploading | resolving | processing | ready | error`;
- `FormatCapability` — пользовательское имя, MIME/extension aliases, preview mode, editor mode, максимальные размеры, backend capability;
- единые `WorkspaceErrorPanel`, `WorkspaceEmptyState`, `WorkspaceProgress`, keyboard-focus и toast patterns.

Не превращать shared в «свалку». Примитив становится общим лишь после второго доказанного потребителя.

### 6.4. UI foundation и производительность

- Продолжать использовать существующие CSS custom properties, radii, shadows, typography и palette из `src/styles.css`.
- Новые токены добавлять на уровне foundation с семантическим именем (`--surface-raised`, `--focus-ring`), не копировать числовые значения по компонентам.
- Избавиться от тяжёлых inline styles и viewport-specific JS, которые можно заменить CSS container/media queries.
- Lazy-load renderer/библиотеку только для выбранного формата: PDF, spreadsheet, SQLite, syntax highlighter, audio waveform и т. п.
- Задать budget для критичного initial JS и проверить production chunk report до/после крупных библиотек.
- Для mobile не прятать важные действия навсегда: toolbar превращается в компактный, доступный overflow menu, inspector — в sheet/drawer.

### 6.5. Критерий готовности

- У Viewer и Editor нет монолитного компонента, одновременно владеющего transport, lifecycle и разметкой всех форматов.
- Все create/revoke URL, abort и stale-response paths покрыты unit tests.
- Desktop, tablet и mobile проверены E2E хотя бы на одном основном сценарии каждого workspace.
- Старый и новый layout сохраняют visual language Jack и не добавляют случайных цветов/теней.

## 7. Этап 2 — Viewer: полный и безопасный contract форматов

### 7.1. Декомпозиция Viewer

Разделить `ViewerWorkspaceView` по ответственностям:

- `ViewerWorkspaceShell` — layout, навигационная плитка, responsive regions;
- `ViewerIntake` — выбор/drag-and-drop, мгновенная UI-валидация и доступность;
- `ViewerToolbar` — только пользовательские действия и доступные labels;
- `ViewerStage` — переключение состояний и renderer slot;
- `ViewerInspector` — метаданные, ограничения preview, download/share actions;
- `ViewerStatusPanel` — progress, recoverable error, retry/cancel;
- format-specific renderer components — изолированная разметка и viewport behavior;
- `useViewerSession` — lifecycle: intake → resolve → job/artifact → ready/cleanup;
- `preview-presentation` — уже начатый mapper «backend preview model → presentation model», расширять без DOM-кода.

Renderer получает готовую нормализованную модель и callbacks; не делает новый resolve запрос за спиной родителя. Root session — единственная точка владения abort/revision/object URL.

### 7.2. Универсальные правила renderer'ов

Каждый renderer обязан:

- принимать typed discriminated union, а не `unknown` и набор optional props;
- иметь `loading`, `empty`, `partial/truncated`, `error` и `unsupported` presentation;
- не делать полный parse больших файлов на main thread;
- оставаться keyboard-accessible: logical tab order, visible focus, именованные icon buttons, управление масштабом/страницами с клавиатуры;
- не позволять untrusted content выполнять код, получать родительский origin или инициировать произвольную навигацию;
- корректно освобождать listeners, workers, timers, object URLs и media instances;
- иметь хотя бы unit/contract test и E2E smoke fixture.

### 7.3. Markdown: целевой стандарт

Поддерживать документированный безопасный поднабор GFM:

- headings, paragraphs, emphasis, strong, strike-through, blockquotes, ordered/unordered/task lists;
- fenced и inline code, language label и безопасный highlighting;
- links с безопасными protocol rules (`https`, `http`, `mailto` при необходимости), external links с `rel="noopener noreferrer"`;
- images только через контролируемый URL/artifact contract, с alt и bounded loading;
- horizontal rules;
- GFM tables: header/body, alignment, horizontal scroll container на mobile, читаемые borders/row hover, long-cell wrapping и доступная семантика `table/th/scope`;
- hard/soft line breaks и корректное экранирование HTML.

Не включать raw HTML в Markdown. Math, Mermaid, HTML embeds, footnotes и custom directives вводить только как отдельные возможности после threat model:

- math — санитизированный renderer без исполнения arbitrary TeX/HTML;
- Mermaid — worker/серверный рендер с sanitization и строгими limits;
- embeds — allowlist origin и iframe sandbox;
- directive — parser token, а не пользовательский HTML.

Для Markdown создать fixture matrix: пустой файл, GFM table with alignment, nested lists, malicious `javascript:` link, raw HTML, oversized line, unicode/RTL, image/link, code fence и malformed syntax. Snapshot-тестировать таблицы в desktop/mobile отдельно от home page.

### 7.4. Plain text, code и structured text

- Определять charset с явным fallback и показывать пользователю выбранную encoding/возможность изменить её для допустимого списка.
- Для больших файлов использовать server-side range/line window либо virtualized viewport; не читать гигабайты в `textarea`/DOM.
- Ограничить максимальную длину строки, число отображаемых строк и размер highlighted segment; предупредить, если preview усечён.
- Syntax highlighting выполнять только для поддерживаемых языков, лениво; raw text доступен всегда.
- Сохранять line numbers, copy/download, поиск по доступному диапазону и ссылку «скачать полный файл».

### 7.5. CSV и TSV

Browser не должен держать все строки большого файла. Нужен backend descriptor и range protocol, предпочтительно как расширение `VIEWER_RESOLVE`/artifact preview:

```ts
type DelimitedTablePreview = {
  kind: "delimited-table";
  artifactId: string;
  delimiter: "," | "\t" | ";" | "|";
  encoding: string;
  hasHeader: boolean;
  columns: Array<{
    id: string;
    label: string;
    inferredType: "text" | "number" | "date" | "boolean";
  }>;
  rowCount: number | null;
  truncated: boolean;
  range: { maxRows: number; maxCells: number; maxCellBytes: number };
};
```

Требования backend:

- magic/encoding/UTF BOM/delimiter detection с детерминированным fallback;
- RFC-aware parsing quoted delimiters, CRLF, empty final field, malformed row reporting;
- лимиты row width, cell size, column count, total parsed bytes и request page size;
- безопасная пагинация `offset/limit` либо cursor; если filter/sort не поддержаны, UI не имитирует работу на неполном наборе;
- формировать metadata и reusable artifact/job, а не повторно парсить файл при каждом scroll;
- CSV formula injection: при export/copy в spreadsheet экранировать опасные ячейки или явно предупреждать; не исполнять формулы.

Требования frontend:

- виртуализированная таблица, sticky header при наличии достаточной ширины;
- horizontal scroll с сохраняемым header; tooltips/expand action для обрезанных ячеек;
- skeleton rows и retry только для следующей страницы;
- доступная таблица и fallback на «показать первые N строк» на узком экране;
- ясно показать delimiter, encoding, headers, доступный range и факт усечения.

### 7.6. XLSX, XLS и ODS

Планировать отдельный spreadsheet preview, не пытаться преобразовать любую книгу в CSV на клиенте.

1. Backend извлекает ограниченный manifest: sheets, visibility, row/column bounds, merges, freeze panes, formula/cached-value state, preview limits.
2. Range endpoint/job выдаёт конкретный лист и прямоугольный диапазон с bounds.
3. Frontend лениво загружает листы и диапазоны, виртуализирует grid, не рендерит все ячейки.
4. Первая версия поддерживает values, data types, базовые number/date formats, merges, column widths и freeze panes. Условное форматирование, charts, macros, external links и формулы не исполняются.
5. Макро-файлы и external data connections получают явный статус «не выполняется».

Fixtures: multiple sheets, merged cells, hidden sheet, very wide sheet, 100k rows, dates/timezones, formula error, xlsm, corrupt workbook, decompression bomb boundary.

### 7.7. SQLite и другие базы

Уже ограничены read-only preview queries; продолжить:

- разрешать только безопасный read-only subset либо не принимать произвольный SQL в первой итерации;
- запрещать PRAGMA/ATTACH/load_extension/write statements и multiple statements;
- лимитировать execution time, returned rows/cells, byte size, depth/complexity и число открытых cursors;
- страницу таблиц и columns получать из metadata; rows — курсорной пагинацией;
- BLOB показывать как размер/type/download action, не пытаться сериализовать всё в JSON;
- использовать isolated temp copy/read-only connection, закрывать connection при abort;
- не делать дорогостоящий `COUNT(*)` для каждой таблицы, если metadata не содержит дешёвую оценку.

Нужны security tests: semicolon injection, `ATTACH`, malformed DB, very large DB, huge BLOB, long query, cancellation и ownership другого artifact.

### 7.8. PDF, EPUB и документы

- PDF: безопасный viewer с page virtualization, page counter, zoom/reset, rotate, text selection/search, понятный download. Ограничить глубину страниц/рендер-память; открыть внешнюю ссылку только с `noopener`.
- Если используется iframe/viewer third-party: строгий sandbox и typed `postMessage` allowlist (`origin`, `source`, schema). Не давать `allow-same-origin` вместе с правами, позволяющими выполнить untrusted код.
- EPUB/архивные документы: backend проверяет zip entries, суммарный unpacked size, compression ratio, nesting, path traversal и mime; UI лениво отображает главы и сообщает о неподдержанных embedded content.
- Office/PDF conversion опирается на backend artifact/job, не на случайный browser parser; конечный preview должен показывать статус, что он partial/rendered, если верность не гарантируется.

### 7.9. Images, media, 3D и miscellaneous

- Images: EXIF orientation, dimensions, color profile с безопасным fallback, alpha/animation notice, pixel count/decoded-memory limit, zoom/pan keyboard controls; strip sensitive metadata only по явному продуктового решению.
- Video/audio: `preload="metadata"`, ограниченный seek, captions/subtitles при поддержке, poster/waveform только после budget decision; корректная отмена/cleanup media source.
- SVG: относить к активному содержимому — sanitise server-side или render as inert image; не вставлять SVG markup напрямую в DOM.
- Архивы: показывать bounded manifest без extraction browser-side; не переходить по внутренним путям.
- Необработанные/экзотические форматы имеют честный fallback: metadata + download, а не сломанный пустой viewer.

### 7.10. Критерий готовности Viewer

- Матрица форматов в документации указывает `full preview`, `bounded preview`, `metadata/download only`, `not supported` и лимиты.
- GFM tables проходят unit, visual и mobile E2E tests.
- Быстрая последовательность A → B → A не оставляет содержимое B, не выдаёт ошибку cancellation и не протекает object URL.
- Большие CSV/XLSX/SQLite не делают full-file parse и не блокируют main thread.
- Неподдержанный или небезопасный файл завершает flow человеческим состоянием, без white screen и stack trace.

## 8. Этап 3 — Editor: полноценный, быстрый и предсказуемый

### 8.1. Структура и состояние

Разделить Editor на:

- `EditorWorkspaceShell` и responsive layout;
- `EditorDocumentSession` — файл, dirty state, revision, save/export lifecycle;
- `EditorToolbar` и command registry;
- `CodeMirrorEditor` adapter — setup/teardown/extensions;
- `EditorPreviewPane` — markdown/html/text preview contract;
- `EditorDiagnosticsPanel` — parser/server diagnostics;
- `EditorPreferences` — theme/font/wrap только UI preferences;
- `useEditorPreview` — debounce, abort, result revision guard;
- `useEditorPersistence` — ограниченное, versioned, opt-in restoration только безопасных drafts.

Команды не должны жить как набор `if (format === ...)` внутри page component. Ввести capability-driven registry: command имеет id, label, shortcut, видимость, enabled predicate и handler. Каждая команда тестируется отдельно.

### 8.2. Markdown Editor

- WYSIWYG не нужен как скрытая цель: code editor + безопасный side-by-side preview достаточно прозрачны.
- Парсить Markdown тем же контрактом/версией renderer, что и Viewer, иначе таблица может выглядеть по-разному.
- Табличные команды: вставить table template, добавить/удалить row/column, alignment только если manipulation работает через AST/token ranges, а не опасные regex по всему тексту.
- Preview обновлять debounce-ом, отменять прошлый запрос и показывать «обновляется» без прыжка layout.
- При переключении preview не терять cursor, scroll и unsaved text; при изменённом файле предупреждать перед replace/close.

### 8.3. Остальные текстовые форматы

- Определить format matrix: plaintext, JSON, YAML, XML, HTML (редактирование возможно, исполнение/preview строго sandboxed), CSV/TSV (табличная форма только с однозначными ограничениями), source code.
- Diagnostics делать parser-backed: JSON/YAML/XML parse errors, line/column, code, human message. Не использовать regex как валидатор формата.
- Auto-format только для поддерживаемых форматов и только по явной команде; перед изменением крупных/сломанных файлов показывать recovery action.
- Подсветка синтаксиса, indent, bracket matching, search/replace, line wrapping, font scale и shortcuts должны сохранять доступность, не перехватывать системные команды без необходимости.
- Лимит интерактивного editing: для больших файлов предложить read-only/windowed viewer/download, а не заморозку вкладки.

### 8.4. Безопасность editor preview

- HTML preview: отдельный sandboxed iframe, строгий CSP, без same-origin, без top navigation и без доступа к app storage/cookies.
- Markdown HTML не рендерить raw; любая sanitization policy едина с Viewer и покрыта XSS corpus.
- Внешние ресурсы и images: allowlist/mediated artifact URL, policy on remote loading. По умолчанию не позволять документу генерировать произвольные network requests.
- Не хранить содержимое чувствительных документов в `localStorage`/`sessionStorage` автоматически. Persistence должен быть отключаемым, ограниченным по размеру и очищаемым.

### 8.5. Критерий готовности Editor

- Markdown tables, code blocks, links, malformed input и XSS corpus одинаково безопасно отображаются в Viewer и Editor preview.
- Быстрый набор текста не приводит к перескоку preview на старую revision.
- Переключение файла, закрытие вкладки и unmount отменяют pending preview.
- Есть E2E: ввод → table command → preview → export/open in viewer; mobile layout; keyboard-only flow; unsaved changes guard.

## 9. Этап 4 — processing-platform и backend reliability

### 9.1. Ownership и доступ к artifacts

Текущий artifact не должен быть доступен только по угадываемому id.

- Выбрать модель: authenticated user или короткоживущая anonymous session, привязанная к подписанной cookie/token.
- Artifact/job хранит owner/session, creation/expiry, parent upload, capability и audit correlation id.
- Каждый `resolve`, status, range, preview и download проверяет owner/session/capability до работы.
- Не сообщать существование чужого artifact: одинаковое безопасное `not found/expired` сообщение.
- Для share links использовать отдельно подписанный, ограниченный scope token с TTL/revocation, а не основной artifact id.

### 9.2. Хранилище, очередь и lifecycle

- Эфемерное состояние jobs/artifacts перенести с in-memory на PostgreSQL/выбранное durable storage. Миграция, indexes, TTL cleanup и rollback документированы.
- Тяжёлые jobs выполнять worker-ами с bounded concurrency, timeout, retry policy только для transient ошибок и идемпотентным key.
- Отделить intake/upload от processing: upload возвращает durable reference, клиент polling/SSE получает status и может cancel.
- Reuse разрешать только для artifact того же owner и того же безопасного source hash/capability/policy version.
- Удалять temp files/unfinished uploads/expired artifacts фоново и при startup reconciliation; метрики cleanup должны показывать backlog.

### 9.3. Intake и файловая безопасность

- Allowlist по magic bytes + content signature + размеру, extension/MIME — лишь подсказка для UX.
- Streaming upload и streaming download; не собирать большой payload целиком в heap.
- Лимиты до allocation: total upload bytes, decoded pixels, page count, archive entries, unpacked bytes, compression ratio, nested archive depth, rows/cells и processing time.
- Нормализовать filename для отображения; storage path строить исключительно из server-side id. Запретить traversal, control characters и опасные unicode-confusables в важных путях.
- Endpoint download поддерживает безопасный `Content-Disposition`, content type, `nosniff`, byte ranges с лимитами и не раскрывает filesystem path.

### 9.4. API contract и наблюдаемость

- Описать OpenAPI/typed shared DTO либо генерируемый client; не дублировать enum formats в трёх местах.
- Все long-running ответы содержат status, progress phase, retryability, limits/partial flag и correlation id.
- Логи структурированные: request/job/artifact hash, capability, elapsed ms, bytes, outcome; без document content, auth token и персональных метаданных.
- Метрики: upload rejection by reason, queue depth, preview latency p50/p95, error code, cancellation rate, artifact cleanup lag, memory/CPU workers.
- Добавить health/readiness checks, graceful shutdown для queue и limits на body/connections/timeouts.

### 9.5. Критерий готовности

- Два anonymous/authenticated контекста не могут прочитать/resolve/download artifact друг друга.
- Large and malicious fixtures отвергаются до опасной работы и оставляют cleanup state корректным.
- Перезапуск API/worker не теряет финальные jobs, не дублирует side effects и не оставляет download на несуществующий temp file.
- Контракт preview range документирован и покрыт integration tests.

## 10. Этап 5 — привести остальные workspace-функции к тому же уровню

### 10.1. Общий контракт задач

Converter, Compression, PDF и тяжёлые DevTools-операции используют один lifecycle:

```text
draft settings → validate → submit revision N → queued/running → artifact ready
                         ↘ cancel / superseded by N+1 / failed with retry guidance
```

Для каждого workspace:

- один current task controller и revision token;
- кнопка cancel там, где операция длится заметно;
- повторные клики не запускают гонку jobs;
- result URL/artifacts очищаются при новом запуске/уходе;
- UI отделяет error input, processing failure, cancelled и expired artifact;
- download проверяет, что artifact принадлежит текущей сессии.

### 10.2. Converter

- Составить matrix вход → выход → backend engine → лимиты → наличие metadata/alpha/orientation/animation → fixture.
- Перед job валидировать реальную возможность conversion через `IMAGE_CONVERT`/platform capability, а не только extension.
- Для изображений сохранять или явно обозначать loss of alpha, color profile, EXIF orientation, animation frames и quality setting.
- Для multi-page/multi-frame контента определить outcome: all pages, first page или explicit rejection. Не делать молчаливую потерю данных.
- Хранить conversion settings в нормализованном DTO и включать их в artifact reuse key.
- Добавить verification tests: magic bytes output, decodable output, expected dimensions/orientation, bounded file size, cancellation и corrupt input.

### 10.3. Compression

- Разделить deterministic target-size/quality policy и UI slider; backend возвращает actual size, ratio, quality attempts и reason, если target недостижим.
- Для iterative compression установить max attempts, time/CPU/memory budget и отмену.
- Preview «до/после» не должен загрузить оба полноразмерных изображения без лимита decoded memory; использовать thumbnails/tiles.
- Не подменять формат без явно видимого согласия пользователя; отдельно показать alpha/metadata/animation loss.
- Проверить повторные запуски с разными настройками: только последний результат может стать активным.

### 10.4. PDF workspace

- Разделить model операции: merge, split, reorder, rotate, extract, watermark, redact, secure/permissions, sign/visible stamp. Не называть косметический stamp «криптографической подписью».
- Перед применением показывать список файлов/страниц/порядок, thumbnail/bounded page preview и понятный irreversible warning для redaction.
- Реализовать redaction как удаление/затирание содержания в output, а не overlay rectangle; добавить proof test, что извлечь redacted text из output нельзя.
- Для encrypted PDF явно поддержать password input в памяти, без persistence/logging; обработать wrong password без раскрытия деталей.
- Merge/split защищать лимитами количества файлов, страниц и суммарного размера; temp files cleanup обязателен.
- Для download фиксировать display filename, MIME, artifact ownership и expiration.

### 10.5. DevTools и metadata-инструменты

- Хеширование большого файла запускать worker/server-stream, показывать progress/cancel, сверять тестовыми known hashes; не блокировать UI.
- Проверку JSON/YAML/XML сделать parser-backed, показывать line/column и recovery action; format/minify не портят newline/encoding без явного режима.
- Metadata export использует `METADATA_EXPORT`, sanitises sensitive fields и сообщает, что именно раскрывается пользователю.
- Секреты, tokens, вставленный код и содержимое файлов не сохранять в local storage/analytics/logs по умолчанию.
- Добавить copy actions с accessible confirmation и защитой от accidental copy в неподходящий target только на уровне UX, не как security boundary.

### 10.6. Критерий готовности workspaces

- Для каждого видимого действия есть хотя бы один success, invalid input, cancellation, network/backend error и mobile test.
- Ни один workspace не запускает тяжёлую операцию без limit/cancel/cleanup policy.
- Понятно, какие операции browser-native, а какие проходят через backend capability.

## 11. Этап 6 — доступность, UX и скорость как product-quality требования

### 11.1. Accessibility

- Выполнять WCAG 2.2 AA как базовую цель: contrast, focus visible, keyboard navigation, semantic controls, labels, errors, live region для progress и status.
- Проверять tab order в workspace shell, toolbar, table/grid, dialogs, mobile drawer и dropzone.
- Все иконки-действия получают accessible name; чисто декоративные SVG скрываются от assistive tech.
- Dialog/sheet: focus trap, initial focus, Escape close, вернуть фокус initiator, не оставлять background interactive.
- Не кодировать смысл только цветом; status/limits показывать текстом/иконкой.
- Таблицы и виртуализированные grid не должны ложно заявлять неверное число строк/колонок screen reader-у.

### 11.2. Responsive UX

- Снимки/тесты минимум для 320, 390, 768, 1024, 1440 px. На 320 не допускать horizontal overflow всей страницы; горизонтальный scroll приемлем только внутри data table/code stage с явным affordance.
- Сохранять крупные характерные product tiles и иерархию Jack; в mobile менять композицию, а не сжимать desktop до нечитаемости.
- Toolbar actions имеют приоритет: primary action, cancel/download, overflow. Не прятать destructive/critical action без подтверждения.
- Inspector на mobile открывается как accessible sheet, на desktop остаётся частью workspace без потери stage width.

### 11.3. Performance budgets

- Зафиксировать начальные метрики: production JS/CSS per route, LCP/INP surrogate in lab, number of DOM nodes for large table, preview time for fixture tiers.
- Не импортировать PDF/spreadsheet/highlighter libraries в initial bundle; проверить code splitting через build artifact/analyzer.
- Длинные списки/страницы — virtualization; тяжёлые client parse — worker только если это действительно browser-native и bounded.
- Скелетоны должны иметь стабильную высоту, чтобы не создавать CLS.
- Добавить test/telemetry guard от stale update after unmount и preview race.

## 12. Этап 7 — security and supply-chain hardening

### 12.1. Угрозы, которые требуется закрыть тестами

- XSS в Markdown, HTML preview, SVG, filenames, metadata и error messages;
- SSRF/remote resource loading из документов;
- IDOR на jobs/artifacts/download/range endpoints;
- path traversal в archive/storage/download filename;
- zip bomb, oversized decompression, image pixel bomb, malformed parser inputs;
- SQL injection/SQLite escape/resource exhaustion;
- open redirect/clickjacking/content-sniffing;
- upload/download memory exhaustion, slow client, unbounded range;
- dependency vulnerabilities, leaked secrets in repo/logs/container image.

### 12.2. Защитные меры

- CSP с принципом least privilege, `frame-ancestors`, `X-Content-Type-Options: nosniff`, `Referrer-Policy`, корректная `Permissions-Policy`; отдельно протестировать, что они не ломают sandbox preview.
- CORS allowlist, CSRF policy для cookie auth, rate limits на upload/resolve/job/download, body/connection/read timeouts.
- Container/runtime: non-root user, read-only filesystem где возможно, минимальные base images, pinned versions/digests, no shell/debug tooling in runtime, health checks.
- SBOM хранить как CI artifact и policy: high/critical vulnerabilities triage с owner/exception/expiry; не «гасить» audit без причины.
- Secret scanning/pre-commit/CI check, `.env.example` без реальных ключей, review of logs.
- Fuzz/property tests для parser boundary или хотя бы curated malicious corpus до полноформатного fuzzing.

### 12.3. Критерий готовности

- Threat-model таблица «вектор → защита → автоматический тест → owner» лежит в docs.
- Security headers и container policy проверены integration/smoke tests.
- CI не содержит secrets в артефактах/logs и не допускает известный high/critical risk без явного зафиксированного исключения.

## 13. Этап 8 — стратегия тестирования, code quality и документация

### 13.1. Пирамида тестов

| Уровень             | Что проверять                                                                | Примеры                                                    |
| ------------------- | ---------------------------------------------------------------------------- | ---------------------------------------------------------- |
| Unit                | чистые parser/mapper/limit/capability функции, composables, command registry | Markdown table mapping, stale revision, object URL cleanup |
| Component           | renderer states и accessibility                                              | empty/error/partial table, toolbar keyboard controls       |
| Backend integration | API + storage + ownership + worker boundaries                                | artifact access, range limit, corrupt archive              |
| Contract            | DTO client/server compatibility                                              | discriminated preview union, error code mapping            |
| E2E                 | сквозной пользовательский flow                                               | upload → preview/edit → download; cancel/retry; mobile     |
| Visual              | стабильные ключевые экранные состояния                                       | home, viewer table, markdown editor desktop/mobile         |
| Security/regression | hostile fixtures и abuse limits                                              | XSS corpus, IDOR, zip bomb boundary                        |

### 13.2. Fixtures

Хранить небольшие, законные и детерминированные fixtures по форматам с README: происхождение/генератор, ожидаемое поведение, размер tier, security relevance. Не коммитить реальные пользовательские документы.

Минимальные tiers:

- `tiny` — быстрый happy path;
- `representative` — характерные возможности формата;
- `large-but-CI-safe` — virtualization/limits;
- `malformed` — controlled parser error;
- `hostile` — ограничение ресурса/безопасности без реальной опасной нагрузки.

### 13.3. Lint, formatting и type safety

- ESLint, Prettier и type-check остаются required CI gates.
- Включить постепенно строгие правила: no floating promises, exhaustive switch for preview union, no implicit `any`, no unused catch variables, import boundaries между features.
- Не включать шумное правило, которое создаёт тысячи suppressions. Каждое правило вводится с baseline, owner и планом удаления legacy исключений.
- Для backend добавить/поддерживать formatter, static analysis и dependency scan, согласованные с Gradle toolchain.
- Проверять conventional commit format локальным hook либо CI; hook должен быть быстрым и не подменять серверную проверку.

### 13.4. Документация

Обновлять одновременно с изменением поведения:

- capability/format matrix и лимиты;
- локальный запуск, Linux visual baselines, unit/integration/E2E команды;
- API/DTO контракт и error codes;
- privacy/data retention/artifact expiry;
- known limitations и fallback UX;
- этот план и roadmap с пометкой реально завершённых этапов.

## 14. Рекомендуемая последовательность работы и коммиты

Работать последовательно; не начинать крупный spreadsheet/PDF refactor, пока CI baseline и базовый lifecycle не стабильны.

| Порядок | Содержимое одного логического PR/серии commit              | Примеры conventional commits                               | Gate перед продолжением              |
| ------- | ---------------------------------------------------------- | ---------------------------------------------------------- | ------------------------------------ |
| 0       | Диагностика и стабилизация Linux visual snapshots          | `test(e2e): stabilize visual snapshot environment`         | CI visual + a11y/responsive зелёные  |
| 1       | Viewer shell/composables без смены поведения               | `refactor(viewer): split workspace session lifecycle`      | type/lint/unit/E2E smoke             |
| 2       | GFM tables и Markdown fixture matrix                       | `feat(markdown): render accessible GFM tables`             | unit/component/desktop-mobile visual |
| 3       | Editor command registry и единый Markdown preview contract | `refactor(editor): isolate format-aware commands`          | editor E2E + XSS corpus              |
| 4       | Artifact ownership, expiry, typed resolve errors           | `feat(platform): bind preview artifacts to session owners` | backend integration/security tests   |
| 5       | Bounded CSV/TSV descriptor и virtualized range UI          | `feat(viewer): add paged delimited table preview`          | large fixture, cancel, mobile grid   |
| 6       | Spreadsheet/SQLite preview hardening                       | `feat(viewer): add bounded workbook sheet preview`         | hostile fixtures + performance gates |
| 7       | PDF/EPUB/media/SVG security and renderer work              | `feat(viewer): harden document preview renderers`          | sandbox/headers/E2E                  |
| 8       | Converter/Compression/PDF/DevTools common task lifecycle   | `refactor(processing): unify cancellable task state`       | every workspace cancel/retry suite   |
| 9       | Durable jobs, queue, cleanup, observability                | `feat(platform): persist processing job lifecycle`         | restart/expiry/load tests            |
| 10      | Infrastructure/supply-chain and release documentation      | `chore(security): enforce runtime hardening policy`        | full CI, SBOM, docs review           |

Если этап объективно требует несколько commits, соблюдать принцип: сначала механический refactor с зелёными тестами, затем поведение/feature, затем docs/baselines, если они неразрывно не принадлежат feature.

## 15. Конкретный первый рабочий цикл для следующего агента

Статус цикла на 17 июля 2026 года: завершён. Инвентаризация актуализирована в
[workspace-module-inventory.md](workspace-module-inventory.md), processing contract — в
[processing-platform.md](processing-platform.md), security ownership — в
[processing-threat-model.md](processing-threat-model.md). Перед следующей продуктовой задачей:

1. Проверить успешность completion-branch CI и замечания MR, не ослабляя gates.
2. Начинать новую feature-ветку от свежего `main` после merge, не продолжать старую ветку.
3. Выбрать один residual capability/deployment prerequisite с отдельным acceptance contract.
4. Для production rollout создать Kubernetes secret, подставить immutable `sha-<commit>` images,
   применить Flyway migration и проверить PVC/NetworkPolicy/Ingress/health в целевом cluster.
5. Не выполнять deployment или merge без отдельного разрешения владельца.

## 16. Финальный Definition of Done программы

Программа считается завершённой, когда одновременно выполнено следующее:

- CI зелёный и воспроизводимый: lint, formatting, type-check, unit, backend integration, E2E, visual, security/SBOM gates.
- Markdown Viewer и Editor безопасно и одинаково отображают поддерживаемый GFM, в том числе таблицы, на desktop/mobile.
- Форматная матрица покрыта тестами и честно объясняет bounded/unsupported сценарии.
- Viewer/Editor разбиты по ответственности, не имеют stale result/URL/job leaks, не блокируют UI на больших файлах.
- Backend является source of truth для file processing, limits, ownership, artifacts и долгих job; frontend не дублирует эти правила.
- Все workspace-модули имеют success/error/cancel/retry/cleanup путь, а тяжёлые задачи лимитированы и отменяемы.
- Security threat model, headers, ownership, input limits, artifact TTL и dependency policy реализованы и протестированы.
- Внешний вид остаётся преемственным Jack: мягкий тёплый industrial/neumorphic foundation, адаптивность, сильная иерархия, доступный контраст и отсутствие generic admin-panel drift.
- Документация и roadmap отражают реальное состояние, а commits логичны, conventional и опубликованы в remote.
