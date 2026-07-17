<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ViewerResolvedDocument } from '../../application/viewer-runtime'
import type { ViewerDocumentTablePreview } from '../../application/viewer-document'
import { requestProcessingJson } from '../../../processing/application/processing-client'

const props = defineProps<{
  selection: ViewerResolvedDocument
  sheetIndex: number
  databaseTableIndex: number
}>()

const emit = defineEmits<{
  selectSheet: [index: number]
  selectDatabaseTable: [index: number]
}>()

const activeSheet = computed(() => {
  if (props.selection.layout.mode !== 'workbook') {
    return null
  }
  return props.selection.layout.sheets[props.sheetIndex] ?? props.selection.layout.sheets[0] ?? null
})

const activeDatabaseTable = computed(() => {
  if (props.selection.layout.mode !== 'database') {
    return null
  }
  return (
    props.selection.layout.tables[props.databaseTableIndex] ??
    props.selection.layout.tables[0] ??
    null
  )
})

const TABLE_DOM_ROW_LIMIT = 200
const tableRows = ref<string[][]>([])
const tableRowOffset = ref(0)
const tableNextCursor = ref<string | null>(null)
const tableLoading = ref(false)
const tableError = ref('')
const tableQuery = ref('')
const workbookColumns = ref<string[]>([])
const workbookRows = ref<string[][]>([])
const workbookNextRow = ref(0)
const workbookLoading = ref(false)
const workbookError = ref('')
const databaseRows = ref<string[][]>([])
const databaseNextCursor = ref<string | null>(null)
const databaseNextOffset = ref(0)
const databaseHasMore = ref(true)
const databaseLoading = ref(false)
const databaseError = ref('')

watch(
  () => (props.selection.layout.mode === 'table' ? props.selection.layout.table : null),
  (table) => {
    const sourceRows = table?.rows.map((row) => [...row]) ?? []
    const overflow = Math.max(0, sourceRows.length - TABLE_DOM_ROW_LIMIT)
    tableRows.value = sourceRows.slice(overflow)
    tableRowOffset.value = (table?.rowOffset ?? 0) + overflow
    tableNextCursor.value = table?.nextCursor ?? null
    tableError.value = ''
    tableQuery.value = ''
  },
  { immediate: true },
)

const filteredTableRows = computed(() => {
  const query = tableQuery.value.trim().toLocaleLowerCase()
  if (!query) return tableRows.value
  return tableRows.value.filter((row) =>
    row.some((cell) => cell.toLocaleLowerCase().includes(query)),
  )
})

watch(
  activeSheet,
  (sheet) => {
    workbookColumns.value = sheet?.table.columns ?? []
    workbookRows.value = (sheet?.table.rows.map((row) => [...row]) ?? []).slice(
      -TABLE_DOM_ROW_LIMIT,
    )
    workbookNextRow.value = workbookRows.value.length + (workbookColumns.value.length > 0 ? 1 : 0)
    workbookError.value = ''
    if (sheet && workbookRows.value.length === 0 && props.selection.sourceUploadId) {
      void loadWorkbookRange(true)
    }
  },
  { immediate: true },
)

watch(
  activeDatabaseTable,
  (table) => {
    databaseRows.value = (table?.sample.rows.map((row) => [...row]) ?? []).slice(
      -TABLE_DOM_ROW_LIMIT,
    )
    databaseNextOffset.value = databaseRows.value.length
    databaseNextCursor.value = null
    databaseHasMore.value = true
    databaseError.value = ''
  },
  { immediate: true },
)

async function loadNextTableRange() {
  if (!props.selection.sourceUploadId || !tableNextCursor.value || tableLoading.value) return
  tableLoading.value = true
  tableError.value = ''
  try {
    const response = await requestProcessingJson<{
      table: ViewerDocumentTablePreview
      warnings: string[]
      exactRowCount: number | null
    }>(
      `/api/uploads/${props.selection.sourceUploadId}/table-range?cursor=${encodeURIComponent(tableNextCursor.value)}&limit=50`,
    )
    const combinedRows = [...tableRows.value, ...response.table.rows]
    const overflow = Math.max(0, combinedRows.length - TABLE_DOM_ROW_LIMIT)
    tableRows.value = combinedRows.slice(overflow)
    tableRowOffset.value += overflow
    tableNextCursor.value = response.table.nextCursor ?? null
  } catch (error) {
    tableError.value =
      error instanceof Error ? error.message : 'Не удалось загрузить следующую часть таблицы.'
  } finally {
    tableLoading.value = false
  }
}

