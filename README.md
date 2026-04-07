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

Локальный backend Docker image уже включает базовый набор инструментов обработки файлов и OCR для `eng`, `osd` и `rus`, поэтому Docker-режим остаётся самым простым способом поднять проект на ноутбуке без ручной настройки системных зависимостей.

## Стек

- **Backend:** Spring Boot, Spring Web MVC, Spring Data JPA, PostgreSQL
- **Frontend:** Vue 3, Vue Router, Pinia, Vite, TypeScript
- **Тесты и качество:** Vitest, ESLint, Prettier
- **Инфраструктура:** Docker Compose, GHCR, k3s

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

- прогоняет проверки;
- публикует `backend` и `frontend` образы в `ghcr.io/mattoyuzuru/jack/...`;
- для default branch обновляет тег `edge`.

Для первого bootstrap или обычного rollout на VPS:

```bash
ssh -tt -i ~/sshKeysDir/id_ed25519_hse matto@158.160.66.87 '
  set -euo pipefail
  cd /opt/jack
  sudo -v
  sudo git fetch --all --prune
  sudo git checkout main
  sudo git pull --ff-only
  sudo JACK_IMAGE_TAG=edge ./scripts/deploy/jack-bootstrap-k8s.sh
  sudo k3s kubectl -n jack rollout restart deployment/backend deployment/frontend
  sudo k3s kubectl -n jack rollout status deployment/backend --timeout=240s
  sudo k3s kubectl -n jack rollout status deployment/frontend --timeout=180s
  sudo k3s kubectl -n jack get ingress,svc,pods,pvc
'
```

Если нужно выкатить не `main`, а конкретную feature-ветку до merge, workflow также публикует branch tag вида `branch-feat-k8s-edge-deploy`, и его можно передать через `JACK_IMAGE_TAG=...`.

## Roadmap и Выполненная Работа

Проект уже прошёл несколько завершённых итераций:

| Итерация | Что было сделано                                                                           |
| -------- | ------------------------------------------------------------------------------------------ |
| `0`      | Поднят bootstrap проекта, контейнерный запуск и базовый workflow                           |
| `1`      | Собрана UI foundation и единое визуальное направление продукта                             |
| `2`      | Реализован viewer для изображений, документов, видео, аудио и служебных текстовых форматов |
| `3`      | Реализован конвертер для изображений, office/PDF и media-сценариев                         |
| `4`      | Добавлен отдельный compression workspace с несколькими режимами сжатия                     |
| `5`      | Поднят PDF toolkit с OCR, redaction, merge/split/rotate и password flows                   |
| `6`      | Добавлен multi-format editor с live preview, diagnostics и экспортом                       |
| `7`      | Собран набор browser-native dev tools и утилит                                             |
| `8`      | Проведён polish: cleaner UX, согласование терминов, cleanup policy и метрики               |

Если нужен более технический разбор processing-платформы, он вынесен отдельно в [docs/processing-platform.md](./docs/processing-platform.md).
