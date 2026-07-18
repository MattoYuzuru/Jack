# Threat Model Processing-платформы

## Границы Доверия

Браузер, имя файла, MIME type и содержимое upload считаются недоверенными. Backend является
единственным владельцем intake, capability policy, job state, artifacts и retention. PostgreSQL
хранит durable metadata, а artifact storage доступен только через owner-bound API. Native tools
запускаются как ограниченные дочерние процессы с timeout, bounded output и без shell interpolation.

Подписанная cookie идентифицирует анонимную processing-session, но не заменяет пользовательскую
аутентификацию. До появления account model нельзя использовать Jack для разграничения нескольких
людей, совместно работающих в одном browser profile.

## Реестр Угроз И Защит

| Vector                                          | Protection                                                                                                                                         | Automated test / gate                                                                       | Owner                           |
| ----------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- | ------------------------------- |
| Подмена owner id или чтение чужого artifact     | HMAC-SHA256 signed `HttpOnly`, `SameSite=Strict` cookie; owner predicate во всех upload/job/artifact запросах                                      | `ProcessingOwnershipApiTests`                                                               | Backend / Processing            |
| Cross-site mutation и отмена чужой job          | Origin-aware mutation gate требует `X-Jack-Request: processing`; allowlist CORS; frontend добавляет intent header только для mutation methods      | `ProcessingOwnershipApiTests`, `processing-client.spec.ts`                                  | Backend + Frontend / Processing |
| Upload с ложным расширением или MIME            | Streaming intake сверяет magic bytes, extension и declared MIME; capability resolution выполняется после intake                                    | `FileIntakeServiceTests`                                                                    | Backend / Intake                |
| Zip bomb, path traversal и nested archive       | Лимиты entry count, expanded bytes, ratio и depth; нормализация archive paths                                                                      | `FileIntakeServiceTests`, `DocumentPreviewApiTests`                                         | Backend / Intake                |
| Image/PDF/table decompression bomb              | Лимиты decoded pixels, pages, rows/cells, artifact/result bytes; paged range API и DOM window не более 200 строк                                   | `DocumentPreviewApiTests`, `PdfToolkitApiTests`, `viewer-renderers.spec.ts`                 | Backend + Frontend / Viewer     |
| Stored/preview XSS через HTML, EPUB или SVG     | Backend sanitization, external-reference rejection, sandboxed preview document и deny-by-default CSP; unsafe SVG только через server rasterization | `DocumentPreviewApiTests`, `FileIntakeServiceTests`                                         | Backend / Documents             |
| Command injection или зависший native parser    | `ProcessBuilder` с отдельными arguments, bounded stdout/stderr, timeout, cancellation и process-tree termination                                   | processing API integration tests, `ProcessingJobCancellationApiTests`                       | Backend / Native runtime        |
| Resource exhaustion очереди или storage         | Global bounded executor, queue capacity, per-session concurrent quota, request rate limit и storage quota                                          | `ProcessingFoundationApiTests`, `ProcessingOwnershipApiTests`                               | Backend / Processing            |
| Stale async result меняет новый workspace       | AbortSignal, request generation guard, job cancellation и object-URL eviction                                                                      | `viewer-session.spec.ts`, `processing-task-controller.spec.ts`, `processing-client.spec.ts` | Frontend / Workspaces           |
| Неполная PDF redaction сохраняет скрытые данные | Raster reconstruction; postconditions запрещают исходный text, annotations, attachments и metadata                                                 | `PdfToolkitApiTests`                                                                        | Backend / PDF                   |
| Утечка файлов после завершения работы           | Durable TTL для upload/job/artifact, scheduled cleanup и owner storage accounting                                                                  | `ProcessingStorageCleanupTests`                                                             | Backend / Lifecycle             |
| Запись runtime за пределами разрешённых путей   | Non-root UID, read-only root, bounded tmp/processing mounts, dropped capabilities и seccomp                                                        | production image smoke, Kustomize validation                                                | Platform / Runtime              |
| Network pivot из workload                       | Default-deny NetworkPolicy; frontend → backend, backend → PostgreSQL/DNS и ingress paths разрешены явно                                            | `kubectl kustomize`, manifest review                                                        | Platform / Kubernetes           |
| Уязвимая или подменённая supply chain           | Base images и Actions pinned by SHA/digest; npm/Dependency-Check/Trivy/gitleaks; SBOM, provenance и keyless Cosign signature                       | CI `verify`, `browser-quality`, `publish`                                                   | Platform / Supply chain         |

## Privacy, Retention И Incident Defaults

- Default TTL upload, job и artifact: 24 часа; production может только явно переопределить его
  через `JACK_PROCESSING_*_RETENTION_HOURS`.
- Default upload limit: 64 MiB. Default owner storage quota: 256 MiB. Default owner concurrency:
  2 jobs; global concurrency: 4 jobs; queue: 16 jobs.
- Artifact URLs не публичные и не являются bearer links: каждый download повторно проверяет owner.
- API responses с processing data получают `Cache-Control: no-store`; frontend shell также не
  кэшируется. Immutable cache разрешён только для content-hashed static assets.
- Для расследования нельзя переносить пользовательские artifacts в CI reports или application
  logs. Диагностика должна использовать ids, state transition и policy version без содержимого.

## Остаточный Риск

- Session model анонимный и browser-bound; account authentication, administrative audit trail и
  tenant-level authorization остаются prerequisite для multi-user deployment.
- Local/PVC artifact storage рассчитан на single-writer deployment. Горизонтальное
  масштабирование требует shared object storage, distributed queue/lease и lifecycle policy.
- Native converters уменьшают, но не устраняют риск parser vulnerabilities. Их images должны
  регулярно пересобираться и проходить blocking High/Critical scan.
- Видимый PDF stamp не является certificate-based digital signature. Redaction сохраняет
  визуальное представление, но намеренно удаляет selectable/vector layers.