async function copyVisibleTableSlice() {
  if (props.selection.layout.mode !== 'table') return
  const rows = [props.selection.layout.table.columns, ...filteredTableRows.value]
  try {
    await navigator.clipboard.writeText(rows.map((row) => row.join('\t')).join('\n'))
    tableError.value = ''
  } catch {
    tableError.value = 'Браузер не разрешил скопировать текущий bounded slice.'
  }
}

async function loadWorkbookRange(replace = false) {
  if (!props.selection.sourceUploadId || !activeSheet.value || workbookLoading.value) return
  workbookLoading.value = true
  workbookError.value = ''
  try {
    const startRow = replace ? 0 : workbookNextRow.value
    const response = await requestProcessingJson<{
      rowCount: number
      rows: Array<Array<{ formattedValue: string; rawValue: string }>>
    }>(
      `/api/uploads/${props.selection.sourceUploadId}/workbook-range?sheetIndex=${props.sheetIndex}&startRow=${startRow}&rows=50&columns=${Math.max(1, Math.min(50, activeSheet.value.table.totalColumns || 12))}`,
    )
    const values = response.rows.map((row) =>
      row.map((cell) => cell.formattedValue || cell.rawValue),
    )
    if (replace) {
      workbookColumns.value =
        values.shift()?.map((value, index) => value || `Column ${index + 1}`) ?? []
      workbookRows.value = values
    } else {
      workbookRows.value = [...workbookRows.value, ...values].slice(-TABLE_DOM_ROW_LIMIT)
    }
    workbookNextRow.value = startRow + response.rowCount
  } catch (error) {
    workbookError.value =
      error instanceof Error ? error.message : 'Не удалось загрузить workbook range.'
  } finally {
    workbookLoading.value = false
  }
}

async function loadDatabaseRange() {
  if (!props.selection.sourceUploadId || !activeDatabaseTable.value || databaseLoading.value) return
  databaseLoading.value = true
  databaseError.value = ''
  try {
    const cursor = databaseNextCursor.value
      ? `&cursor=${encodeURIComponent(databaseNextCursor.value)}`
      : `&offset=${databaseNextOffset.value}`
    const response = await requestProcessingJson<{
      rows: string[][]
      nextCursor: string | null
      truncated: boolean
    }>(
      `/api/uploads/${props.selection.sourceUploadId}/database-range?table=${encodeURIComponent(activeDatabaseTable.value.name)}&limit=50${cursor}`,
    )
    databaseRows.value = [...databaseRows.value, ...response.rows].slice(-TABLE_DOM_ROW_LIMIT)
    databaseNextOffset.value += response.rows.length
    databaseNextCursor.value = response.nextCursor
    databaseHasMore.value = response.truncated
  } catch (error) {
    databaseError.value =
      error instanceof Error ? error.message : 'Не удалось загрузить database range.'
  } finally {
    databaseLoading.value = false
  }
}
</script>

