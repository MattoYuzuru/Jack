# Markdown profile Jack

Текущая версия контракта: `jack-markdown-1.1.0`.

Viewer и Editor используют один backend endpoint `POST /api/markdown/render`. Контракт возвращает
sanitized HTML fragment, полный `previewDocument`, outline/anchors, warnings, detected features и
unresolved Obsidian references. `previewDocument` содержит фиксированные CSP и стили профиля;
Viewer и Editor показывают один и тот же документ в iframe без `allow-same-origin`. Frontend не
выполняет собственный Markdown parser и не добавляет пользовательский HTML.

## Обязательный профиль

`commonmark-gfm` включает CommonMark blocks/inline syntax и GFM tables, task lists,
strikethrough и autolinks. Raw HTML всегда экранируется. Разрешены только `http`, `https` и
`mailto` links; scripts, event attributes, forms, iframe/object/embed и unsafe URL schemes
удаляются allowlist sanitizer.

External images не загружаются автоматически. Вместо `<img>` контракт возвращает читаемый
placeholder и warning, поэтому preview не делает скрытых внешних запросов.

### GFM tables

- `thead` получает `th scope="col"`, поэтому заголовки связаны с колонками семантически;
- `:---`, `:---:` и `---:` сохраняются как allowlisted alignment classes после sanitization;
- таблица помещается в именованный `role="region"` с `tabindex="0"` и горизонтальным scroll;
- длинные ячейки ограничены по ширине и переносятся, не расширяя весь workspace;
- desktop и mobile presentation используют одинаковый `previewDocument` и проверяются visual E2E.

Safe extensions включаются явно через `extensions`:

- `footnotes`;
- `definition-lists`;
- `heading-anchors`;
- `toc`;
- `highlight` (`==text==`);
- `sub-sup`.

## Obsidian-compatible профиль

`obsidian-safe` дополнительно распознаёт YAML frontmatter, `[[wikilinks]]`, `![[embeds]]`,
callouts, tags и block references. Без явно выбранного vault/набора вложений Jack не угадывает
путь: link/embed попадает в `unresolvedReferences`, а в документе остаётся безопасный fallback.

Профиль намеренно не обещает Obsidian plugin API, Dataview, Templater, Canvas, пользовательский
JavaScript, CSS snippets или поведение сторонних plugins. Неизвестная конструкция остаётся
читаемым source. Math и Mermaid в версии `1.1.0` выключены.
