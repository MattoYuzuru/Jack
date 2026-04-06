export type DevToolId = 'encoding' | 'jwt' | 'hash' | 'link' | 'validator' | 'quick-utils'

export interface DevToolDefinition {
  id: DevToolId
  label: string
  title: string
  description: string
  detail: string
  accents: string[]
}

const DEV_TOOL_DEFINITIONS: DevToolDefinition[] = [
  {
    id: 'encoding',
    label: '01 · Encoding Lab',
    title: 'Encoding & Decoding',
    description:
      'Base64, Base64URL, URL, HTML entity и Unicode escape преобразования без переключения между внешними сервисами.',
    detail:
      'Подходит для payload cleanup, query debugging, unsafe fragment inspection и быстрого перевода текста между transport-friendly представлениями.',
    accents: ['Base64', 'URL', 'HTML', 'Unicode'],
  },
  {
    id: 'jwt',
    label: '02 · JWT Inspector',
    title: 'JWT Decoder',
    description:
      'Разбор header/payload, временных claim и transport-состояния токена без подписи и без внешней отправки.',
    detail:
      'Inspector показывает exp/iat/nbf, предупреждает про alg=none, пустую signature часть и сразу собирает Bearer header.',
    accents: ['Header', 'Claims', 'Expiry', 'Bearer'],
  },
  {
    id: 'hash',
    label: '03 · Hash Toolkit',
    title: 'Hashes & HMAC',
    description:
      'SHA-family digests и HMAC для текста или локального файла прямо в браузере через Web Crypto.',
    detail:
      'Нужен для checksum, webhook debugging, signature comparison и быстрой сверки payload без backend roundtrip.',
    accents: ['SHA', 'HMAC', 'Files', 'Integrity'],
  },
  {
    id: 'link',
    label: '04 · Link Utils',
    title: 'Links & Share URLs',
    description:
      'Разбор URL, чистка tracking-параметров и сборка более короткой share-friendly версии вместо нереалистичного hosted shortener.',
    detail:
      'Модуль показывает host/path/query структуру, чистит UTM/analytics noise и считает, насколько ссылка стала компактнее.',
    accents: ['URL', 'UTM', 'Query', 'Share'],
  },
  {
    id: 'validator',
    label: '05 · Validators',
    title: 'Structured Validators',
    description:
      'Быстрая валидация JSON, YAML, XML и .env без открытия редактора и без смешивания этого сценария с полноценным editing route.',
    detail:
      'Validator возвращает короткие diagnostics, нормализованный output и базовую структурную сводку для повседневных payload checks.',
    accents: ['JSON', 'YAML', 'XML', '.env'],
  },
  {
    id: 'quick-utils',
    label: '06 · Quick Utils',
    title: 'Daily Helpers',
    description:
      'UUID/ULID generator, timestamp converter и Basic Auth helper для типовых инженерных микро-задач.',
    detail:
      'Это те мелкие действия, которые постоянно всплывают в документации, curl/debug flow и внутренних интеграционных проверках.',
    accents: ['UUID', 'ULID', 'Epoch', 'Auth'],
  },
]

export function listDevTools(): DevToolDefinition[] {
  return DEV_TOOL_DEFINITIONS
}

export function resolveDevTool(toolId: string): DevToolDefinition | null {
  return DEV_TOOL_DEFINITIONS.find((tool) => tool.id === toolId) ?? null
}
