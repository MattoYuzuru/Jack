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

    expect(wrapper.text()).toContain(
      'Jack собирает частые рабочие сценарии в один понятный интерфейс.',
    )
    expect(wrapper.findAll('.tool-card')).toHaveLength(6)
    expect(wrapper.text()).toContain('Открыть Viewer')
    expect(wrapper.text()).toContain('Открыть Converter')
    expect(wrapper.text()).toContain('Открыть Compressor')
    expect(wrapper.text()).toContain('Открыть PDF Toolkit')
    expect(wrapper.text()).toContain('Открыть Editor')
    expect(wrapper.text()).toContain('Открыть Dev Utils')
  })
})
