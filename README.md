# <img src="./assets/brand/logo.svg" alt="Логотип Jack" width="196">

**Jack** (`Jack of all trades`) — это веб-приложение-мультитул для повседневной работы с файлами, документами, медиа и инженерными утилитами в одном интерфейсе.

Проект собран как монорепо:

- `backend/` — Spring Boot 4.0.5, Java 26
- `frontend/` — Vue 3, Vite, TypeScript
- `assets/brand/` — логотип и брендовые ассеты

## Что Умеет Jack

Сейчас в проекте уже доступны рабочие модули:

- **Viewer** — просмотр изображений, документов, видео и аудио
- **Converter** — конвертация изображений, офисных файлов, PDF, видео и аудио
- **Compression** — уменьшение размера файлов с режимами `maximum reduction`, `target size` и ручными настройками
- **PDF Toolkit** — merge, split, rotate, reorder, OCR, redact, password protect/unlock
- **Editor** — редактор для `Markdown`, `HTML`, `CSS`, `JavaScript`, `JSON`, `YAML` и обычного текста
- **Dev Tools** — кодировки, JWT, хеши, валидаторы, URL utilities, UUID/ULID и другие быстрые утилиты

## Быстрый Старт

Рекомендуемый способ запуска локально:

```bash
docker compose up -d --build
```

После старта будут доступны:

- frontend: `http://localhost:5173`
- backend: `http://localhost:8080`
- healthcheck: `http://localhost:8080/actuator/health`
- postgres: `localhost:5432`

Остановить окружение:

```bash
docker compose down
```

Остановить и удалить тома:

```bash
docker compose down -v
```

## Локальный Запуск Без Docker

Для запуска без Docker понадобятся:

- Java 26
- Node.js `^20.19.0 || >=22.12.0`
- PostgreSQL
- утилиты обработки файлов в `PATH`: `ffmpeg`, `ffprobe`, `ImageMagick`, `potrace`, `dcraw_emu`, `tesseract`

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

## Переменные Окружения

Шаблон конфигурации лежит в [.env.example](./.env.example).

Чаще всего достаточно проверить или переопределить:

- `JACK_FRONTEND_PORT`
- `JACK_BACKEND_PORT`
- `JACK_DB_PORT`
- `JACK_DB_NAME`
- `JACK_DB_USERNAME`
- `JACK_DB_PASSWORD`
- `JACK_API_BASE_URL`
- `JACK_WEB_ALLOWED_ORIGINS`
- `JACK_PROCESSING_STORAGE_ROOT`
- `JACK_PROCESSING_SESSION_SECRET` (production: случайное значение минимум 32 bytes)
- `JACK_PROCESSING_SESSION_COOKIE_SECURE` (production: `true` за TLS ingress)
- `JACK_PROCESSING_MAX_UPLOAD_SIZE_BYTES`, concurrency/queue/session storage limits и TTL

Локальный backend Docker image уже включает базовый набор инструментов обработки файлов и OCR для `eng`, `osd` и `rus`, поэтому Docker-режим остаётся самым простым способом поднять проект на ноутбуке без ручной настройки системных зависимостей.

## Стек

- **Backend:** Spring Boot, Spring Web MVC, Spring Data JPA, PostgreSQL
- **Frontend:** Vue 3, Vue Router, Pinia, Vite, TypeScript
- **Тесты и качество:** Vitest, Playwright/axe/visual, ESLint, Prettier, OWASP Dependency-Check,
  CycloneDX, Trivy и gitleaks
- **Инфраструктура:** Docker Compose, GHCR, k3s

## Processing, Privacy И Limits

File processing является backend-owned: browser управляет presentation/interaction, а backend
выполняет magic-aware intake, sanitization/conversion, bounded queue, owner checks и artifact
lifecycle. Anonymous owner хранится в signed `HttpOnly` cookie. Upload, job и artifact одного
browser profile недоступны другому owner id; это isolation boundary, но не account authentication.

Default policy:

- upload 64 MiB, result 128 MiB, owner storage 256 MiB;
- 4 global jobs, queue 16, не более 2 concurrent jobs на session;
- 40 MP image decode, 500 document pages, 20k rows / 400k table cells;
- archive 2048 entries, 128 MiB expanded, ratio 100, nesting depth 1;
- upload/job/artifact TTL 24 hours, scheduled cleanup физических файлов и metadata.

