package com.keykomi.jack.processing.domain;

import java.util.List;

public record DocumentPreviewPayload(
	List<DocumentFact> summary,
	String searchableText,
	List<String> warnings,
	DocumentLayoutPayload layout,
	String previewLabel
) {

	public record DocumentFact(
		String label,
		String value
	) {
	}

	public record DocumentOutlineItem(
		String id,
		String label,
		int level
	) {
	}

	public record DocumentTablePreview(
		List<String> columns,
		List<List<String>> rows,
		int totalRows,
		int totalColumns,
		String delimiter,
		String revision,
		boolean truncated,
		String encoding,
		Boolean hasHeader,
		List<DocumentTableColumn> columnMetadata,
		String nextCursor,
		int rowOffset
	) {
		public DocumentTablePreview(
			List<String> columns,
			List<List<String>> rows,
			int totalRows,
			int totalColumns,
			String delimiter
		) {
			this(columns, rows, totalRows, totalColumns, delimiter, null, false, null, null, List.of(), null, 0);
		}
	}

	public record DocumentTableColumn(
		String id,
		String label,
		String inferredType
	) {
	}

	public record DocumentSheetPreview(
		String id,
		String name,
		DocumentTablePreview table
	) {
	}

	public record DocumentSlidePreview(
		String id,
		String title,
		List<String> bullets
	) {
	}

	public record DocumentDatabaseColumnPreview(
		String name,
		String type,
		boolean nullable,
		boolean primaryKey,
		String defaultValue
	) {
	}

	public record DocumentDatabaseTablePreview(
		String id,
		String name,
		Long rowCount,
		String schemaSql,
		List<DocumentDatabaseColumnPreview> columns,
		DocumentTablePreview sample
	) {
	}

	public record DocumentEditableDraft(
		String text,
		String fileName,
		String editorFormatId
	) {
	}

	public record DocumentLayoutPayload(
		String mode,
		Integer pageCount,
		String text,
		List<String> paragraphs,
		DocumentTablePreview table,
		String srcDoc,
		List<DocumentOutlineItem> outline,
		List<DocumentSheetPreview> sheets,
		Integer activeSheetIndex,
		List<DocumentSlidePreview> slides,
		List<DocumentDatabaseTablePreview> tables,
		Integer activeTableIndex,
		DocumentEditableDraft editableDraft
	) {
	}

}
