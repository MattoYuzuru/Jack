import { mount } from '@vue/test-utils'
import { h } from 'vue'
import { describe, expect, it } from 'vitest'
import AppShell from '../AppShell.vue'
import UiDropZone from '../UiDropZone.vue'
import UiField from '../UiField.vue'
import UiProgress from '../UiProgress.vue'
import UiStatusBanner from '../UiStatusBanner.vue'
import UiTabs from '../UiTabs.vue'
import WorkspaceHeader from '../WorkspaceHeader.vue'

describe('UI foundation primitives', () => {
  it('provides semantic shell and workspace header landmarks', () => {
    const shell = mount(AppShell, { slots: { default: '<h1>Workspace</h1>' } })
    const header = mount(WorkspaceHeader, {
      props: { eyebrow: 'Jack · Test', title: 'Тестовый инструмент' },
      slots: { actions: '<a href="/">На главную</a>' },
    })

    expect(shell.element.tagName).toBe('MAIN')
    expect(header.element.tagName).toBe('HEADER')
    expect(header.get('img').attributes('alt')).toBe('Логотип Jack')
  })

  it('connects field labels and descriptions to slotted controls', () => {
    const wrapper = mount(UiField, {
      props: { label: 'Имя файла', hint: 'Используй безопасное расширение.' },
      slots: {
        default: ({ id, describedBy }: { id: string; describedBy?: string }) =>
          h('input', { id, 'aria-describedby': describedBy }),
      },
    })

    expect(wrapper.get('label').attributes('for')).toBe(wrapper.get('input').attributes('id'))
    expect(wrapper.get('input').attributes('aria-describedby')).toBe(
      wrapper.get('.ui-field__description').attributes('id'),
    )
  })

  it('implements roving semantic tabs', async () => {
    const wrapper = mount(UiTabs, {
      props: {
        modelValue: 'preview',
        label: 'Панели',
        idPrefix: 'test',
        items: [
          { id: 'preview', label: 'Просмотр' },
          { id: 'issues', label: 'Проверка' },
        ],
      },
    })

    await wrapper.get('#test-tab-preview').trigger('keydown', { key: 'ArrowRight' })
    expect(wrapper.emitted('update:modelValue')).toEqual([['issues']])
    expect(wrapper.get('#test-tab-preview').attributes('aria-controls')).toBe('test-panel-preview')
  })

  it('announces statuses and exposes native progress semantics', () => {
    const status = mount(UiStatusBanner, { props: { tone: 'error' }, slots: { default: 'Ошибка' } })
    const progress = mount(UiProgress, { props: { value: 140, label: 'Обработка' } })

    expect(status.attributes('role')).toBe('alert')
    expect(progress.get('progress').attributes('value')).toBe('100')
  })

  it('accepts keyboard-addressable file input', async () => {
    const wrapper = mount(UiDropZone, {
      props: { label: 'Выбрать документ', accept: '.txt' },
      slots: { default: 'Перетащи файл' },
    })
    const file = new File(['hello'], 'notes.txt', { type: 'text/plain' })
    const input = wrapper.get('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')

    expect(wrapper.attributes('role')).toBe('button')
    expect(wrapper.emitted('file')?.[0]?.[0]).toBe(file)
  })
})
