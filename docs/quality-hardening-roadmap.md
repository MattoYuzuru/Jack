# Полный аудит и план hardening Jack

Дата аудита: 12 июля 2026 года.

## Цель и границы

Документ задаёт исполнимый план исправления Viewer, Editor, UI foundation и общих
production-рисков. Он рассчитан на следующего агента: каждый этап содержит конкретный scope,
проверки и критерий завершения.

Под «полной поддержкой Markdown» здесь понимается не бесконечный набор несовместимых плагинов,
а версионируемый профиль:

1. CommonMark как обязательная база.
2. GitHub Flavored Markdown как обязательное расширение.
3. Версионируемый Obsidian-compatible профиль без plugin API и пользовательского JavaScript.
4. Отдельно включаемые безопасные расширения, перечисленные в этом плане.
5. Явный fallback для синтаксиса вне профиля.

Аудит включал чтение frontend/backend/infra-кода, локальный build и тесты, `npm audit`,
точечное выполнение текущего Markdown renderer, headless-снимки на desktop/mobile и read-only
проверку опубликованного контура. Разрушающие security-тесты и загрузка вредоносных файлов в
production не выполнялись. Backend dependency SCA и полноценный penetration test в baseline
отсутствуют и должны быть добавлены отдельным этапом.

## Проверенный baseline

- `main` совпадал с актуальным `origin/main` на момент аудита.
- Frontend: typecheck, Oxlint, ESLint, Prettier и production build прошли.
- Frontend: 24 test suites, 78 тестов прошли.
- Backend: 47 тестов прошли.
- E2E, visual regression и автоматизированных accessibility-тестов нет.
- `npm audit`: 6 advisory во всём dependency tree — 1 critical, 3 high, 2 moderate.
- `npm audit --omit=dev`: 1 moderate advisory для PostCSS dependency path.
- Прямой Vite dependency находится в уязвимом диапазоне; это особенно важно для dev-server,
  который в Docker Compose запускается на `0.0.0.0`.
- Опубликованный `/api/capabilities/platform` доступен без authentication.
- Опубликованный `/actuator/metrics` доступен без authentication и раскрывает JVM, JDBC,
  executor и processing-метрики.
- В production-ответе frontend нет CSP, HSTS, `X-Content-Type-Options`, `Referrer-Policy` и
  `Permissions-Policy`; также раскрывается версия nginx.
- Headless-снимки на ширине 390 px подтвердили horizontal overflow на Home, Viewer и Editor.
- Семь экранов дублируют topbar и держат крупные scoped style-блоки. Только Viewer занимает
  3464 строки. Во view-слое и общей таблице стилей найдено 64 объявления `font-size`, 90
  `border-radius`, 79 `box-shadow` и 126 `gap`.

## Критические находки

| ID     | Приоритет | Находка                                                                                                                         | Последствие                                                        |
| ------ | --------- | ------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| SEC-01 | P0        | Editor Markdown preview собирает ссылки regex-заменой и вставляет результат через `v-html`; URL-схема и кавычки не валидируются | DOM XSS через `javascript:` и инъекцию event-атрибута              |
| SEC-02 | P0        | Public processing API принимает до 256 MB, запускает CPU/RAM-heavy jobs без auth, quota, rate limit и bounded queue             | Удалённый resource exhaustion и неконтролируемые расходы           |
| SEC-03 | P0        | Untrusted file processors работают root-процессом; ImageMagick policy заново включает PDF/PS/EPS/XPS coders                     | Максимальный blast radius при parser/converter exploit             |
| SEC-04 | P0        | `OfficeConversionService.parseXml` использует default `DocumentBuilderFactory`                                                  | XXE/SSRF/local-file disclosure risk для ODT/ODS containers         |
| SEC-05 | P1        | Dev Tools сохраняет JWT, HMAC secret и Basic Auth password в `localStorage`                                                     | Секреты переживают сессию и доступны любому same-origin script/XSS |
| SEC-06 | P1        | ZIP entries, CSV, workbook, PDF/image decode и часть text paths читаются/разворачиваются целиком без общих resource budgets     | Zip/decompression bomb, heap exhaustion, long-running jobs         |
| SEC-07 | P1        | Content type и file family определяются преимущественно по клиентскому MIME/расширению                                          | Type confusion и отправка misleading input в опасный parser        |
| SEC-08 | P1        | Security headers отсутствуют, а Markdown XSS не сдерживается CSP                                                                | Уязвимость выполняется в основном origin приложения                |
| SEC-09 | P1        | Upload/job/artifact не имеют owner/session boundary; artifact отдается по UUID, job можно отменить по UUID                      | Нет модели изоляции данных даже для anonymous users                |
| SEC-10 | P1        | Artifact download читает весь файл через `Files.readAllBytes`                                                                   | Один большой download удваивает memory pressure на backend         |
| SEC-11 | P1        | Job errors могут возвращать клиенту raw exception message                                                                       | Возможна утечка внутренних путей и деталей runtime                 |
| SEC-12 | P1        | Metrics endpoint публичен                                                                                                       | Раскрытие инфраструктуры и текущей нагрузки упрощает abuse         |

