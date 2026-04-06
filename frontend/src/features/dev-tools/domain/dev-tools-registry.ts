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
    description:
      'Быстрые преобразования Base64, Base64URL, URL, HTML entity и Unicode escape в одном окне.',
    detail:
      'Удобно, когда нужно быстро подготовить текст для ссылки, запроса, интеграции или обмена с другой системой.',
    accents: ['Base64', 'URL', 'HTML', 'Unicode'],
  },
  {
    id: 'jwt',
    label: '02 · JWT',
    title: 'Проверка JWT',
    description:
      'Разбор заголовка, данных и срока жизни токена без отправки данных во внешний сервис.',
    detail:
      'Можно быстро проверить сроки действия, подозрительные поля и сразу скопировать готовый заголовок авторизации.',
    accents: ['JWT', 'Claims', 'Срок', 'Auth'],
  },
  {
    id: 'hash',
    label: '03 · Хэши',
    title: 'Хэши и HMAC',
    description: 'SHA-хэши и HMAC для текста или локального файла прямо в браузере.',
    detail:
      'Полезно для проверки контрольных сумм, подписи webhook и сверки содержимого перед отправкой.',
    accents: ['SHA', 'HMAC', 'Files', 'Integrity'],
  },
  {
    id: 'link',
    label: '04 · Ссылки',
    title: 'Ссылки и URL для отправки',
    description:
      'Очистка ссылок от лишних параметров и сборка аккуратной версии для отправки коллегам или клиентам.',
    detail:
      'Показывает структуру URL, убирает трекинг и помогает быстро получить понятную чистую ссылку.',
    accents: ['URL', 'UTM', 'Query', 'Share'],
  },
  {
    id: 'validator',
    label: '05 · Валидаторы',
    title: 'Проверка структуры',
    description:
      'Проверка JSON, YAML, XML и `.env`, когда нужно быстро убедиться, что текст читается без ошибок.',
    detail:
      'На выходе сразу есть короткая диагностика, нормализованный текст и компактная сводка по структуре.',
    accents: ['JSON', 'YAML', 'XML', '.env'],
  },
  {
    id: 'quick-utils',
    label: '06 · Быстрые утилиты',
    title: 'Ежедневные задачи',
    description:
      'UUID, ULID, конвертация времени и базовая авторизация для частых рабочих мелочей.',
    detail:
      'Те самые маленькие задачи, которые постоянно всплывают в интеграциях, отладке и ручных проверках.',
    accents: ['UUID', 'ULID', 'Epoch', 'Auth'],
  },
]

export function listDevTools(): DevToolDefinition[] {
  return DEV_TOOL_DEFINITIONS
}

export function resolveDevTool(toolId: string): DevToolDefinition | null {
  return DEV_TOOL_DEFINITIONS.find((tool) => tool.id === toolId) ?? null
}
