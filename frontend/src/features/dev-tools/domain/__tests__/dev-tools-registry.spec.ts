import { describe, expect, it } from 'vitest'
import { listDevTools, resolveDevTool } from '../dev-tools-registry'

describe('dev tools registry', () => {
  it('lists all live dev tool groups for iteration 7', () => {
    const tools = listDevTools()

    expect(tools).toHaveLength(6)
    expect(tools[0]?.id).toBe('encoding')
    expect(tools[5]?.id).toBe('quick-utils')
  })

  it('resolves a single tool definition by id', () => {
    const tool = resolveDevTool('jwt')

    expect(tool?.title).toBe('Проверка JWT')
    expect(tool?.accents).toContain('Auth')
  })
})