### Воспроизводимое подтверждение SEC-01

Текущий `buildEditorLocalPreview('markdown', input)` генерирует:

```text
<a href="javascript:document.body.dataset.pwned=1">bad</a>
<a href="https://example.com/" onmouseover="document.body.dataset.pwned=1">inject</a>
```

Этот HTML попадает в `EditorWorkspaceView.vue` через `v-html`. Наличие backend-warning для
`javascript:` не является защитой: live preview выполняется до server validation.

## Фактическая поддержка Markdown

В проекте есть две независимые реализации: `DocumentPreviewService.renderMarkdownBody` для
Viewer и `editor-preview.ts` для Editor. Обе основаны на regex, уже расходятся и не реализуют
стандартный parser state.

| Конструкция                     | Viewer                                   | Editor live preview                                     | Требуемый профиль            |
| ------------------------------- | ---------------------------------------- | ------------------------------------------------------- | ---------------------------- |
| ATX headings `#`                | Частично                                 | Частично                                                | CommonMark                   |
| Setext headings                 | Нет                                      | Нет                                                     | CommonMark                   |
| Paragraph/soft break semantics  | Неверно упрощены                         | Неверно упрощены                                        | CommonMark                   |
| Bullet lists                    | Только плоские `-`/`*`                   | Только отдельные плоские блоки                          | CommonMark                   |
| Ordered lists                   | Нет                                      | Последовательность может развалиться после первого item | CommonMark                   |
| Nested/mixed lists              | Нет                                      | Нет                                                     | CommonMark                   |
| Task lists                      | Печатаются как текст                     | Только top-level `- [ ]`/`- [x]`                        | GFM                          |
| Tables                          | Нет                                      | Нет                                                     | GFM                          |
| Strikethrough                   | Нет                                      | Нет                                                     | GFM                          |
| Autolinks                       | Нет                                      | Нет                                                     | GFM                          |
| Fenced/indented code            | Нет                                      | Упрощённый triple-backtick без корректного info string  | CommonMark                   |
| Inline code escaping            | Частично                                 | Частично                                                | CommonMark                   |
| Links/images                    | Только часть `http(s)` links, images нет | Links небезопасны, images заменены текстом              | CommonMark + security policy |
| Blockquote nesting              | Нет                                      | Нет                                                     | CommonMark                   |
| Thematic breaks                 | Нет                                      | Нет                                                     | CommonMark                   |
| Escapes/entities                | Нет                                      | Нет                                                     | CommonMark                   |
| Raw HTML                        | Не определён продуктовый policy          | Экранируется, но diagnostics говорит о raw HTML         | Явный allow/deny policy      |
| Footnotes/definition lists/math | Нет                                      | Нет                                                     | Optional profile             |

Текущий frontend-тестовый набор вообще не тестирует Markdown renderer. Backend-тест проверяет
только заголовок, простой `-`-список и наличие editable draft; compliance и XSS corpus нет.

## Прочие подтверждённые дефекты

### Viewer

- `ViewerWorkspaceView.vue` объединяет intake, image tools, document viewer, spreadsheet,
  database, video, audio, subtitles, metadata и почти весь CSS в одном SFC.
- При выборе нового server-assisted файла старый frontend response игнорируется, но сам backend
  job не отменяется. То же происходит при уходе со страницы и client-side timeout.
- Начальный toolbar показывает много нерелевантных disabled controls до выбора типа файла.
- Document search выдаёт не более 10 excerpts, но не подсвечивает и не прокручивает совпадение в
  HTML/PDF/table preview.
- Clipboard/fullscreen/PiP действия местами не ловят rejected promise и не дают стабильное
  accessible error state.
- CSV всегда считает первую непустую строку header, удаляет пустые строки и сначала парсит весь
  файл в память, хотя показывает только 24 строки.
- XLS/XLSX проходит по всем materialized rows/cells и только после этого обрезает preview до
  12x28; styles, merged cells, formulas, comments и charts не представлены в контракте.
- SQLite preview выполняет `COUNT(*)` для каждой таблицы без query timeout и read-only URI mode.
- Generic `text/plain` MIME перекрывает более точные extension-профили в frontend registry;
  `.env`/`.log` могут отображаться как TXT. Та же precedence-проблема особенно заметна для
  Markdown в Editor.
- Быстрый handoff в Editor автоматически кладёт полный документ в `localStorage`.

### Editor

- Простой `textarea` имитирует IDE: line gutter расходится с визуальными строками при wrap,
  format-specific indentation отсутствует, а programmatic snippets не дают надёжный undo.
- Последовательные regex-проходы syntax highlighter повторно обрабатывают уже вставленные
  `<span>`: JavaScript `const value = "return 42"` повреждает generated markup и подсвечивает
  keyword внутри строки.
- Markdown toolbar не содержит italic, strike, quote, bullet/ordered/task lists, table, image,
  footnote, thematic break, inline code и heading levels.
- HTML/CSS/JS/JSON/YAML actions вставляют статические строки без AST/context awareness,
  indentation, toggle semantics и сохранения selection.
