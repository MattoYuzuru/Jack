# Инвентаризация Viewer и Editor

Дата фиксации: 15 июля 2026 года.

Инвентаризация выполнена перед дальнейшей декомпозицией workspace-экранов. Размер сам по себе не
является причиной переноса: следующий модуль выделяется только при наличии отдельного контракта,
lifecycle и самостоятельного теста.

| Файл                                                             | Строк | Текущая ответственность                                                                  | Ключевые зависимости                                     | Автоматическая проверка                           | Следующий безопасный перенос                                                                      |
| ---------------------------------------------------------------- | ----: | ---------------------------------------------------------------------------------------- | -------------------------------------------------------- | ------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| `frontend/src/views/ViewerWorkspaceView.vue`                     |  3289 | Layout, intake, toolbar, inspector и разметка image/document/video/audio renderer-ов     | Viewer composables, presentation mapper, editor handoff  | Viewer a11y/overflow E2E на 5 ширинах             | Сначала format renderer-компоненты с typed props; root оставляет композицию и DOM refs            |
| `frontend/src/features/viewer/composables/useViewerWorkspace.ts` |   155 | Vue-адаптер session state, capability matrix и viewport transform                        | `viewer-session`, `viewer-runtime`, viewer registry      | Session/runtime unit tests и Viewer E2E           | Не расширять transport-логикой; capability hydration можно вынести после второго потребителя      |
| `frontend/src/features/viewer/application/viewer-session.ts`     |   177 | Единственный владелец revision, AbortController, stale result и release текущего preview | Typed `ViewerRuntime`, без Vue и DOM                     | 4 unit-теста: A→B race, cancel, Blob URL, dispose | Использовать как lifecycle boundary при выделении shell/stage                                     |
| `frontend/src/features/viewer/application/viewer-runtime.ts`     |   445 | Выбор preview strategy и нормализация typed result                                       | Registry, native/server preview adapters                 | 9 unit-тестов, включая поздний Blob URL           | Сохранять presentation-free; новые backend descriptors добавлять discriminated union-ветками      |
| `frontend/src/views/EditorWorkspaceView.vue`                     |   731 | Shell, responsive panels, toolbar routing и связывание готовых editor-компонентов        | `useEditorWorkspace`, editor components, UI foundation   | Editor a11y/overflow/file E2E и component tests   | Отделить shell CSS от panel composition только вместе с visual/component test                     |
| `frontend/src/features/editor/composables/useEditorWorkspace.ts` |   780 | Document state, preview debounce/abort, persistence, processing job и export lifecycle   | Editor application services, registry, processing client | 19 application unit-тестов и Editor E2E           | Следующий кандидат — отдельный `useEditorPreview`; persistence и job lifecycle не смешивать с ним |

## Проверка ресурсов и гонок

- Основной Viewer request lifecycle теперь находится в `viewer-session.ts`: новый выбор отменяет
  предыдущий, поздний результат освобождается, а cancellation не превращается в пользовательскую
  ошибку.
- `viewer-runtime.ts` владеет Blob URL результата и освобождает image/video/audio/PDF URL через
  `releaseViewerEntry`; это покрыто runtime и session unit-тестами.
- В `ViewerWorkspaceView.vue` остаётся один краткоживущий download URL для metadata export; он
  отзывается сразу после клика. Глобальные `fullscreenchange` и `keydown` listeners симметрично
  снимаются в `onBeforeUnmount`.
- Subtitle/poster Blob URL принадлежат `useViewerVideoPlayback` и очищаются при удалении,
  переполнении и unmount. Media element listeners в video/audio composables снимаются cleanup
  функцией соответствующего watcher.
- Editor preview использует debounce + AbortController + revision guard. Export URL отзывается
  таймером; persistence и preview timers очищаются при unmount. Этот lifecycle остаётся отдельным
  следующим refactor-блоком.
- Type-check не обнаруживает implicit `any` в перечисленных модулях. Повторяющиеся MIME/extension
  aliases находятся в feature registry, а не в view-разметке.
