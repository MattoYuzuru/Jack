# План UI/UX и функционального hardening

Дата первичного аудита: 12 июля 2026 года.

## Цель

Привести Jack к единому современному UI foundation и подтвердить поддерживаемые файловые
сценарии воспроизводимыми тестами. Главный приоритет — Viewer, затем Converter, PDF Toolkit
и Editor.

Под «полной поддержкой» в этом плане понимается не формальное умение принять расширение,
а прохождение документированного support profile: корректный intake, понятный preview,
безопасная обработка, сохранение оговорённой семантики, предсказуемые ограничения и тестовые
fixtures для happy path и edge cases.

## Зафиксированный baseline

- Опубликованный сайт и локальный `main` соответствуют одной текущей UI-концепции.
- Frontend: 24 test suites, 78 unit-тестов — успешно; typecheck, ESLint, Oxlint и Prettier — успешно.
- Backend: 14 test suites, 47 тестов — успешно на Java 26 toolchain.
- `npm audit`: 6 известных проблем в dependency tree, включая direct advisory для Vite.
- E2E, visual regression и автоматизированных accessibility-тестов сейчас нет.
- Семь workspace-экранов повторяют один и тот же topbar, но имеют собственные крупные scoped
  style-блоки. Только Viewer занимает 3464 строки; во view-слоях отдельно заданы 64 размера
  шрифта, 90 радиусов и 79 теней.

## Основные находки аудита

### UI foundation

- На мобильной ширине Viewer выходит за границы viewport; длинные topbar/pill-группы и
  некоторые workspace-блоки не имеют надёжной shrink/wrap-стратегии.
- Иерархия перегружена очень крупными заголовками, капсом, pill-элементами и одинаково
  сильными тенями. Интерактивные, вторичные и disabled-состояния визуально похожи.
- Огромные панели и пустые области уменьшают рабочую плотность, особенно в Viewer.
- Типографика зависит от внешнего Google Fonts import и не имеет локально поставляемых
  font assets. Размеры, line-height и measure задаются по экранам, а не шкалой токенов.
- Общий визуальный язык Jack узнаваем, поэтому нужен controlled refinement существующего
  soft industrial neumorphism, а не смена бренда.

### Viewer и форматы

- Markdown имеет две независимые regex-реализации: backend preview и live preview Editor.
  Они уже расходятся и не реализуют CommonMark/GFM полностью; таблицы не поддерживаются.
- CSV разбирается Apache Commons CSV, но первая строка безусловно считается header, preview
  ограничен 24 строками, а encoding/BOM, headerless/ragged data, большие файлы, навигация,
  filter/sort и диагностические сообщения не образуют законченный продуктовый сценарий.
- XLS/XLSX читаются Apache POI, но preview ограничен 12 колонками и 28 строками. Сейчас не
  сохраняются точная визуальная модель, merged cells, styles, charts и полная семантика формул.
- Capability matrix перечисляет широкий набор форматов, однако тесты проверяют отдельные
  representative paths, а не каждый объявленный format profile.
- Viewer совмещает intake, preview, медиаконтролы, document navigation, metadata и почти весь
  UI в одном SFC; это затрудняет изолированные тесты и безопасные изменения.

### Converter, PDF Toolkit и Editor

- Converter имеет хорошее backend-first разделение, но отсутствует автоматически исполняемая
  матрица всех объявленных source-target сценариев и fixture corpus с проверкой результата.
- PDF Toolkit покрыт happy-path API-тестами для всех операций, но нужен отдельный edge/security
  pass: повреждённые и encrypted PDF, пароли дополнительных merge inputs, page ranges/order,
  rotated/cropped pages, OCR limits, redaction verification и ресурсные ограничения.
- Операция `sign` фактически добавляет visible stamp и не является certificate-based digital
  signature; это нужно ясно отразить в названии и UX.
- Editor использует regex-highlighting и локальные preview/outline эвристики. Markdown дублирует
  Viewer, а диагностика HTML/CSS/JavaScript не заменяет полноценный parser/language service.

## Очерёдность реализации

### Этап 0. Quality harness и format contract