- Templates не фильтруются по активному формату и применяются разрушительно без dirty-state
  confirmation.
- Выбор формата по generic MIME выполняется раньше extension. Markdown-файл с `text/plain`
  открывается как TXT.
- Autosave каждые 220 ms сериализует весь draft в `localStorage`; quota errors не обработаны,
  opt-in и size limit отсутствуют.
- Открытие файла использует `file.text()` без client-side size/encoding policy.
- Local preview синхронно пересчитывается на каждый input и может блокировать main thread на
  большом документе.
- HTML sanitizer является blocklist, а CSS preview разрешает remote `@import`/`url`, поэтому
  preview может делать нежелательные внешние запросы.
- `iframe sandbox="allow-same-origin"` шире необходимого для HTML/CSS preview.
- Server diagnostics parser-backed только для JSON/YAML. Markdown, HTML, CSS и JavaScript в
  основном проверяются regex/скобочным scanner и дают ложное чувство полноценной валидации.
- HTML validation создаёт `Files.createTempFile`, но не удаляет файл и не отдаёт его TTL cleanup.
- Tabs имеют `role="tablist"`, но нет `role="tab"`, `aria-selected`, `aria-controls` и
  `tabpanel` contract.

### UI/UX foundation

- На 390 px обрезаются topbar, hero titles, chips и editor status grid; аналогичный риск остаётся
  на 320 px.
- Логотип уже содержит wordmark/tagline, рядом повторно рисуется крупный lockup copy; на mobile
  это создаёт минимальную ширину больше viewport.
- Почти каждый экран повторяет hero + marketing copy + status card до основного действия. Для
  рабочих инструментов это уменьшает полезную плотность первого viewport.
- Все pills, buttons, cards и panels используют похожую neumorphic глубину. Primary,
  interactive, passive и disabled surfaces различаются недостаточно.
- Базовый control height равен 40 px, что меньше рекомендуемой touch target 44 px.
- `panel-surface` использует `overflow: hidden`, поэтому внутренние focus ring могут обрезаться.
- Reduced motion определён только на Home; forced colors, high contrast, safe-area и global
  motion policy отсутствуют.
- Ошибки и progress-сообщения не имеют системного `aria-live`/`role="alert"` contract.
- `<html lang="">` не задаёт язык документа.
- Шрифты загружаются runtime-import с Google Fonts. Offline/privacy-blocked сценарий меняет
  метрики текста и усиливает layout shift.

### Processing platform и общие frontend runtime

- `newVirtualThreadPerTaskExecutor()` не ограничивает число одновременно запущенных внешних
  ffmpeg/ImageMagick/Tesseract/PDF jobs.
- Jobs/uploads живут только в `ConcurrentHashMap`, хотя PostgreSQL уже включён в deployment.
  После restart registry теряется, а во время rolling update два pod не разделяют job state.
- Frontend polling не использует `AbortController`; timeout прекращает только ожидание клиента,
  но не server job.
- Converter отменяет job при смене source, но не при unmount. Viewer, Compression, PDF Toolkit и
  Editor также не имеют общего route-leave cancellation contract.
- Compression и PDF Toolkit сначала делают `.slice(0, MAX_RESULT_HISTORY)`, затем пытаются
  revoke удалённые URL в недостижимом `while`; вытесненные object URL и Blob остаются в памяти.
- Histories держат несколько полных result/preview Blob. Для крупных медиа/PDF это быстро
  превышает разумный browser memory budget.
- Compression и PDF Toolkit не имеют request revision token уровня Converter; поздний ответ
  способен примениться после смены source/operation.
- В local development frontend вызывает `DELETE /api/jobs/{id}`, но CORS allowlist содержит
  только GET/POST/OPTIONS. Cancel flow блокируется preflight.
- Capability cache не имеет explicit refresh/versioning, а локально полезные функции Editor и
  native Viewer становятся недоступны при падении capability API вместо degraded mode.

### Converter

- Capability matrix тестируется representative examples, а не автоматически исполняемой
  матрицей каждой объявленной пары source-target.
- Cache key использует name/type/size/lastModified, но не content hash; два разных файла с
  одинаковыми metadata могут получить неверный session-cache hit.
- Result validation в части сценариев подтверждает наличие файла/facts, но не всегда делает
  независимый decode/probe результата.
- Browser history удерживает Blob, а download создаёт ещё один временный object URL.

### Compression

- Помимо object URL leak и race, retry вызывает текущий `compress()`, а не обязательно
  воспроизводит snapshot всех значений из `lastRequest`.
- Target-size является best-effort, но UX должен показывать achieved/failed budget до download,
  а не только общей фразой после выполнения.
- Нужны fixtures для alpha, animation, odd dimensions, duration/bitrate limits и near-impossible
  target sizes.

### PDF Toolkit

- `sign` является visible stamp, а не certificate-based digital signature.
- Merge умеет пароль только для primary input; additional encrypted PDF открываются без пароля.
- Result history имеет object URL leak; operation/source races не защищены revision token.
- Redaction правильно raster rebuild-ит страницы, но нет postcondition-теста: извлечение текста,
  content streams, attachments и metadata должны проверяться после операции.
