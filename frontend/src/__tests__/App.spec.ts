import { describe, it, expect } from 'vitest'

import { mount } from '@vue/test-utils'
import App from '../App.vue'
import router from '../router'

describe('App', () => {
  it('renders the home route and exposes the viewer entry point', async () => {
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [router],
      },
    })

    expect(wrapper.text()).toContain('Главный экран Jack теперь ведёт в рабочий viewer-маршрут.')
    expect(wrapper.findAll('.tool-card')).toHaveLength(6)
    expect(wrapper.text()).toContain('Open Viewer')
  })
})