CSV/TSV, workbook и SQLite preview используют paged/range API; frontend удерживает не более 200
table rows в DOM. Подробные API, format fidelity и ограничения описаны в
[processing-platform.md](./docs/processing-platform.md), Markdown — в
[markdown-profile.md](./docs/markdown-profile.md), модель угроз — в
[processing-threat-model.md](./docs/processing-threat-model.md).

## Deploy На VPS Через GHCR И k3s

В репозитории теперь есть production-контур:

- GitHub Actions pipeline: `.github/workflows/ci-cd.yml`
- Kubernetes manifests: `k8s/jack/`
- bootstrap-скрипт для кластера: `scripts/deploy/jack-bootstrap-k8s.sh`
- production Dockerfiles: `backend/Dockerfile.prod` и `frontend/Dockerfile.prod`

Схема рассчитана на single-domain ingress:

- `https://jack.keykomi.com/` -> frontend
- `https://jack.keykomi.com/api/*` -> backend API
- `https://jack.keykomi.com/actuator/health` -> backend health endpoint

После push в GitHub workflow:

- прогоняет frontend/backend/E2E/visual/audit/Dependency-Check/SBOM gates и secret scan;
- собирает production images, блокирует publish при High/Critical Trivy finding;
- публикует SBOM/provenance и подписывает image digest keyless Cosign signature;
- публикует immutable tag `sha-<commit>`; `edge`/`main` остаются только удобными aliases default
  branch и не должны использоваться для контролируемого rollout.

### Deployment prerequisites

- PostgreSQL/PVC backup и достаточно места для processing TTL window;
- TLS ingress, корректный `web-allowed-origins` и namespace label для разрешённого ingress
  controller;
- `jack-secrets` с PostgreSQL values и случайным `processing-session-secret`; bootstrap создаёт
  отсутствующий ключ и не ротирует существующий без явного operator input;
- прошедшие CI images, проверенная Cosign signature и конкретный `JACK_IMAGE_TAG=sha-<commit>`;
- доступность Flyway migration, readiness/liveness probes и NetworkPolicy paths PostgreSQL/DNS;
- после rollout — smoke ownership, upload → job → artifact, cancel, range API и cleanup.

Пример подготовки bootstrap без выполнения deployment из этого репозитория:

```bash
export JACK_IMAGE_TAG=sha-<verified-commit>
export JACK_APP_HOST=https://jack.example.com
export JACK_PROCESSING_SESSION_SECRET="$(openssl rand -hex 32)"
# После отдельного approval оператора:
# sudo -E ./scripts/deploy/jack-bootstrap-k8s.sh
```

Manifests намеренно содержат `sha-RELEASE_SHA`: apply без подстановки immutable release SHA не
является production rollout. Horizontal scaling требует shared object storage, distributed
queue/lease и единого cleanup coordinator.

## Roadmap и Выполненная Работа

Проект уже прошёл несколько завершённых итераций:

Завершённая программа Viewer, Editor, UI/UX, security и processing-platform зафиксирована в
[плане quality hardening](./docs/quality-hardening-roadmap.md) и
[execution plan A–H](./docs/remaining-hardening-execution-plan.md).

| Итерация | Что было сделано                                                                            |
| -------- | ------------------------------------------------------------------------------------------- |
| `0`      | Поднят bootstrap проекта, контейнерный запуск и базовый workflow                            |
| `1`      | Собрана UI foundation и единое визуальное направление продукта                              |
| `2`      | Реализован viewer для изображений, документов, видео, аудио и служебных текстовых форматов  |
| `3`      | Реализован конвертер для изображений, office/PDF и media-сценариев                          |
| `4`      | Добавлен отдельный compression workspace с несколькими режимами сжатия                      |
| `5`      | Поднят PDF toolkit с OCR, redaction, merge/split/rotate и password flows                    |
| `6`      | Добавлен multi-format editor с live preview, diagnostics и экспортом                        |
| `7`      | Собран набор browser-native dev tools и утилит                                              |
| `8`      | Проведён polish: cleaner UX, согласование терминов, cleanup policy и метрики                |
| `9`      | Завершён A–H hardening: durable ownership, bounded formats, lifecycle, infra и supply chain |

Если нужен более технический разбор processing-платформы, он вынесен отдельно в [docs/processing-platform.md](./docs/processing-platform.md).
Версионированный CommonMark/GFM и Obsidian-compatible contract описан в
[docs/markdown-profile.md](./docs/markdown-profile.md).