- Нет полного negative corpus для corrupt/encrypted/huge PDF, rotations/crop boxes, duplicate or
  invalid ranges, image-only OCR и timeout/cancel.

### Dev Tools

- Sensitive inputs сохраняются автоматически и без предупреждения.
- Hash tool читает весь File в `arrayBuffer()` и параллельно считает несколько digest на main
  thread; большой файл может заморозить UI.
- Clipboard errors не нормализованы общим interaction primitive.
- UUID/ULID fallback не должен тихо использовать нулевую энтропию, если Web Crypto отсутствует.

### Infrastructure и CI/CD

- Frontend/backend containers и Kubernetes workloads не задают non-root `securityContext`,
  dropped capabilities, read-only root filesystem или seccomp profile.
- Backend runtime образ содержит широкий набор native parsers в одном container и скачивает
  `latest` JDK archive без checksum/pinned version.
- GitHub Actions и base images закреплены mutable tags, SBOM/signing/scanning gates отсутствуют.
- Production Nginx не задаёт security/cache headers и не скрывает version header.
- Actuator metrics опубликован тем же ingress без отдельной access policy.

## Целевая архитектура

### Markdown

```text
Markdown source
      |
      v
bounded backend render/analyze service
      |
      +-- CommonMark + GFM parser
      +-- optional extension registry
      +-- URL/image policy
      +-- allowlist sanitizer
      +-- outline/anchor extraction
      |
      v
versioned RenderContract
      |
      +-- Viewer sandboxed renderer
      +-- Editor debounced preview
      +-- export/diagnostics
```

Viewer и Editor не должны иметь собственные Markdown regex. Один render contract возвращает
sanitized HTML, outline, anchors, warnings, detected features и profile version.

### Editor

Presentation/interaction остаются на frontend: CodeMirror 6 surface, selection, commands,
keybindings, panels и responsive layout. Parsing, sanitization, authoritative diagnostics и
exports остаются backend-owned. Для live feedback нужен bounded synchronous analyze endpoint с
debounce, request revision и cancellation; текущий upload/job/artifact flow остаётся для export и
тяжёлых операций, но не создаёт artifact на каждое нажатие клавиши.

### Processing

Public request сначала получает anonymous/user session и budget, затем проходит sniffing и
capability policy. Bounded queue отправляет job в изолированный worker с CPU/RAM/time/output
limits. Metadata job/upload/artifact сохраняются в PostgreSQL, binary — в ограниченном artifact
storage. Download стримится и проверяет owner/session.

## Карта затрагиваемых подсистем

| Направление                 | Основные точки изменения                                                                                                                                        | Что остаётся source of truth                                                                               |
| --------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| Markdown и Editor preview   | `frontend/src/features/editor/application/editor-preview.ts`, `frontend/src/views/EditorWorkspaceView.vue`, `DocumentPreviewService`, `EditorProcessingService` | Версионируемый backend `RenderContract`; frontend отвечает за presentation, debounce и cancellation        |
| Editor interaction          | `useEditorWorkspace.ts`, `editor-registry.ts`, `EditorWorkspaceView.vue`                                                                                        | CodeMirror command state на frontend; parsing, sanitization, diagnostics и export на backend               |
| Viewer lifecycle            | `ViewerWorkspaceView.vue`, `viewer-runtime.ts`, `viewer-server-preview.ts`, `processing-client.ts`                                                              | Общий task controller и backend job state; renderer-компоненты не владеют business rules                   |
| UI foundation               | `frontend/src/App.vue`, `frontend/src/styles.css`, все workspace views                                                                                          | Общие tokens/primitives; scoped styles содержат только специфику конкретного renderer                      |
| Processing security         | `WebConfiguration`, upload/job controllers, `ProcessingJobService`, storage services, `OfficeConversionService`                                                 | Owner-bound backend policy, bounded queue, central intake и resource budgets                               |
| Converter, Compression, PDF | Соответствующие composables, capability matrix services и processing services                                                                                   | Backend capability/validation contract; frontend хранит только interaction state и лёгкую metadata history |
| Runtime и delivery          | `compose.yaml`, Dockerfiles, `frontend/nginx/default.conf`, `k8s/jack`, `.github/workflows`                                                                     | Ограничения и security policy должны совпадать в local, CI и production deployment                         |

Эта карта задаёт стартовые точки, но не разрешает переносить файловую обработку или policy-логику
на frontend. Перед новым endpoint нужно сначала проверить возможность расширить существующие
upload/job/artifact/capability контракты.

## План реализации

### Этап 0. Немедленно закрыть exploitable security gaps

1. До готовности нового parser отключить `v-html` для Markdown либо прогонять результат через
   строгий sanitizer и URL allowlist; предпочтительно временно показывать escaped source.
2. Добавить regression payloads для `javascript:`, mixed-case/whitespace schemes, quotes,
   event handlers, SVG/data URLs, malformed links и nested markup.
3. Удалить JWT/HMAC/Basic Auth password из persisted schema, при старте миграционно очистить
   старые поля. Для секретов использовать только volatile state; несекретный persistence сделать
   opt-in.
