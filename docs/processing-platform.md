# Processing Platform

## Зачем Это Нужно

Jack больше не строится как продукт, где весь тяжёлый runtime живёт в браузере.
Backend теперь является processing-platform, которая:

- принимает исходные файлы
- оркестрирует job lifecycle
- собирает preview/result artifacts
- держит capability matrix как источник правды
- отдаёт frontend уже продуктовые payload-контракты

Идея простая: frontend остаётся быстрым interaction/UI слоем, а backend забирает вычисления,
валидацию, sanitzation, format intelligence и long-running processing.

## Что Уже Работает

### Foundation

- `POST /api/uploads`
- `GET /api/uploads/{id}`
- `POST /api/jobs`
- `GET /api/jobs/{id}`
- `DELETE /api/jobs/{id}`
- `GET /api/jobs/{id}/artifacts/{artifactId}`
- `GET /api/capabilities/viewer`
- `GET /api/capabilities/converter`
- `GET /api/capabilities/platform`

### Активные Job Type

- `UPLOAD_INTAKE_ANALYSIS`
- `MEDIA_PREVIEW`
- `IMAGE_CONVERT`
- `DOCUMENT_PREVIEW`
- `METADATA_EXPORT`
- `VIEWER_RESOLVE`

### Processing Domains

- media processing через `ffprobe` / `ffmpeg`
- heavy imaging через `ImageMagick`, `Ghostscript`, `potrace`, `libraw`
- document intelligence для PDF / office / archive / SQLite preview
- metadata inspect/export для image/audio flows
- unified viewer resolve route
- server-owned capability matrix для viewer, converter и future modules

## Разделение Ответственности

### Backend

Backend должен владеть тем, что реально является продуктовой и файловой логикой:

- file intake и hash-based identity
- preview / convert / extract / sanitize / inspect pipelines
- metadata read/write policy
- format/scenario/preset capability rules
- job status, progress, retry/cancel contract
- artifact storage и повторное скачивание результата

### Frontend

Frontend должен оставаться thin, но удобным слоем:

- workspace state
- local interaction и form UX
- browser-native rendering там, где он уже достаточен
- zoom / rotate / playback / subtitle / search / copy / download UX
- progress presentation поверх backend job contract

## Активные Маршруты

### Viewer

Viewer теперь использует backend-first contract для всех server-assisted форматов:

- legacy video/audio контейнеры идут через `VIEWER_RESOLVE`
- HEIC / TIFF / RAW идут через `VIEWER_RESOLVE`
- document stack идёт через `VIEWER_RESOLVE`
- metadata inspect/export переиспользует `METADATA_EXPORT`

На клиенте остались native rendering, state и interaction tooling.

### Converter

Converter работает как backend-first route:

- supported сценарии уходят в `IMAGE_CONVERT`
- frontend держит progress, retry, cancel и artifact reuse
- capability/source-target/preset matrix приходит с backend

## Platform Reuse Для Следующих Модулей

`GET /api/capabilities/platform` описывает, как следующие roadmap-модули должны стартовать
не с нуля, а поверх уже существующей processing-platform:

- `Compression`
  - reuse: `IMAGE_CONVERT`, `MEDIA_PREVIEW`, capability matrix, artifact lifecycle
- `PDF Toolkit`
  - reuse: `DOCUMENT_PREVIEW`, `IMAGE_CONVERT`, `VIEWER_RESOLVE`
- `Multi-Format Editor`
  - reuse: `DOCUMENT_PREVIEW`, `METADATA_EXPORT`, safe export contracts
- `Batch Conversion`
  - reuse: upload/job/artifact foundation и `IMAGE_CONVERT`
- `OCR`
  - reuse: `DOCUMENT_PREVIEW`, `IMAGE_CONVERT`, общий job/artifact flow
- `Office/PDF Conversion`
  - reuse: document contracts, viewer resolve, capability-driven routing

Это значит, что новые модули должны добавлять свою product-specific orchestration,
а не заново собирать browser-heavy runtime.

## Правило Для Новых Фич

Новая логика должна уходить в backend, если она:

- изменяет файл или строит новый artifact
- валидирует, санитизирует или извлекает структурированные данные
- тяжёлая по CPU/RAM
- требует единых лимитов, retry, observability или audit
- должна одинаково работать между браузерами и устройствами
- влияет на capability rules и source of truth продукта

На frontend её стоит оставлять только тогда, когда это действительно interaction/presentation
или безопасный browser-native сценарий без заметной выгоды от server orchestration.

## Что Ещё Не Production-Grade

Текущая processing-platform уже рабочая, но ещё не закрывает весь production hardening:

- постоянное хранилище вместо локального temp storage
- TTL cleanup policy
- очередь и retry policy
- quota / rate limit / audit
- observability и metrics
- специализированные job types для compression, OCR, PDF toolkit и office conversion
