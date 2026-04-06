import { afterEach, describe, expect, it, vi } from 'vitest'
import { createPlatformCapabilityScopeFixture } from '../../application/__tests__/capability-matrix.fixtures'
import { resetProcessingCapabilityScopeCache } from '../../application/processing-client'
import {
  getProcessingPlatformModules,
  resolveProcessingPlatformModule,
} from '../platform-registry'

const originalFetch = globalThis.fetch

describe('processing platform registry', () => {
  afterEach(() => {
    globalThis.fetch = originalFetch
    resetProcessingCapabilityScopeCache()
  })

  it('loads queued module definitions from backend platform matrix', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(createPlatformCapabilityScopeFixture()), {
        status: 200,
        headers: {
          'Content-Type': 'application/json',
        },
      }),
    ) as typeof fetch

    const modules = await getProcessingPlatformModules()

    expect(modules).toHaveLength(6)
    expect(modules[0]?.id).toBe('compression')
    expect(modules[1]?.reusedJobTypes).toContain('VIEWER_RESOLVE')
  })

  it('resolves a single queued module by id', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(createPlatformCapabilityScopeFixture()), {
        status: 200,
        headers: {
          'Content-Type': 'application/json',
        },
      }),
    ) as typeof fetch

    const module = await resolveProcessingPlatformModule('multi-format-editor')

    expect(module?.label).toBe('Multi-Format Editor')
    expect(module?.foundationReady).toBe(true)
  })
})
