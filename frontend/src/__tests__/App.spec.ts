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
      'Главный экран Jack теперь ведёт сразу в viewer и converter с image, AVIF, ICO и PDF-output.',
    )
    expect(wrapper.findAll('.tool-card')).toHaveLength(6)
    expect(wrapper.text()).toContain('Open Viewer')
    expect(wrapper.text()).toContain('Open Converter')
  })
})