4. Защитить XML parsing в `OfficeConversionService`: secure processing, запрет DOCTYPE/external
   entities/DTD, `XInclude=false`, `expandEntityReferences=false`; вынести общий secure factory.
5. Обновить Vite/PostCSS и транзитивные зависимости через lockfile с полным regression run.
6. Добавить DELETE в dev CORS либо перейти на same-origin proxy для всех окружений.
7. Закрыть `/actuator/metrics` внешним ingress, оставить только минимальный health endpoint.
8. Добавить initial CSP report-only, затем enforcing CSP без `unsafe-inline`; задать HSTS,
   nosniff, referrer/permissions policy, frame-ancestors и безопасный cache policy.
9. До полной переделки platform ввести аварийные ingress/backend-ограничения: уменьшить public
   body limit, ограничить частоту и число одновременных тяжёлых jobs, задать bounded admission и
   возвращать 413/429 до помещения работы в executor.
10. До выделения worker отключить неиспользуемые опасные parser/coder paths и запустить доступные
    processors non-root с запрещённым network egress. PDF/PS/EPS/XPS через ImageMagick не должны
    оставаться глобально включёнными только ради редкого conversion path.

Критерий: XSS corpus не создаёт executable DOM; XXE fixtures не читают file/network; секреты не
появляются в Storage; dependency audit не содержит known high/critical; cancel работает локально;
burst тяжёлых anonymous-запросов получает 413/429 без роста неограниченной очереди, а untrusted
processor не работает root и не имеет произвольного network access.

### Этап 1. Quality harness и format contract

1. Подключить Playwright для Chromium как минимум на 1440, 1024, 768, 390 и 320 px.
2. Добавить smoke navigation, keyboard-only flow, upload/drop, route-leave, cancel/retry и
   horizontal-overflow assertions.
3. Подключить axe и visual snapshots для Home и empty/loading/error/result states каждого
   workspace.
4. Завести versioned fixture corpus: valid, empty, large, corrupt, encrypted, Unicode/RTL,
   misleading extension/MIME, decompression bomb simulators и security payloads.
5. Описать machine-readable support profile. Capability может быть `available` только при наличии
   fixture и contract test для intake, processing, artifact и preview.
6. Добавить frontend component tests для workspace primitives и renderer components; тестировать
   не только domain registries.
7. Добавить backend SCA, frontend audit gate, SBOM и container scanning в CI.

Критерий: regression размеров, a11y, declared formats и security corpus ловится CI до merge.

### Этап 2. Единый CommonMark/GFM и Obsidian-compatible pipeline

1. Выбрать поддерживаемый Java parser с extension API, например `flexmark-java`, и зафиксировать
   версию профиля в contract.
2. Обязательный CommonMark: paragraphs, soft/hard breaks, ATX/Setext headings, thematic breaks,
   blockquotes, nested/mixed ordered и unordered lists, fenced/indented code, inline code,
   emphasis/strong, links, images, escapes и entities.
3. Обязательный GFM: tables, task lists, strikethrough, autolinks и disallowed raw HTML policy.
4. Безопасные optional extensions: footnotes, definition lists, heading anchors, TOC, highlight
   и sub/sup. Включать их по profile flags и покрывать fixtures.
5. Obsidian-compatible профиль должен отдельно определить YAML frontmatter, `[[wikilinks]]`,
   `![[embeds]]`, callouts, tags и block references. Без vault context внутренние ссылки и embeds
   не угадывают путь: contract возвращает unresolved reference и UI показывает безопасный
   fallback либо предлагает явно выбранный набор вложений.
6. Явно не обещать совместимость с Obsidian plugin API, Dataview/Templater, пользовательскими
   scripts/CSS snippets, Canvas и поведением сторонних plugins. Неизвестная конструкция остаётся
   читаемым source, а не исчезает и не исполняется.
7. Math и Mermaid добавлять только отдельным этапом: без user JavaScript, с sanitized SVG/HTML,
   size/time limits и CSP-compatible renderer.
8. Raw HTML по умолчанию выключить. Если product включает его, пропускать через explicit
   allowlist; блокировать scripts, event attributes, forms, iframe/object/embed, dangerous SVG,
   CSS и unsafe URL schemes.
9. External images по умолчанию не загружать автоматически. Добавить click-to-load либо backend
   proxy с SSRF policy, DNS/IP validation, content/size/time limits.
10. Возвращать одинаковые sanitized HTML, outline и anchors в Viewer и Editor. Оба preview
    рендерить в sandboxed iframe без `allow-same-origin`, если оно не доказано необходимо.
11. Добавить CommonMark spec examples, GFM corpus, Obsidian compatibility fixtures, adversarial
    XSS corpus и snapshot contract между Viewer/Editor.

Критерий: заявленный профиль проходит compliance corpus; Viewer и Editor дают один результат;
Obsidian-specific fixtures имеют документированный render или fallback; ни один payload не
выполняет script, navigation или скрытый external request.

### Этап 3. Editor как полноценный multi-format инструмент

