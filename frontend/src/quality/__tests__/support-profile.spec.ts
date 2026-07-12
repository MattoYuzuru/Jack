import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

interface SupportCapability {
  id: string
  status: 'available' | 'test-only' | 'unavailable'
  fixture?: string
  contractTest?: string
}

interface SupportProfile {
  profileVersion: string
  capabilities: SupportCapability[]
}

describe('machine-readable support profile', () => {
  const qualityRoot = resolve(process.cwd(), '../quality')
  const profile = JSON.parse(
    readFileSync(resolve(qualityRoot, 'support-profile.v1.json'), 'utf8'),
  ) as SupportProfile

  it('is versioned', () => {
    expect(profile.profileVersion).toMatch(/^\d+\.\d+\.\d+$/u)
  })

  it.each(profile.capabilities.filter((capability) => capability.status === 'available'))(
    '$id has an executable fixture and contract test',
    (capability) => {
      expect(capability.fixture).toBeTruthy()
      expect(existsSync(resolve(qualityRoot, capability.fixture!))).toBe(true)
      expect(capability.contractTest).toBeTruthy()
    },
  )
})
