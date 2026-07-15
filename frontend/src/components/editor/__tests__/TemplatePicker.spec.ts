import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TemplatePicker from '../TemplatePicker.vue'

const templates = [
  { id: 'brief', label: 'План заметки', content: '# План\n\nТекст' },
  { id: 'release', label: 'Release notes', content: '# Release\n\n- Change' },
]

describe('TemplatePicker', () => {
  it('filters the palette and emits an explicit selection', async () => {
    const wrapper = mount(TemplatePicker, {
      props: { modelValue: 'brief', templates, currentContent: '# Draft' },
    })

    await wrapper.get('summary').trigger('click')
    await wrapper.get('input[type="search"]').setValue('release')

    const options = wrapper.findAll('[role="option"]')
    expect(options).toHaveLength(1)
    expect(options[0]?.text()).toContain('Release notes')
    await options[0]?.trigger('click')
    expect(wrapper.emitted('select')).toEqual([['release']])
  })

  it('shows a line-diff preview before replacement', () => {
    const wrapper = mount(TemplatePicker, {
      props: { modelValue: 'brief', templates, currentContent: '# Draft' },
    })

    expect(wrapper.get('.template-picker__diff').text()).toMatch(/\+\d+ строк · −\d+ строк/u)
    expect(wrapper.get('.template-picker__preview').text()).toContain('# План')
  })
})