1. Заменить textarea/gutter/regex-highlighter на CodeMirror 6 с lazy language packages для
   Markdown, HTML, CSS, JavaScript, JSON, YAML и plain text.
2. Вынести `EditorSurface`, `FormatToolbar`, `PreviewPanel`, `DiagnosticsPanel`, `OutlinePanel`,
   `ExportPanel`, `TemplatePicker` и persistence service.
3. Реализовать command model с корректным selection/undo/redo/toggle semantics.
4. Markdown commands: heading levels, bold, italic, strike, inline/fenced code, quote,
   bullet/ordered/task list, link, image, table builder, thematic break и footnote.
5. Enter/Tab должны продолжать/завершать/вкладывать списки и task items, сохранять нумерацию и
   работать с multi-selection.
6. HTML presets: semantic layout, text, link/image, list, table, form и accessible landmarks.
   CSS presets: rule, variables, flex, grid, media/container query, keyframes. JS presets:
   import/export, function, async/fetch, try/catch, listener. JSON/YAML snippets должны учитывать
   AST context и не создавать заведомо невалидный документ.
7. Фильтровать templates по формату, добавить search/command palette, preview diff и confirmation
   перед заменой dirty draft.
8. Extension должен иметь приоритет над generic MIME; MIME использовать как fallback и источник
   mismatch warning.
9. Добавить encoding/newline selector и round-trip tests для UTF-8 BOM, CRLF/LF и Unicode.
10. Persistence сделать opt-in, bounded и versioned; quota errors показывать пользователю.
    Добавить recovery snapshots без секретных полей.
11. Parser-backed diagnostics возвращают line/column/range и quick-fix code. HTML/CSS/JS не
    называть fully validated, пока они проверяются regex.
12. Live analyze debounce выполнять вне main interaction, отменять stale request и не позволять
    позднему ответу перезаписать новый draft.
13. Гарантированно удалять временные backend-файлы в `finally` либо создавать их внутри managed
    job directory.

Критерий: toolbar покрывает заявленные commands; undo/redo и selection сохраняются; large draft
не блокирует typing; stale diagnostics невозможны; export round-trip сохраняет encoding/newlines.

### Этап 4. UI foundation без смены визуального языка

1. Сохранить soft industrial neumorphism, sand/teal/coral palette и крупные продуктовые
   иллюстрации, но снизить количество одновременно raised/pressed surfaces.
2. Ввести foundation tokens до рефакторинга экранов:
   - spacing: 4/8/12/16/24/32/48/64;
   - type scale и line-height для caption/body/label/title/display;
   - content measure и workspace widths;
   - radius tiers вместо десятков ad-hoc значений;
   - elevation 0/1/2/floating/inset и border states;
   - control sizes 44/48, focus, motion и z-index.
3. Self-host WOFF2 fonts с `font-display: swap`, корректными fallback и проверкой кириллицы.
4. Создать общие `AppShell`, `WorkspaceHeader`, `Panel`, `Button`, `Chip`, `Field`, `Tabs`,
   `DropZone`, `Toolbar`, `StatusBanner`, `Progress` и empty/loading/error primitives.
5. На mobile использовать компактный mark/logo, `min-width: 0`, overflow wrapping и fluid
   typography. Длинные слова/имена файлов должны безопасно переноситься.
6. Сократить marketing hero на рабочих маршрутах: главное действие и stage должны попадать в
   первый viewport. Вторичное описание переносится в collapsible help/about.
7. Viewer toolbar строить по текущему renderer; не показывать image controls для empty/document/
   media state.
8. Сделать responsive inspector: desktop sidebar, tablet collapsible panel, mobile bottom sheet.
9. Закрыть WCAG 2.2 AA: keyboard order, 44 px targets, contrast, visible unclipped focus,
   semantic tabs, labels, `aria-live`, reduced motion, forced colors и screen-reader names.
10. Задать `lang="ru"`, safe-area padding и отсутствие horizontal scroll на всех target widths.

Критерий: visual snapshots стабильны; на 320–1440 px нет overflow; все ключевые flows проходят
axe и keyboard-only; внешний font network не нужен.

### Этап 5. Декомпозиция и устойчивость Viewer

1. Разделить shell/dropzone/toolbar/inspector и renderers: image, Markdown/HTML/text, PDF,
   table/workbook/database, video и audio.
2. Ввести общий processing task controller: AbortController, job id, revision token,
   cancel-on-replace/unmount/timeout, retry и idempotent cleanup.
3. В renderer tests проверить rapid file switching, route leave, corrupt/unsupported input,
   late manifest/blob response и object URL release.
4. Связать search results с renderer navigation/highlight. Для iframe определить безопасный
   message contract либо рендерить структурный document model.
5. Нормализовать clipboard/fullscreen/PiP errors, focus restoration после file picker и
   accessible drag-and-drop state.
6. Не хранить большие document drafts в `localStorage` для handoff; использовать in-memory route
   state либо bounded encrypted/session-scoped draft store.

Критерий: Viewer SFC отвечает только за композицию; каждый renderer тестируется отдельно; смена
файла не оставляет job, Blob URL, stale state или потерянный focus.