1. Добавить Playwright E2E для Chromium с viewport 1440, 1024, 768, 390 и 320 px.
2. Добавить smoke navigation, upload/drag-and-drop, keyboard flow и horizontal-overflow checks.
3. Подключить axe accessibility checks и visual snapshots для Home и основных workspace states.
4. Завести versioned fixture corpus: валидные, пустые, большие, повреждённые, password-protected,
   Unicode/RTL и misleading extension/MIME файлы.
5. Описать machine-readable format support profile и связать его с capability matrix и тестами.

Критерий готовности: любое объявленное поддерживаемое направление имеет fixture, ожидаемый
результат и тест; UI-регрессии размеров и overflow ловятся автоматически.

### Этап 1. UI foundation

1. Расширить общие tokens: type scale, line-height, spacing, radii, elevation, borders, focus,
   motion, content widths и control sizes.
2. Self-host display/body fonts в WOFF2, задать устойчивый system fallback и убрать runtime
   зависимость от Google Fonts.
3. Вынести общий `AppShell`/`WorkspaceHeader`, кнопки, chips, fields, panels, empty/loading/error
   states и responsive toolbar primitives.
4. Снизить визуальный шум: меньше одновременных raised/pressed поверхностей, яснее primary и
   secondary actions, компактнее рабочие панели, спокойнее caps и letter-spacing.
5. Исправить 320–390 px layout, safe-area, touch targets, overflow, visible focus, contrast,
   reduced-motion и forced-colors.

Критерий готовности: WCAG 2.2 AA для ключевых flows, отсутствие horizontal scroll на целевых
viewport, стабильная типографика без сети и единые primitives на всех workspace-экранах.

### Этап 2. Viewer shell и декомпозиция

1. Разделить Viewer на shell, dropzone, toolbar, image, document, spreadsheet, video, audio,
   metadata и inspector components.
2. Сделать stage главным рабочим объектом: адаптивный sidebar/inspector, компактная командная
   панель, предсказуемые loading/progress/error/retry/cancel состояния.
3. Нормализовать keyboard shortcuts, focus restoration, drag-and-drop, fullscreen и cleanup
   object URLs/jobs при быстрой смене файлов.
4. Добавить component tests для каждого renderer и E2E для race/cancel/unsupported/corrupt flows.

Критерий готовности: view-компонент отвечает за композицию, renderer-ы тестируются отдельно,
а смена файла во время обработки не оставляет stale UI или ресурсов.

### Этап 3. Markdown profile уровня Obsidian

Единый backend-owned pipeline должен заменить оба regex-рендера. Базовый профиль:

- CommonMark и GFM: tables, task lists, strikethrough, autolinks, fenced code, nested lists;
- footnotes, heading anchors, TOC/outline, definition lists, highlights и безопасные links/images;
- Obsidian-style wikilinks, embeds, callouts и tags с понятным fallback без vault context;
- math и Mermaid только через безопасный internal renderer, без выполнения пользовательского JS;
- raw HTML проходит allowlist sanitization; `script`, event handlers, `javascript:` URL, iframe,
  object/embed и опасные SVG-конструкции блокируются;
- одна и та же render service/configuration используется Viewer и Editor preview.

В scope не входят Obsidian plugin API, выполнение snippets/scripts, vault database, Canvas и
поведение сторонних plugins. Это различие должно быть явно описано пользователю.

Критерий готовности: CommonMark/GFM compliance fixtures, отдельный Obsidian-extension corpus,
XSS suite и идентичный HTML/outline contract для Viewer и Editor.

### Этап 4. Полноценный CSV/TSV viewer

1. Поддержать UTF-8 BOM, диагностируемые encoding errors, RFC 4180 quoting/newlines,
   delimiter/quote detection, header/headerless режим, empty/ragged/duplicate columns.
2. Перенести большие таблицы в paged/streaming backend contract; не отправлять весь dataset в DOM.
3. Добавить sticky row/column headers, virtualized rows, resize, sort, filter, find, cell copy,
   row numbers, column statistics и export текущего среза.
4. Показывать выбранный dialect/encoding и позволять пользователю переопределить неверный detect.

Критерий готовности: corpus покрывает запятые/точки с запятой/tab, multiline quoted cells,
Unicode, headerless/ragged files и большие datasets без заметной блокировки UI.

