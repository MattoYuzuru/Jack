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
    label: '01 · Кодировки',
    title: 'Кодировки и преобразования',
    description: 'Base64, URL, HTML entities и Unicode escape в одном окне.',
    detail: 'Подготовка текста для ссылок, запросов и интеграций.',
    accents: ['Base64', 'URL', 'HTML', 'Unicode'],
  },
  {
    id: 'jwt',
    label: '02 · JWT',
    title: 'Проверка JWT',
    description: 'Разбор header, payload и срока жизни токена локально.',
    detail: 'Claims, сроки действия и готовый Authorization header.',
    accents: ['JWT', 'Claims', 'Срок', 'Auth'],
  },
  {
    id: 'hash',
    label: '03 · Хэши',
    title: 'Хэши и HMAC',
    description: 'SHA-хэши и HMAC для текста или локального файла прямо в браузере.',
    detail: 'Проверка контрольных сумм, webhook-подписей и сверки файлов.',
    accents: ['SHA', 'HMAC', 'Files', 'Integrity'],
  },
  {
    id: 'link',
    label: '04 · Ссылки',
    title: 'Ссылки и URL для отправки',
    description: 'Очистка URL и сборка аккуратной версии для отправки.',
    detail: 'UTM, query-параметры, якоря и готовая чистая ссылка.',
    accents: ['URL', 'UTM', 'Query', 'Share'],
  },
  {
    id: 'validator',
    label: '05 · Валидаторы',
    title: 'Проверка структуры',
    description: 'JSON, YAML, XML и `.env` с быстрой диагностикой.',
    detail: 'Ошибки, нормализованный текст и короткая сводка по структуре.',
    accents: ['JSON', 'YAML', 'XML', '.env'],
  },
  {
    id: 'quick-utils',
    label: '06 · Быстрые утилиты',
    title: 'Ежедневные задачи',
    description: 'UUID, ULID, время и basic auth для частых мелких задач.',
    detail: 'Генерация значений и быстрые заготовки для ручной работы.',
    accents: ['UUID', 'ULID', 'Epoch', 'Auth'],
  },
]

export function listDevTools(): DevToolDefinition[] {
  return DEV_TOOL_DEFINITIONS
}

export function resolveDevTool(toolId: string): DevToolDefinition | null {
  return DEV_TOOL_DEFINITIONS.find((tool) => tool.id === toolId) ?? null
}