### Этап 6. Табличные и document profiles Viewer

#### CSV/TSV

1. UTF-8 BOM и диагностируемые encoding errors; delimiter/quote detection с ручным override.
2. Header/headerless режим, duplicate/empty/ragged columns, quoted multiline cells и blank rows.
3. Backend paging/streaming вместо full dataset payload.
4. Virtualized rows, sticky headers, resize, sort, filter, find, copy, row numbers и export slice.

#### XLS/XLSX

1. Semantic payload: raw/formatted value, type, formula/cached result, date/error, merged ranges,
   styles subset, sizes, hidden/frozen state, hyperlinks/comments.
2. Lazy sheet/range API и virtualization вместо fixed 12x28 preview.
3. Отдельно оценить LibreOffice PDF/HTML fidelity artifact, сохранив semantic grid для поиска.
4. Macro/external link/formula injection policy; zip-bomb and huge-dimension limits.

#### PDF/Office/EPUB/SQLite/text

1. Page/section navigation, honest fidelity warnings и search/copy contract по формату.
2. SQLite открывать read-only, ограничивать query time/rows/tables и не делать unconditional
   `COUNT(*)` на огромных таблицах.
3. EPUB/ODT/ODS ZIP entries читать bounded stream с path normalization и expansion ratio limits.
4. Text viewer поддерживает encoding selection, huge-file paging и сохраняет blank lines.

Критерий: каждый capability profile имеет valid/corrupt/large fixture, documented fidelity и
resource budget; preview не требует materialize всего файла.

### Этап 7. Processing platform security и reliability

1. Определить public product mode. Рекомендуемый минимум без accounts — signed anonymous
   session, owner-bound uploads/jobs/artifacts, короткий TTL и explicit privacy notice.
2. Ввести rate limits, concurrent-job quota, storage quota и request/body/parameter limits по
   session/IP; возвращать 429 с Retry-After.
3. Заменить unbounded virtual-thread submission на bounded queue + per-tool semaphore и
   backpressure. CPU-heavy worker concurrency должна соответствовать pod limits.
4. Сохранить upload/job/artifact metadata в PostgreSQL, сделать state transitions atomic и
   восстановление после restart. Подготовить multi-pod/rolling-update contract.
5. Stream artifact download через Resource/zero-copy, поддержать Range там, где нужно; добавить
   `no-store`, `nosniff` и безопасный filename.
6. Ввести central file intake: magic/signature sniffing, MIME/extension mismatch warning,
   allowlisted parser route и quarantine до обработки.
7. Общие budgets: input bytes, decoded pixels/pages/cells/entries, archive expanded bytes/ratio,
   process time, stdout size, result size и temp disk.
8. Native processors запускать в non-root isolated worker: dropped capabilities, seccomp,
   read-only root, writable job temp only, no network по умолчанию, CPU/RAM/pid limits.
9. Не включать опасные ImageMagick coders глобально. Выделить нужный conversion path и минимальную
   policy; по возможности PDF/PS обрабатывать отдельным sandboxed service.
10. Нормализовать public errors до code/message/correlation id; internal cause писать только в
    protected logs.
11. Cleanup должен удалять partial/temp/orphan files, быть idempotent и иметь metrics/alerts.

Критерий: abuse tests получают 413/429 без деградации pod; restart не теряет job state; parser
process не имеет root/network; artifact download не материализует файл в heap.

### Этап 8. Converter и Compression hardening

1. Сгенерировать scenario tests напрямую из capability matrix. Ни одна `available` пара не может
   существовать без fixture и result probe.
2. Проверять MIME/signature mismatch, orientation/color profile/alpha, animation, odd dimensions,
   codec/container compatibility, duration/FPS/bitrate и corrupt/empty input.
3. После conversion делать independent decode/probe и проверять target media type, dimensions,
   duration/pages и отсутствие неожиданного executable content.
4. Cache identity строить по backend content hash + normalized parameters.
5. Histories хранить metadata и artifact references, а не несколько больших Blob; исправить
   eviction/revoke order.
6. Compression добавить immutable request snapshot/revision token и точный retry этого snapshot.
7. Batch UX, estimates, warnings, cancellation и безопасные result names строить поверх общего
   processing task controller.

Критерий: вся matrix исполняется; output валидируется повторным decoder/probe; смена source и
eviction не дают stale state или memory leak.

### Этап 9. PDF Toolkit hardening

1. Добавить password per input для merge и отрицательные fixtures encrypted/corrupt PDF.
2. Усилить page expression/order tests: empty, reversed, duplicate, out-of-range, rotated,
   crop/media boxes и large page counts.
3. Проверить OCR language/DPI/timeouts, image-only input, orientation и searchable output.
4. Redaction postcondition: извлечённый текст не содержит terms; старые streams, annotations,
   attachments и metadata не сохраняют секрет; visual mask покрывает bounding boxes.
5. Переименовать `sign` в `Visible stamp` во всём UI/API либо отдельно спроектировать
   certificate-based signature с trust/timestamp/revocation model.