### Этап 5. XLS/XLSX workbook viewer

1. Развить semantic payload: типы ячеек, raw/formatted values, formulas и cached results, dates,
   errors, merged ranges, row/column sizes, hidden/frozen state, hyperlinks и comments.
2. Добавить lazy sheet/range API и виртуализацию вместо fixed 12×28 preview.
3. Отрисовывать безопасный поднабор styles и явно показывать неподдерживаемые workbook features.
4. Для fidelity preview оценить backend conversion через LibreOffice в PDF/HTML artifact, сохранив
   semantic grid как отдельный режим для поиска, копирования и анализа.
5. Проверить XLSX zip-bomb limits, external links, macros и formula injection policy.

Критерий готовности: multi-sheet, formulas, merged cells, dates, styles, hidden/frozen rows и
large-sheet fixtures проходят semantic и visual assertions; macros никогда не выполняются.

### Этап 6. Остальные Viewer profiles

По каждой строке capability matrix проверить intake, preview fidelity, metadata, search/copy,
download, corrupt-file response, limits и browser fallback. Отдельные группы: raster/vector/RAW,
PDF/office/EPUB/SQLite/config/text, video/subtitles и audio/tags/waveform.

Критерий готовности: capability matrix строится только из профилей, прошедших contract tests;
недоступные возможности честно маркируются, а не выглядят полностью поддержанными.

### Этап 7. Converter hardening

1. Сгенерировать scenario tests из capability matrix и выполнить каждое объявленное направление.
2. Валидировать MIME/signature/extension mismatch, alpha/color profile/orientation, animated input,
   odd media dimensions, codec/container compatibility, bitrate/FPS/duration и empty/corrupt files.
3. Проверять артефакт через повторный decode/probe, а не только наличие непустого файла.
4. Добавить batch UX, понятные estimates/warnings, retry/cancel и безопасные имена результатов.

Критерий готовности: ни один `available` scenario не существует без executable fixture test и
post-conversion validation.

### Этап 8. PDF Toolkit hardening

1. Проверить encrypted/corrupt/large PDFs и password flow для каждого input, включая merge stack.
2. Усилить parser/validation page selections, ranges, duplicates, rotations и crop/media boxes.
3. Проверить OCR language/DPI/timeouts, image-only documents и сохранение orientation.
4. Подтвердить redaction извлечением текста и отсутствием старых content streams/attachments;
   показывать пользователю потерю vector/text/annotation layers из-за raster rebuild.
5. Переименовать `sign` в visible signature/stamp либо отдельно реализовать certificate-based flow.
6. Добавить preview before apply и undo-friendly operation history на frontend.

Критерий готовности: каждая операция имеет negative/edge/security tests и проверку содержимого
результата, а не только media type/page count.

### Этап 9. Editor hardening

1. Переиспользовать единый Markdown pipeline.
2. Заменить regex-highlighting на устойчивый parser/highlighter; не исполнять пользовательский JS.
3. Дать точные diagnostics для JSON/YAML/HTML/CSS/JavaScript с line/column и безопасным preview.
4. Проверить autosave, recovery, large text, undo/redo, keyboard shortcuts, dirty state, import/export,
   encoding/newlines и быстрые последовательные server checks.
5. Разделить editor surface, toolbar, preview, diagnostics, outline и export components.

Критерий готовности: malformed input не ломает preview, stale responses не перезаписывают новый
draft, а export round-trip сохраняет выбранные encoding/newline semantics.

### Этап 10. Security, performance и документация

1. Обновить уязвимые frontend dependencies с regression run.
2. Зафиксировать upload/decompression/XML/ZIP/XLSX/PDF resource limits и security fixtures.
3. Добавить performance budgets для initial load, large preview, memory и long tasks.
4. Обновить README и processing-platform docs: support levels, ограничения и реальный статус.

## Стратегия коммитов

Каждый этап разбивается на отдельные conventional commits: foundation, конкретный renderer,
backend contract, tests и docs не смешиваются без необходимости. После каждого логического блока
запускаются затронутые unit/API/E2E проверки, затем полный frontend и backend regression suite.

Работа ведётся локально в feature-ветке. Fetch, pull, push и создание MR не выполняются до
отдельного разрешения пользователя.
