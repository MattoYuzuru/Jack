# Frontend

Стартовая Vue-база для Jack.

## Стек

- Vue 3
- Vite
- TypeScript
- Vue Router
- Pinia
- Vitest
- ESLint
- Prettier

## Команды

```bash
npm install
npm run dev
npm run build
npm run test:unit
npm run test:e2e:linux
npm run lint
```

## Visual И E2E Baseline

Канонические visual snapshots создаются и проверяются только в закреплённом Linux-образе
Playwright, совпадающем с CI. Обычный запуск всех browser-тестов:

```bash
npm run test:e2e:linux
```

Обновлять baseline можно только после просмотра `actual`, `expected` и `diff` для каждой
затронутой ширины. Массовое обновление без проверки layout, шрифтов и mobile overflow запрещено:

```bash
npm run test:e2e:linux -- --update-snapshots
```

Контейнер использует production build, фиксированные Chromium, locale, timezone, light scheme,
reduced motion и device scale factor. Временные `node_modules` живут в анонимном Docker volume
и удаляются вместе с контейнером; download cache npm переиспользуется с хоста.
При падении CI публикует `playwright-report` и `test-results` с screenshots, diff и trace.
