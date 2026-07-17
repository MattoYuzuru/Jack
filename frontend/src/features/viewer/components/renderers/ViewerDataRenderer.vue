<script setup lang="ts">
import { computed } from 'vue'
import type { ViewerResolvedDocument } from '../../application/viewer-runtime'

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
</script>

<template>
  <div class="viewer-document-frame" data-testid="viewer-data-renderer">
    <div v-if="selection.layout.mode === 'table'" class="document-table">
      <div class="document-table__summary">
        <strong>{{ selection.layout.table.totalRows }} строк</strong>
        <span>{{ selection.layout.table.totalColumns }} колонок</span>
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
            <tr v-for="(row, rowIndex) in selection.layout.table.rows" :key="rowIndex">
              <th scope="row" class="document-table__row-number">
                {{ rowIndex + 1 }}
              </th>
              <td v-for="(cell, columnIndex) in row" :key="`${rowIndex}-${columnIndex}`">
                {{ cell || '—' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
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
                <th v-for="column in activeSheet.table.columns" :key="column" scope="col">
                  {{ column }}
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(row, rowIndex) in activeSheet.table.rows"
                :key="`${activeSheet.id}-${rowIndex}`"
              >
                <td v-for="(cell, columnIndex) in row" :key="`${rowIndex}-${columnIndex}`">
                  {{ cell || '—' }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
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
                  v-for="(row, rowIndex) in activeDatabaseTable.sample.rows"
                  :key="`${activeDatabaseTable.id}-${rowIndex}`"
                >
                  <td v-for="(cell, columnIndex) in row" :key="`${rowIndex}-${columnIndex}`">
                    {{ cell || '—' }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
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