<template>
  <div class="viewer-document-frame" data-testid="viewer-data-renderer">
    <div v-if="selection.layout.mode === 'table'" class="document-table">
      <div class="document-table__summary">
        <strong>
          {{
            selection.layout.table.truncated
              ? 'Строк: больше preview window'
              : `${selection.layout.table.totalRows} строк`
          }}
        </strong>
        <span>{{ selection.layout.table.totalColumns }} колонок</span>
      </div>
      <div class="document-table__tools">
        <label>
          <span class="sr-only">Найти в загруженном диапазоне</span>
          <input v-model="tableQuery" type="search" placeholder="Найти в диапазоне" />
        </label>
        <button type="button" @click="copyVisibleTableSlice">Копировать slice</button>
        <span v-if="selection.layout.table.encoding">
          {{ selection.layout.table.encoding }} ·
          {{ selection.layout.table.hasHeader ? 'header' : 'без header' }}
        </span>
      </div>
      <div
        class="document-table__scroll"
        tabindex="0"
        role="region"
        aria-label="Предпросмотр таблицы"
      >
        <table>
          <thead>
            <tr>
              <th scope="col" class="document-table__row-number">#</th>
              <th v-for="column in selection.layout.table.columns" :key="column" scope="col">
                {{ column }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, rowIndex) in filteredTableRows" :key="tableRowOffset + rowIndex">
              <th scope="row" class="document-table__row-number">
                {{ tableRowOffset + rowIndex + 1 }}
              </th>
              <td v-for="(cell, columnIndex) in row" :key="`${rowIndex}-${columnIndex}`">
                {{ cell || '—' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <button
        v-if="tableNextCursor"
        class="document-table__load-more"
        type="button"
        :disabled="tableLoading"
        @click="loadNextTableRange"
      >
        {{ tableLoading ? 'Загружаю…' : 'Следующие 50 строк' }}
      </button>
      <p v-if="tableError" class="document-table__error" role="status">{{ tableError }}</p>
    </div>

    <div v-else-if="selection.layout.mode === 'workbook'" class="document-workbook">
      <div class="document-workbook__tabs" role="tablist" aria-label="Листы книги">
        <button
          v-for="(sheet, index) in selection.layout.sheets"
          :key="sheet.id"
          class="document-sheet-chip"
          :class="{ 'document-sheet-chip--active': sheetIndex === index }"
          type="button"
          role="tab"
          :aria-selected="sheetIndex === index"
          @click="emit('selectSheet', index)"
        >
          {{ sheet.name }}
        </button>
      </div>
      <div v-if="activeSheet" class="document-table">
        <div class="document-table__summary">
          <strong>{{ activeSheet.table.totalRows }} строк</strong>
          <span>{{ activeSheet.table.totalColumns }} колонок</span>
        </div>
        <div
          class="document-table__scroll"
          tabindex="0"
          role="region"
          :aria-label="`Лист ${activeSheet.name}`"
        >
          <table>
            <thead>
              <tr>
                <th v-for="column in workbookColumns" :key="column" scope="col">
                  {{ column }}
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, rowIndex) in workbookRows" :key="`${activeSheet.id}-${rowIndex}`">
                <td v-for="(cell, columnIndex) in row" :key="`${rowIndex}-${columnIndex}`">
                  {{ cell || '—' }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <button
          v-if="workbookNextRow <= activeSheet.table.totalRows"
          class="document-table__load-more"
          type="button"
          :disabled="workbookLoading"
          @click="loadWorkbookRange(false)"
        >
          {{ workbookLoading ? 'Загружаю…' : 'Следующий workbook range' }}
        </button>
        <p v-if="workbookError" class="document-table__error" role="status">{{ workbookError }}</p>
      </div>
      <p v-else class="viewer-panel__empty">В книге нет доступных листов.</p>
    </div>

    <div v-else-if="selection.layout.mode === 'database'" class="document-database">
      <div class="document-workbook__tabs" role="tablist" aria-label="Таблицы базы данных">
        <button
          v-for="(table, index) in selection.layout.tables"
          :key="table.id"
          class="document-sheet-chip"
          :class="{
            'document-sheet-chip--active': databaseTableIndex === index,
          }"
          type="button"
          role="tab"
          :aria-selected="databaseTableIndex === index"
          @click="emit('selectDatabaseTable', index)"
        >
          {{ table.name }}
        </button>
      </div>
      <template v-if="activeDatabaseTable">
        <article class="document-database__schema">
          <div class="document-table__summary">
            <strong>{{
              activeDatabaseTable.rowCount == null
                ? 'Строк: неизвестно'
                : `${activeDatabaseTable.rowCount} строк`
            }}</strong>
            <span>{{ activeDatabaseTable.columns.length }} колонок</span>
          </div>
          <pre>{{ activeDatabaseTable.schemaSql }}</pre>
        </article>
        <div class="document-table">
          <div
            class="document-table__scroll"
            tabindex="0"
            role="region"
            :aria-label="`Таблица ${activeDatabaseTable.name}`"
          >
            <table>
              <thead>
                <tr>
                  <th
                    v-for="column in activeDatabaseTable.sample.columns"
                    :key="column"
                    scope="col"
                  >
                    {{ column }}
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(row, rowIndex) in databaseRows"
                  :key="`${activeDatabaseTable.id}-${rowIndex}`"
                >
                  <td v-for="(cell, columnIndex) in row" :key="`${rowIndex}-${columnIndex}`">
                    {{ cell || '—' }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <button
            v-if="databaseHasMore"
            class="document-table__load-more"
            type="button"
            :disabled="databaseLoading"
            @click="loadDatabaseRange"
          >
            {{ databaseLoading ? 'Загружаю…' : 'Следующий database range' }}
          </button>
          <p v-if="databaseError" class="document-table__error" role="status">
            {{ databaseError }}
          </p>
        </div>
      </template>
      <p v-else class="viewer-panel__empty">В этой базе не найдено таблиц для просмотра.</p>
    </div>
  </div>
</template>

<style scoped>
.viewer-document-frame,
.document-table,
.document-workbook,
.document-database {
  display: grid;
  width: 100%;
  gap: 14px;
}
.viewer-document-frame {
  min-height: 480px;
  place-items: start center;
  overflow: auto;
}
.document-workbook__tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.document-sheet-chip {
  min-height: 44px;
  padding: 8px 14px;
  border: 0;
  border-radius: 999px;
  background: rgba(255, 250, 242, 0.84);
  box-shadow: var(--shadow-pressed);
  color: var(--text-soft);
  font-weight: 700;
  cursor: pointer;
}
.document-sheet-chip--active {
  color: var(--accent-cool-strong);
  background:
    radial-gradient(circle at top left, rgba(255, 203, 148, 0.5), transparent 36%),
    rgba(255, 250, 242, 0.92);
}
.document-table__summary {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: var(--text-soft);
}
.document-table__tools {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--text-soft);
}
.document-table__tools input,
.document-table__tools button,
.document-table__load-more {
  min-height: 42px;
  border: 0;
  border-radius: 999px;
  background: rgba(255, 250, 242, 0.9);
  box-shadow: var(--shadow-pressed);
  color: var(--text-main);
}
.document-table__tools input {
  min-width: min(260px, 72vw);
  padding: 8px 14px;
}
.document-table__tools button,
.document-table__load-more {
  padding: 8px 16px;
  font-weight: 750;
  cursor: pointer;
}
.document-table__load-more {
  justify-self: start;
}
.document-table__error {
  margin: 0;
  color: var(--accent-warm-strong);
}
.document-table__scroll {
  max-width: 100%;
  overflow: auto;
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-pressed);
}
.document-table__scroll:focus-visible {
  outline: 3px solid var(--focus-ring);
  outline-offset: 3px;
}
table {
  width: 100%;
  border-collapse: collapse;
  background: rgba(255, 250, 242, 0.84);
}
th,
td {
  padding: 12px 14px;
  border-bottom: 1px solid rgba(16, 36, 38, 0.08);
  text-align: left;
  vertical-align: top;
}
th {
  position: sticky;
  top: 0;
  background: rgba(240, 230, 216, 0.96);
  color: var(--text-strong);
}
.document-table__row-number {
  width: 1%;
  white-space: nowrap;
  color: var(--text-soft);
}
.document-database__schema {
  display: grid;
  gap: 12px;
  padding: 18px;
  border-radius: var(--radius-xl);
  background: rgba(255, 250, 242, 0.88);
  box-shadow: var(--shadow-pressed);
}
.document-database__schema pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-main);
}
.viewer-panel__empty {
  margin: 0;
  color: var(--text-soft);
}
</style>
