import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import DiagnosticsPanel from '../DiagnosticsPanel.vue'

describe('DiagnosticsPanel', () => {
  it('renders parser range and machine-readable quick fix', () => {
    const wrapper = mount(DiagnosticsPanel, {
      props: {
        fresh: true,
        scopeLabel: 'Parser-backed diagnostics.',
        issues: [
          {
            severity: 'error',
            code: 'JSON_PARSE_ERROR',
            message: 'Unexpected token',
            line: 2,
            column: 4,
            endLine: 2,
            endColumn: 5,
            hint: 'Проверьте запятые.',
            quickFixCode: 'repair-structured-syntax',
          },
        ],
      },
    })

    expect(wrapper.text()).toContain('2:4–2:5')
    expect(wrapper.get('.editor-issue__fix').text()).toBe('repair-structured-syntax')
  })
})
