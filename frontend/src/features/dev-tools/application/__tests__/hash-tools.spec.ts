import { describe, expect, it } from 'vitest'
import { buildHashReport } from '../hash-tools'

describe('hash tools', () => {
  it('builds stable digests for text payloads', async () => {
    const report = await buildHashReport('abc', '')

    expect(report.digests.find((entry) => entry.id === 'sha1')?.value).toBe(
      'a9993e364706816aba3e25717850c26c9cd0d89d',
    )
    expect(report.digests.find((entry) => entry.id === 'sha256')?.value).toBe(
      'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad',
    )
    expect(report.hmacDigests).toHaveLength(0)
  })

  it('adds hmac values when a secret is provided', async () => {
    const report = await buildHashReport('jack', 'secret')

    expect(report.hmacDigests).toHaveLength(2)
    expect(report.hmacDigests[0]?.value).toHaveLength(64)
  })
})