6. Добавить preview-before-apply и operation history, но не держать все PDF Blob в browser RAM.
7. Добавить request revision/cancel-on-replace/unmount и исправить result URL eviction.

Критерий: каждая операция имеет positive/negative/security tests и content-level postconditions,
а не только page count/media type.

### Этап 10. Dev Tools, infrastructure и документация

1. Hash больших файлов считать chunked/worker-based либо явно ограничить размер; UI не блокирует
   main thread.
2. Все secret-like fields volatile, masked и имеют clear-on-blur/navigation option.
3. Добавить common clipboard/download error UX и Web Crypto hard failure вместо weak fallback.
4. Frontend/backend containers запускать non-root; задать Kubernetes securityContext, probes,
   PodDisruptionBudget и NetworkPolicy.
5. Pin JDK/base images/actions по version/digest, проверять checksum downloads, генерировать SBOM,
   подписывать images и сканировать их до publish.
6. Nginx hardening, immutable caching только для hashed assets, no-store для shell/API-sensitive
   responses и отдельная ingress policy для actuator.
7. Обновить README и processing-platform docs: support levels, privacy/retention, limits,
   Markdown profile, stamp semantics и реальные non-production-grade ограничения.

## Зависимости этапов и параллельные потоки

Номера этапов задают приоритет и dependency boundary, а не один многомесячный линейный PR:

```text
Этап 0: emergency security gates
    |
    +--> Этап 1: общий quality harness (расширяется в каждом следующем PR)
    |
    +--> Markdown: Этап 2 render contract --> Этап 3 Editor
    |
    +--> UI/Viewer: Этап 4 foundation --> Этап 5 shell --> Этап 6 format profiles
    |
    +--> Platform: Этап 7 budgets/isolation --> Этапы 8 и 9

Этап 10 сопровождает каждый поток и закрывается после них
```

- Этап 0 блокирует feature work: сначала устраняются исполняемый XSS, XXE и отсутствие аварийных
  ограничений/isolation для public processing.
- Этап 1 не нужно ждать целиком: сначала добавляется минимальный security/E2E harness, затем
  fixture corpus растёт вместе с каждым capability.
- Этап 3 не начинает собственный Markdown renderer и зависит от contract этапа 2. CodeMirror
  integration можно готовить параллельно только за стабильным adapter interface.
- Этапы 5–6 зависят от foundation primitives этапа 4, чтобы не переносить второй раз ad-hoc CSS.
- Расширение тяжёлых conversion/PDF profiles зависит от admission limits и isolation этапа 7;
  browser-native presentation не обязана ждать durable PostgreSQL job registry.
- Каждый PR содержит миграционный/rollback путь. Старый contract удаляется только после contract
  tests и переключения обоих consumers; capability скрывается, если новый безопасный path не готов.

Первый следующий агент должен ограничиться первым PR: зафиксировать SEC-01 regression-тестами и
заменить исполняемый Markdown preview на безопасный временный fallback. Новый parser, UI refactor и
CodeMirror в этот emergency PR не входят.

## Рекомендуемая последовательность PR

1. `fix(editor): block markdown preview xss`
2. `fix(processing): enforce emergency public resource limits`
3. `fix(infra): isolate untrusted file processors`
4. `fix(processing): harden xml parsing and local cors cancellation`
5. `fix(security): remove persisted dev tool secrets and close public metrics`
6. `chore(deps): update vulnerable frontend toolchain`
7. `feat(testing): add e2e accessibility and security harness`
8. `feat(markdown): add versioned commonmark gfm render contract`
9. `feat(markdown): add safe obsidian compatibility profile`
10. `refactor(ui): introduce responsive workspace primitives`
11. `refactor(viewer): split renderer components and task lifecycle`
12. `feat(editor): adopt codemirror and format command registry`
13. Отдельные PR для CSV, workbook, PDF, durable processing и каждого следующего профиля.

Security fix, parser contract, UI foundation и конкретный renderer не следует смешивать в один
коммит. Каждый PR должен обновлять tests и docs вместе с изменённым поведением.

## Definition of Done всей программы

- CommonMark/GFM и Obsidian-compatible profiles документированы и проходят compliance/XSS corpus
  одинаково в Viewer и Editor; unsupported Obsidian syntax имеет явный безопасный fallback.
- На 320, 390, 768, 1024 и 1440 px нет horizontal overflow; ключевые flows соответствуют
  WCAG 2.2 AA.
- Нет high/critical known dependency advisory; SCA/container scan работают в CI.
- Public API имеет session ownership, rate/concurrency/storage limits и не раскрывает metrics.
- Untrusted parsers не работают root, не имеют произвольного network access и ограничены по
  ресурсам.
- Jobs переживают restart/rolling update либо честно имеют документированный durable boundary.
- Route leave/file replace/client timeout отменяют или отсоединяют server job предсказуемо;
  stale response не меняет UI.
- Blob/object URL и temp files освобождаются детерминированно, большие artifacts не читаются
  целиком в backend heap и не накапливаются в browser history.
- Каждая capability matrix запись подтверждена executable fixture test и честно описывает
  fidelity/ограничения.
