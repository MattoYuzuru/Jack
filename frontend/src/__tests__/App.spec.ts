import { describe, it, expect } from 'vitest'

import { mount } from '@vue/test-utils'
import App from '../App.vue'

describe('App', () => {
  it('mounts renders properly', () => {
    const wrapper = mount(App)
    expect(wrapper.text()).toContain('Главный экран Jack как мягкий big-tech control center.')
    expect(wrapper.findAll('.tool-card')).toHaveLength(6)
    expect(wrapper.text()).toContain('Viewer')
  })
})
