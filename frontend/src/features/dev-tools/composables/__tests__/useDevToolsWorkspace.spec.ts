import { nextTick } from 'vue'
import { beforeEach, describe, expect, it } from 'vitest'
import { useDevToolsWorkspace } from '../useDevToolsWorkspace'

const STORAGE_KEY = 'jack.dev-tools.workspace.v1'

describe('dev tools persistence', () => {
  beforeEach(() => {
    Object.defineProperty(window, 'localStorage', {
      configurable: true,
      value: createMemoryStorage(),
    })
    window.localStorage.clear()
  })

  it('does not persist workspace state until the user opts in', async () => {
    const workspace = useDevToolsWorkspace()
    workspace.jwtInput.value = 'secret.jwt.value'
    workspace.hashSecret.value = 'hmac-secret'
    workspace.basicAuthPassword.value = 'basic-secret'
    workspace.encodingInput.value = 'ordinary text'

    await nextTick()

    expect(window.localStorage.getItem(STORAGE_KEY)).toBeNull()
  })

  it('migrates legacy state without restoring or persisting secrets', () => {
    window.localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        persistenceEnabled: true,
        activeToolId: 'hash',
        encodingInput: 'safe value',
        jwtInput: 'secret.jwt.value',
        hashSecret: 'hmac-secret',
        basicAuthPassword: 'basic-secret',
      }),
    )

    const workspace = useDevToolsWorkspace()
    const persisted = window.localStorage.getItem(STORAGE_KEY) ?? ''

    expect(workspace.activeToolId.value).toBe('hash')
    expect(workspace.encodingInput.value).toBe('safe value')
    expect(workspace.jwtInput.value).toBe('')
    expect(workspace.hashSecret.value).toBe('')
    expect(workspace.basicAuthPassword.value).toBe('')
    expect(persisted).not.toContain('secret.jwt.value')
    expect(persisted).not.toContain('hmac-secret')
    expect(persisted).not.toContain('basic-secret')
  })

  it('persists only non-secret fields after opt-in', async () => {
    const workspace = useDevToolsWorkspace()
    workspace.persistenceEnabled.value = true
    workspace.encodingInput.value = 'safe value'
    workspace.jwtInput.value = 'secret.jwt.value'
    workspace.hashSecret.value = 'hmac-secret'
    workspace.basicAuthPassword.value = 'basic-secret'

    await nextTick()

    const persisted = window.localStorage.getItem(STORAGE_KEY) ?? ''
    expect(persisted).toContain('safe value')
    expect(persisted).not.toContain('secret.jwt.value')
    expect(persisted).not.toContain('hmac-secret')
    expect(persisted).not.toContain('basic-secret')
  })
})

function createMemoryStorage(): Storage {
  const values = new Map<string, string>()

  return {
    get length() {
      return values.size
    },
    clear: () => values.clear(),
    getItem: (key) => values.get(key) ?? null,
    key: (index) => [...values.keys()][index] ?? null,
    removeItem: (key) => values.delete(key),
    setItem: (key, value) => values.set(key, value),
  }
}
