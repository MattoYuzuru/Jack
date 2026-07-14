import { EditorSelection, EditorState } from '@codemirror/state'
import { EditorView } from '@codemirror/view'
import { afterEach, describe, expect, it } from 'vitest'
import { applyEditorCommand, continueMarkdownList } from '../editor-commands'

const views: EditorView[] = []

afterEach(() => {
  views.splice(0).forEach((view) => view.destroy())
  document.body.innerHTML = ''
})

describe('CodeMirror editor commands', () => {
  it('wraps every selected range and preserves selections', () => {
    const view = createView(
      'alpha beta',
      EditorSelection.create([EditorSelection.range(0, 5), EditorSelection.range(6, 10)]),
    )

    expect(applyEditorCommand(view, 'md-bold')).toBe(true)
    expect(view.state.doc.toString()).toBe('**alpha** **beta**')
    expect(view.state.selection.ranges).toHaveLength(2)
  })

  it('toggles existing inline markers off', () => {
    const view = createView('**alpha**', EditorSelection.single(2, 7))

    applyEditorCommand(view, 'md-bold')

    expect(view.state.doc.toString()).toBe('alpha')
    expect(view.state.sliceDoc(view.state.selection.main.from, view.state.selection.main.to)).toBe(
      'alpha',
    )
  })

  it('continues and ends Markdown task lists', () => {
    const view = createView('- [x] done', EditorSelection.single(10))
    expect(continueMarkdownList(view)).toBe(true)
    expect(view.state.doc.toString()).toBe('- [x] done\n- [ ] ')

    expect(continueMarkdownList(view)).toBe(true)
    expect(view.state.doc.toString()).toBe('- [x] done\n')
  })

  it('continues ordered lists for every cursor', () => {
    const view = createView(
      '2. first\n7. second',
      EditorSelection.create([EditorSelection.cursor(8), EditorSelection.cursor(18)]),
    )

    expect(continueMarkdownList(view)).toBe(true)
    expect(view.state.doc.toString()).toBe('2. first\n3. \n7. second\n8. ')
    expect(view.state.selection.ranges).toHaveLength(2)
  })

  it('toggles line commands across multiple selections', () => {
    const view = createView(
      'alpha\nbeta',
      EditorSelection.create([EditorSelection.cursor(0), EditorSelection.cursor(6)]),
    )

    applyEditorCommand(view, 'md-quote')
    expect(view.state.doc.toString()).toBe('> alpha\n> beta')

    applyEditorCommand(view, 'md-quote')
    expect(view.state.doc.toString()).toBe('alpha\nbeta')
  })

  it('keeps JSON snippets valid in an existing root object', () => {
    const view = createView('{"existing":true}', EditorSelection.single(0))

    expect(applyEditorCommand(view, 'json-field')).toBe(true)
    expect(JSON.parse(view.state.doc.toString())).toEqual({ existing: true, key: 'value' })
  })

  it('does not mutate an already invalid structured document', () => {
    const view = createView('{ invalid', EditorSelection.single(0))

    expect(applyEditorCommand(view, 'json-field')).toBe(false)
    expect(view.state.doc.toString()).toBe('{ invalid')
  })
})

function createView(doc: string, selection: EditorSelection): EditorView {
  const parent = document.createElement('div')
  document.body.append(parent)
  const view = new EditorView({
    parent,
    state: EditorState.create({
      doc,
      selection,
      extensions: [EditorState.allowMultipleSelections.of(true)],
    }),
  })
  views.push(view)
  return view
}
