# План завершения оставшегося hardening Jack

Дата подготовки: 17 июля 2026 года.

Базовый commit: `713e844` (`main`, merge MR #43).

Основные источники: `docs/quality-hardening-roadmap.md`,
`docs/next-agent-improvement-plan.md`, `docs/workspace-module-inventory.md` и `AGENTS.md`.

## 1. Цель задания

Следующий агент должен не ограничиться одним безопасным refactor или одним форматом, а закрыть
все пункты этого документа в одной новой feature-ветке. Работу нужно делить на небольшие
логические conventional commits по мере выполнения, а не складывать в один финальный commit.
После полного набора проверок ветку нужно запушить в `origin` и подготовить к MR.

Задание заканчивается только тогда, когда одновременно выполнено следующее:

- Viewer и Editor разделены по ответственности и не оставляют stale jobs/Blob URL/state;
- большие таблицы и документы открываются через bounded backend contracts, а не целиком в RAM;
- uploads, jobs и artifacts привязаны к owner/session, имеют TTL, quotas и durable metadata;
- тяжёлые задачи исполняются с bounded concurrency, cancellation, cleanup и безопасными limits;
- Converter, Compression, PDF Toolkit и Dev Tools используют единый lifecycle;
- контейнеры, ingress/Nginx и Kubernetes workload имеют production security policy;
- все заявленные capability подтверждены fixtures и автоматическими тестами;
- документация соответствует фактическому поведению;
- локальные quality/security gates и GitHub Actions зелёные;
- feature-ветка запушена без force push.

Деплой, merge в `main` и изменение внешней инфраструктуры в это задание не входят. После push
нужно передать пользователю URL ветки/MR-кандидата, commits, результаты проверок и известные
ограничения.

## 2. Что уже сделано и не должно переделываться

На базовом commit уже существуют и должны переиспользоваться:

- единый CommonMark/GFM/Obsidian-safe Markdown contract `jack-markdown-1.1.0`;
- общий backend-generated `previewDocument` для Viewer и Editor;
- semantic и responsive GFM tables с desktop/mobile visual tests;
- CodeMirror-based Editor и базовый безопасный preview pipeline;
- `viewer-session.ts` как владелец revision, AbortController и cleanup текущего preview;
- abort-aware Viewer lifecycle и bounded in-memory handoff в Editor;
- общие UI foundation tokens/primitives, responsive/a11y harness и Linux Playwright baseline;
- backend/frontend unit, E2E, visual, audit, Dependency-Check и SBOM gates;
- исправленные Spring Boot/Framework, Jackson, Tomcat, Log4j, PostgreSQL, SQLite и PDFBox;
- кэш H2-базы OWASP Dependency-Check между GitHub Actions runner'ами.

Не создавать второй Markdown renderer, второй capability registry или параллельный frontend-only
processing pipeline. Новая бизнес-логика обработки файлов должна сначала оцениваться как
backend-кандидат и по возможности расширять существующие `upload/job/artifact/capability`,
`VIEWER_RESOLVE`, `DOCUMENT_PREVIEW`, `IMAGE_CONVERT`, `MEDIA_PREVIEW` и `METADATA_EXPORT`.

## 3. Обязательный старт и Git workflow

Перед любым изменением выполнить:

```bash
git fetch origin --prune
git switch main
git pull --ff-only origin main
git status --short --branch
test "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)"
git switch -c feat/complete-hardening-roadmap
```

Этот файл специально оставлен предыдущим агентом незакоммиченным. После создания feature-ветки
его можно включить в первый `docs(plan)` commit и затем актуализировать checkbox/status по мере
выполнения. Нельзя начинать работу из старой merged-ветки.

Правила работы с Git:

1. Перед каждым commit проверить `git status`, `git diff --check` и staged diff.
2. Коммитить сразу после завершения логического блока и его релевантных тестов.
3. Использовать только conventional commits; не смешивать backend platform, UI и infra в одном
   commit без неразрывной причины.
4. Не переписывать историю, не использовать force push и не откатывать несвязанные изменения.
5. Generated reports, Playwright output, временные fixtures, local DB/cache и secrets не
   коммитить.
6. Не останавливаться после отдельного этапа. Продолжать следующий пункт, пока весь план не
   завершён или не обнаружен реальный blocker, который нельзя безопасно разрешить из репозитория.
7. Push выполнить после полного локального gate. Если нужен промежуточный backup push, он не
   заменяет финальный push и итоговую проверку remote branch.

## 4. Сквозные архитектурные правила

- Backend остаётся source of truth для file intake, ownership, format limits, processing,
  artifacts, TTL, retries и capability availability.
- Frontend отвечает за screen state, presentation, viewport/playback controls, optimistic UX,
  bounded local filtering и download/share interaction.
- Любой long-running flow имеет один contract:
  `validate → submit revision → queued/running → artifact ready | cancelled | failed | expired`.
- Любая новая операция обязана иметь limit, timeout, cancellation, idempotent cleanup и typed
  public error. Raw exception message наружу не возвращается.
- Не материализовать большие uploads/downloads, CSV, workbook, archive или PDF целиком без
  доказанного малого upper bound.
- Любой artifact доступен только текущему owner/session либо по отдельному scope-limited share
  token с TTL.
- Для сложной или неочевидной логики добавлять короткие комментарии на русском, объясняющие
  ограничение или причину решения.
- UI продолжает существующий soft industrial neumorphism: sand/teal/coral foundation,
  существующие tokens, крупная иерархия, adaptive desktop/tablet/mobile и WCAG 2.2 AA.
- Для каждого изменения поведения одновременно обновлять fixture/contract tests и документацию.

## 5. Этап A — завершить декомпозицию Viewer и Editor

### A1. Viewer renderers

- Вынести из `frontend/src/views/ViewerWorkspaceView.vue` отдельные renderer-компоненты:
  image, Markdown/HTML/text, PDF/document, table/workbook/database, video и audio.
- Компоненты получают typed props и events; они не должны напрямую владеть transport, polling
  или global capability state.
- Root view оставляет композицию, DOM refs, toolbar/inspector routing и подключение уже
  существующего `viewer-session`.
- Не переносить большой scoped CSS механически без component/visual test на соответствующий
  renderer.
- Toolbar строить по активному renderer и не показывать нерелевантные image/media controls.
- Нормализовать clipboard/fullscreen/PiP failures, focus restoration после picker и accessible
  drag/drop status.

Проверки:

- unit/component tests каждого renderer: empty, loading, success, partial и error;
- rapid A→B switch, route leave, late response и Blob URL release;
- Viewer keyboard/a11y/overflow/visual E2E на 320, 390, 768, 1024 и 1440 px.

Рекомендуемые commits:

- `refactor(viewer): extract typed format renderers`
- `fix(viewer): normalize renderer interaction cleanup`

### A2. Editor lifecycle и commands

- Вынести preview debounce/abort/revision в отдельный `useEditorPreview`; persistence и export
  lifecycle не смешивать с preview.
- Довести command registry: heading levels, bold/italic/strike, inline/fenced code, quote,
  ordered/bullet/task lists, link, image, table, footnote и thematic break.
- Сохранять selection и корректный undo/redo; Enter/Tab поддерживают list continuation,
  termination и nesting.
- Templates фильтровать по формату, добавить search и confirmation перед заменой dirty draft.
- Extension имеет приоритет над generic MIME; mismatch показывается пользователю.
- Persistence сделать opt-in, versioned и bounded; обработать quota errors. Секретные данные и
  полный большой document не сохранять автоматически.
- Добавить encoding/newline contract и round-trip для UTF-8 BOM, LF/CRLF и Unicode.
- Preview HTML/CSS не должен загружать remote resources; sandbox не расширять без тестируемой
  необходимости.

Проверки:

- unit tests command registry, selection/undo, preview race и persistence quota;
- component/E2E tests toolbar, templates, tabs semantics и dirty confirmation;
- XSS/remote-resource corpus для Markdown/HTML/CSS preview;
- large draft interaction test без stale diagnostics.

Рекомендуемые commits:

- `refactor(editor): isolate preview lifecycle`
- `feat(editor): complete format-aware command registry`
- `fix(editor): bound draft persistence and format detection`

## 6. Этап B — owner-bound и durable processing platform

Этот этап выполняется до расширения тяжёлых format profiles, потому что новые artifacts не должны
появляться поверх небезопасного in-memory access contract.

### B1. Session ownership

- Зафиксировать product mode: короткоживущая anonymous session с подписанной HttpOnly/Secure/
  SameSite cookie или эквивалентным token contract; не хранить основной secret во frontend.
- Upload, job и artifact metadata содержат owner/session id, creation/expiry, parent upload,
  capability, policy version и correlation id.
- Проверять owner на status, cancel, resolve, preview/range и download до чтения/обработки.
- Для чужого, истёкшего и отсутствующего id возвращать одинаковый безопасный typed response.
- Share link, если он нужен текущему UI, реализовать отдельным signed scope token с TTL и
  revocation; artifact id не является share credential.

### B2. PostgreSQL state и миграции

- Перенести authoritative metadata jobs/uploads/artifacts из `ConcurrentHashMap` в PostgreSQL.
- Добавить versioned schema migrations, indexes по owner/status/expiry и rollback notes.
- State transitions `QUEUED → RUNNING → COMPLETED|FAILED|CANCELLED` делать атомарными.
- При startup reconciliation корректно обрабатывать orphan RUNNING jobs и partial artifacts.
- Cleanup expired/temp/orphan state сделать идемпотентным; добавить backlog/lag metrics.
- Artifact bytes могут оставаться в storage/PVC, но запись БД обязана однозначно описывать
  storage id, size, hash, media type, owner и expiry.

### B3. Queue, quotas и backpressure

- Заменить безлимитный запуск тяжёлых native jobs на bounded queue и per-tool semaphore.
- Задать per-session/IP concurrent-job quota, storage quota, upload/body limits и Retry-After.
- Разделить CPU-heavy worker concurrency с учётом pod CPU/RAM/pid limits.
- Retry разрешать только transient/idempotent задачам; reuse key строить по content hash,
  normalized parameters, owner, capability и policy version.
- Graceful shutdown прекращает admission, завершает/отмечает running jobs и не теряет durable
  state.

### B4. Platform tests

Обязательные backend integration/security tests:

- две сессии не читают, не отменяют и не скачивают artifacts друг друга;
- expired/missing/foreign id не раскрывают существование объекта;
- quota и queue saturation дают 413/429, не создавая job;
- restart/reconciliation не теряет terminal jobs и не дублирует side effects;
- cleanup повторяем и безопасен после partial failure;
- share token ограничен artifact/action/TTL и отзывается;
- correlation id присутствует в typed error, но internal cause не попадает в ответ.

Рекомендуемые commits:

- `feat(platform): bind processing resources to sessions`
- `feat(platform): persist job and artifact lifecycle`
- `feat(platform): add bounded admission and quotas`
- `test(platform): cover ownership restart and cleanup boundaries`

## 7. Этап C — central intake, streaming и resource budgets

- Создать единый intake service для magic/signature sniffing, MIME/extension mismatch,
  normalized filename и allowlisted parser route.
- Extension/MIME использовать как UX hint, а не security boundary.
- Upload и download сделать streaming; не использовать `readAllBytes`/полный heap buffer для
  потенциально больших artifacts.
- Download задаёт безопасные `Content-Disposition`, media type, `nosniff`, `no-store` и bounded
  Range contract, не раскрывает filesystem path.
- Ввести общие budgets до allocation: input bytes, decoded pixels, pages, rows/cells, archive
  entries, expanded bytes/ratio, nested depth, process time, stdout/result/temp size.
- ZIP/EPUB/ODT/ODS entries читать bounded stream с path normalization; закрыть traversal,
  Unicode/control filename cases и decompression bombs.
- Native processors запускать через общий isolated execution adapter: timeout, kill process tree,
  stdout/stderr cap, cancellation и cleanup.
- Нормализовать error codes: unsupported, mismatch, too-large, quota, timeout, corrupt,
  cancelled, expired и internal; message не содержит raw path/exception.

Проверки:

- misleading MIME/extension, path traversal, nested archive и CI-safe zip-bomb fixtures;
- slow/oversized upload, invalid/oversized Range и cancelled streaming download;
- pixel/page/cell/time limits с проверкой, что опасный parser не был запущен;
- temp directory остаётся пустым после success/failure/cancel/timeout.

Рекомендуемые commits:

- `feat(platform): centralize bounded file intake`
- `feat(platform): stream owner-bound artifact downloads`
- `fix(processing): enforce parser and native process budgets`

## 8. Этап D — bounded table, workbook и database profiles

### D1. CSV/TSV

- Backend descriptor определяет encoding/BOM, delimiter, quote, header mode, column metadata,
  ragged/duplicate/empty fields и warnings без отправки всего dataset.
- Добавить paged/range API с row/byte/time limits, stable cursor/revision и owner checks.
- Сохранять blank rows и quoted multiline cells; headerless/manual override является частью
  нормализованных параметров.
- Frontend использует virtualized rows, sticky header, row numbers, bounded sort/filter/find,
  copy и export slice. Горизонтальный scroll допускается только внутри grid.

### D2. XLS/XLSX/ODS

- Возвращать lazy sheet/range contract: raw/formatted value, type, formula/cached result,
  date/error, merged ranges, safe style subset, sizes, hidden/frozen state, links/comments.
- Не обходить весь workbook ради preview 12x28; лимитировать dimensions и materialized cells.
- Зафиксировать macro, external link, formula injection, encrypted/zip-bomb policy.
- При необходимости использовать LibreOffice fidelity artifact отдельно от semantic grid, не
  подменяя им search/copy/data contract.

### D3. SQLite

- Открывать только read-only, запретить ATTACH и mutation; ограничить tables/rows/query time и
  output bytes.
- Не делать unconditional `COUNT(*)`; использовать bounded estimate/unknown state.
- Добавить cancellation, malformed DB/huge BLOB/long query/ownership tests.

Проверки всех профилей:

- tiny, representative, large-but-CI-safe, malformed и hostile fixture;
- backend contract tests paging/cursor/limits;
- frontend component/E2E grid tests на desktop/mobile и keyboard/screen reader semantics;
- performance assertion: preview не materialize весь source и DOM не растёт со всем dataset.

Рекомендуемые commits:

- `feat(viewer): add paged delimited table preview`
- `feat(viewer): add bounded workbook range contract`
- `fix(viewer): harden read-only database preview`

## 9. Этап E — document, PDF, EPUB, SVG и media renderers

- PDF/Office/EPUB profiles получают честный fidelity/partial warning, page/section navigation,
  bounded search/copy и renderer-specific controls.
- PDF preview учитывает page count, rotation, crop/media boxes, encrypted/corrupt/large cases.
- EPUB/HTML/SVG sanitization запрещает scripts, event handlers, forms, iframe/object/embed,
  dangerous SVG/CSS и unsafe URL schemes.
- External resources по умолчанию не загружать; если нужен proxy, он обязан иметь SSRF policy,
  DNS/IP validation, redirects/content/size/time limits и no private network access.
- Images учитывают EXIF orientation, alpha, color profile, animation и decoded pixel budget.
- Video/audio/subtitles/poster/waveform имеют duration/track/size limits, cancellation и полный
  release listeners/Blob URLs.
- Document search связывается с navigation/highlight через безопасный typed message/model
  contract; не расширять iframe sandbox без причины.

Проверки:

- XSS/SSRF SVG/HTML/EPUB corpus без script/navigation/network request;
- encrypted/corrupt/rotated/large PDF fixtures;
- malformed/oversized image/media/subtitle fixtures и route-leave cleanup;
- renderer component, a11y, responsive и visual tests.

Рекомендуемые commits:

- `feat(viewer): harden bounded document renderers`
- `fix(viewer): isolate svg and external resource previews`
- `fix(viewer): enforce media lifecycle budgets`

## 10. Этап F — единый lifecycle остальных workspaces

### F1. Общий frontend task controller

- Converter, Compression, PDF Toolkit, Editor export и тяжёлые Dev Tools используют один
  revision/cancel/retry/cleanup contract.
- Новый submit отменяет superseded request/job; unmount/route leave отменяет polling и server
  job; timeout не прекращает только ожидание клиента.
- Retry воспроизводит immutable snapshot исходного запроса.
- Result history хранит metadata/artifact references, а не много больших Blob. Eviction сначала
  определяет удалённые entries, затем гарантированно освобождает URL/resources.
- UI различает invalid input, backend failure, cancelled, quota/limit и expired artifact.

### F2. Converter и Compression

- Исполнять всю capability matrix через fixtures; `available` невозможен без result probe.
- Cache/reuse identity строить по backend content hash и normalized parameters.
- Output проверять независимым decoder/probe: magic/media type, dimensions/pages/duration,
  orientation/alpha/animation и отсутствие executable content.
- Compression возвращает attempts, actual size/ratio/quality и reason недостижимого target;
  iterative loop имеет max attempts/time/CPU/RAM и cancellation.
- UX явно показывает потерю alpha/metadata/animation или смену формата до download.

### F3. PDF Toolkit

- Password поддерживается для каждого encrypted input и остаётся только volatile.
- Page expression/order tests покрывают empty/reversed/duplicate/out-of-range, rotation,
  crop/media boxes и large counts.
- OCR имеет language/DPI/timeouts/cancel и image-only fixtures.
- Redaction postcondition проверяет extracted text, streams, annotations, attachments и metadata.
- Переименовать косметический `sign` в `Visible stamp`; не заявлять digital signature без
  certificate/trust/timestamp/revocation model.
- Preview-before-apply и history не удерживают все PDF Blob в browser RAM.

### F4. Dev Tools и metadata

- Hash больших файлов считать chunked worker/server-stream с progress/cancel и known vectors.
- JSON/YAML/XML validation parser-backed с line/column; format/minify сохраняют выбранные
  encoding/newline rules.
- Secret-like inputs volatile/masked и не попадают в storage, analytics или logs.
- Clipboard/download errors используют общий accessible interaction primitive.
- При отсутствии Web Crypto показывать hard failure, а не weak/zero-entropy fallback.

Рекомендуемые commits:

- `refactor(processing): unify cancellable task state`
- `test(converter): execute declared capability matrix`
- `fix(compression): preserve immutable retry requests`
- `fix(pdf): enforce secure operation postconditions`
- `fix(devtools): bound file hashing and secret state`

## 11. Этап G — production infrastructure и supply chain

- Frontend/backend runtime images запускаются non-root; pin base/JDK/native tool versions и
  digests/checksums, убрать shell/debug tooling из production stage где возможно.
- Kubernetes: `runAsNonRoot`, explicit uid/gid, `allowPrivilegeEscalation: false`, dropped
  capabilities, `RuntimeDefault` seccomp, read-only root filesystem, bounded writable temp,
  resources, probes, PDB и NetworkPolicy.
- Native parser worker по умолчанию не имеет network egress и широкого filesystem access.
- Nginx/ingress: CSP, HSTS, `nosniff`, Referrer/Permissions policy, frame protection, скрытая
  version, immutable cache только hashed assets и `no-store` для shell/API-sensitive responses.
- Actuator externally exposes only minimum health; metrics доступны только protected internal
  path/policy.
- CORS/CSRF/cookie policy соответствует выбранной session model; DELETE cancel работает в dev и
  production.
- CI: сохранить audit/Dependency-Check/SBOM, добавить container image scan до publish, secret
  scan и подпись images/provenance. High/critical нельзя suppress без owner, причины и expiry.
- Cache Dependency-Check не должен отключать обновление NVD или полный анализ dependency graph.

Проверки:

- `actionlint`, Compose validation и production Docker builds;
- `kubectl kustomize k8s/jack` и server/client schema validation, если доступно;
- smoke container запускается non-root и не пишет вне разрешённых mounts;
- headers/actuator/network-policy integration tests;
- image scan не содержит необработанных high/critical findings.

Рекомендуемые commits:

- `fix(containers): run production services as non-root`
- `fix(k8s): enforce workload security boundaries`
- `fix(nginx): add production security headers`
- `ci(security): scan and attest published images`

## 12. Этап H — финальный product-quality sweep

- Проверить WCAG 2.2 AA: keyboard order, focus, 44 px targets, semantic tabs/grids/dialogs,
  labels, live regions, reduced motion, forced colors и screen-reader names.
- На 320/390/768/1024/1440 px не должно быть page-level horizontal overflow; table/code stage
  может иметь локальный scroll с понятным affordance.
- Зафиксировать performance budgets: route JS/CSS, DOM nodes large grid, preview latency fixture
  tiers и stale-update guards. Тяжёлые библиотеки не входят в initial bundle.
- Capability/format matrix генерирует или проверяет fixture coverage; удалить ложные `available`.
- Добавить threat-model таблицу `vector → protection → automated test → owner`.
- Обновить README, platform/API/format/Markdown docs, privacy/retention/TTL/limits, deployment
  prerequisites, known limitations и оба roadmap-документа.
- Пометить этап завершённым только по факту тестов, а не по наличию каркаса или TODO.

Рекомендуемые commits:

- `test(security): cover processing threat model`
- `test(e2e): enforce workspace accessibility and budgets`
- `docs(platform): document ownership limits and retention`
- `docs(roadmap): mark completed hardening stages`

## 13. Проверки перед каждым commit

Выбирать минимальный достаточный набор по изменённому блоку:

- frontend application/component: targeted Vitest, `type-check`, lint и format check;
- backend domain/API/storage: targeted test class, затем `./gradlew test` перед завершением этапа;
- UI/renderer: targeted E2E плюс desktop/mobile visual inspection;
- workflow/infra: `actionlint`, `docker compose config`, Kustomize/schema validation;
- dependency change: dependency insight, backend tests, Dependency-Check и SBOM.

Если тест упал, сначала установить причину. Не обновлять visual snapshot вслепую и не добавлять
suppression только ради зелёного gate.

## 14. Полный gate перед финальным push

Из корня репозитория выполнить:

```bash
npm --prefix frontend ci
npm --prefix frontend audit --audit-level=high
npm --prefix frontend run lint:check
npm --prefix frontend run format:check
npm --prefix frontend run test:unit -- --run
npm --prefix frontend run build

(cd backend && ./gradlew --no-daemon test)
(cd backend && ./gradlew --no-daemon dependencyCheckAnalyze)
(cd backend && ./gradlew --no-daemon cyclonedxBom)

npm --prefix frontend run test:e2e:linux
actionlint .github/workflows/ci-cd.yml
docker compose config
kubectl kustomize k8s/jack >/dev/null
```

Дополнительно собрать production images и выполнить добавленный container/security scan. Если
локально нет `NVD_API_KEY`, использовать существующий локальный кэш и дождаться обновления; не
коммитить `autoUpdate=false` и не ослаблять CVSS threshold. E2E artifacts и временные Docker
volumes после проверки удалить.

Проверить итог:

```bash
git status --short
git diff --check
git log --oneline origin/main..HEAD
```

Working tree перед push должен содержать только осознанные tracked changes/commits; временного
мусора быть не должно.

## 15. Финальный push и handoff

После успешного полного gate:

```bash
git push -u origin feat/complete-hardening-roadmap
```

После push проверить, что remote branch указывает на локальный `HEAD`, и подготовить MR candidate.
Не выполнять merge или deployment без отдельной инструкции пользователя.

Итоговый отчёт должен содержать:

1. Branch, base/head SHA и ссылку на remote/MR.
2. Список logical commits и назначение каждого.
3. Какие архитектурные/security решения приняты и почему.
4. Таблицу выполненных этапов A–H и их acceptance evidence.
5. Точные команды и результаты unit/integration/E2E/visual/security/infra gates.
6. Состояние CI и ссылки на artifacts/reports.
7. Миграционные и deployment prerequisites без выполнения самого deployment.
8. Остаточные ограничения. Если обязательный пункт не завершён, нельзя писать, что весь план
   выполнен: нужно явно назвать blocker и не маскировать его TODO или suppression.
