import { describe, it, expect } from 'vitest'

import { mount } from '@vue/test-utils'
import App from '../App.vue'
import router from '../router'

describe('App', () => {
  it('renders the home route and exposes active workspace entry points', async () => {
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [router],
      },
    })

    expect(wrapper.text()).toContain('Выбери задачу и сразу открой нужный инструмент.')
    expect(wrapper.findAll('.tool-card')).toHaveLength(6)
    expect(wrapper.text()).toContain('Viewer')
    expect(wrapper.text()).toContain('Converter')
    expect(wrapper.text()).toContain('Compressor')
    expect(wrapper.text()).toContain('PDF Toolkit')
    expect(wrapper.text()).toContain('Editor')
    expect(wrapper.text()).toContain('Dev Utils')
  })
})
