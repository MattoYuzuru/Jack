import sqlWasmUrl from 'sql.js/dist/sql-wasm.wasm?url'
import type { QueryExecResult, SqlJsStatic } from 'sql.js'
import type {
  ViewerDocumentDatabaseColumnPreview,
  ViewerDocumentDatabaseTablePreview,
  ViewerDocumentPreviewPayload,
  ViewerDocumentTablePreview,
} from './viewer-document'

const sqliteSampleRowLimit = 24
const sqliteSampleColumnLimit = 12
const sqliteMaxPreviewTables = 12
const sqliteHeader = 'SQLite format 3\u0000'

let sqlJsPromise: Promise<SqlJsStatic> | null = null

export class ViewerDatabaseFormatError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'ViewerDatabaseFormatError'
  }
}

export async function buildSqliteDocumentPreview(file: File): Promise<ViewerDocumentPreviewPayload> {
  const bytes = new Uint8Array(await file.arrayBuffer())

  if (!looksLikeSqlite(bytes)) {
    throw new ViewerDatabaseFormatError(
      'Файл распознан как DB/SQLite по расширению, но его сигнатура не похожа на SQLite container.',
    )
  }

  const SQL = await loadSqlJs()
  const database = new SQL.Database(bytes)

  try {
    const tableEntries = queryRows(
      database,
      "SELECT name, sql FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
    )
    const viewCount = querySingleNumber(
      database,
      "SELECT COUNT(*) AS count FROM sqlite_master WHERE type = 'view'",
    )
    const triggerCount = querySingleNumber(
      database,
      "SELECT COUNT(*) AS count FROM sqlite_master WHERE type = 'trigger'",
    )
    const tables = tableEntries
      .slice(0, sqliteMaxPreviewTables)
      .map((entry, index) =>
        buildDatabaseTablePreview(database, String(entry.name ?? `table_${index + 1}`), String(entry.sql ?? '')),
      )
    const searchableText = tables
      .map((table) =>
        [
          table.name,
          table.schemaSql,
          table.columns.map((column) => `${column.name} ${column.type}`.trim()).join('\n'),
          ...table.sample.rows.map((row) => row.join(' ')),
        ].join('\n'),
      )
      .join('\n\n')
    const warnings: string[] = [
      'SQLite preview работает только в read-only introspection mode: viewer читает schema и sample rows, но не исполняет произвольные пользовательские запросы и не модифицирует базу.',
    ]

    if (tableEntries.length > sqliteMaxPreviewTables) {
      warnings.push(
        `Для производительности viewer показывает первые ${sqliteMaxPreviewTables} таблиц из ${tableEntries.length}; полная структура всё равно остаётся в extracted search layer.`,
      )
    }

    return {
      summary: [
        { label: 'Тип документа', value: 'SQLite' },
        { label: 'Таблицы', value: String(tableEntries.length) },
        { label: 'Views', value: String(viewCount) },
        { label: 'Triggers', value: String(triggerCount) },
      ],
      searchableText,
      warnings,
      layout: {
        mode: 'database',
        text: searchableText,
        tables,
        activeTableIndex: 0,
      },
      previewLabel: 'SQLite database adapter',
    }
  } finally {
    database.close()
  }
}

async function loadSqlJs(): Promise<SqlJsStatic> {
  sqlJsPromise ??= import('sql.js').then(async (module) => {
    const initSqlJs = (module.default ?? module) as unknown as (config?: {
      locateFile: (fileName: string) => string
    }) => Promise<SqlJsStatic>

    return initSqlJs({
      locateFile(fileName) {
        if (fileName.endsWith('.wasm')) {
          return sqlWasmUrl
        }

        return fileName
      },
    })
  })

  return sqlJsPromise
}

function buildDatabaseTablePreview(
  database: InstanceType<SqlJsStatic['Database']>,
  tableName: string,
  schemaSql: string,
): ViewerDocumentDatabaseTablePreview {
  const quotedName = quoteSqlIdentifier(tableName)
  const columns = queryRows(database, `PRAGMA table_info(${quotedName})`).map((column) => ({
    name: String(column.name ?? ''),
    type: String(column.type ?? ''),
    nullable: Number(column.notnull ?? 0) === 0,
    primaryKey: Number(column.pk ?? 0) > 0,
    defaultValue: column.dflt_value == null ? '—' : String(column.dflt_value),
  }))
  const rowCount = querySingleNumber(database, `SELECT COUNT(*) AS count FROM ${quotedName}`)
  const sampleRowsResult = queryFirstResult(
    database,
    `SELECT * FROM ${quotedName} LIMIT ${sqliteSampleRowLimit}`,
  )
  const sample = buildDatabaseSample(columns, sampleRowsResult, rowCount)

  return {
    id: `sqlite-table-${tableName}`,
    name: tableName,
    rowCount,
    schemaSql: schemaSql || `CREATE TABLE ${tableName} (...)`,
    columns,
    sample,
  }
}

function buildDatabaseSample(
  columns: ViewerDocumentDatabaseColumnPreview[],
  sampleRowsResult: QueryExecResult | null,
  rowCount: number | null,
): ViewerDocumentTablePreview {
  const visibleColumns = columns
    .map((column) => column.name || 'Column')
    .slice(0, sqliteSampleColumnLimit)
  const rows = (sampleRowsResult?.values ?? []).map((row) =>
    row.slice(0, sqliteSampleColumnLimit).map((value) => formatSqlValue(value)),
  )

  return {
    columns: visibleColumns,
    rows,
    totalRows: rowCount ?? rows.length,
    totalColumns: columns.length,
    delimiter: '',
  }
}

function queryRows(
  database: InstanceType<SqlJsStatic['Database']>,
  sql: string,
): Record<string, unknown>[] {
  const result = queryFirstResult(database, sql)

  if (!result) {
    return []
  }

  return result.values.map((row) =>
    Object.fromEntries(result.columns.map((column, index) => [column, row[index] ?? null])),
  )
}

function querySingleNumber(
  database: InstanceType<SqlJsStatic['Database']>,
  sql: string,
): number | null {
  const result = queryFirstResult(database, sql)
  const value = result?.values[0]?.[0]

  if (typeof value === 'number') {
    return value
  }

  if (typeof value === 'string' && value.trim()) {
    return Number(value)
  }

  return null
}

function queryFirstResult(
  database: InstanceType<SqlJsStatic['Database']>,
  sql: string,
): QueryExecResult | null {
  return database.exec(sql)[0] ?? null
}

function formatSqlValue(value: unknown): string {
  if (value == null) {
    return 'NULL'
  }

  if (value instanceof Uint8Array) {
    return `BLOB(${value.byteLength} bytes)`
  }

  return String(value)
}

function quoteSqlIdentifier(identifier: string): string {
  return `"${identifier.replace(/"/gu, '""')}"`
}

function looksLikeSqlite(bytes: Uint8Array): boolean {
  const signature = new TextDecoder('ascii').decode(bytes.slice(0, sqliteHeader.length))
  return signature === sqliteHeader
}
