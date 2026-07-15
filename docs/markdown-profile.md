# Markdown profile Jack

Текущая версия контракта: `jack-markdown-1.0.0`.

Viewer и Editor используют один backend endpoint `POST /api/markdown/render`. Контракт возвращает
sanitized HTML, outline/anchors, warnings, detected features и unresolved Obsidian references.
Frontend не выполняет собственный Markdown parser и показывает HTML в iframe без
`allow-same-origin`.

## Обязательный профиль

`commonmark-gfm` включает CommonMark blocks/inline syntax и GFM tables, task lists,
strikethrough и autolinks. Raw HTML всегда экранируется. Разрешены только `http`, `https` и
`mailto` links; scripts, event attributes, forms, iframe/object/embed и unsafe URL schemes
удаляются allowlist sanitizer.

External images не загружаются автоматически. Вместо `<img>` контракт возвращает читаемый
placeholder и warning, поэтому preview не делает скрытых внешних запросов.

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
читаемым source. Math и Mermaid в версии `1.0.0` выключены.
