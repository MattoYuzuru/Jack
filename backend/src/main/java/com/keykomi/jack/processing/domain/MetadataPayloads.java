package com.keykomi.jack.processing.domain;

import java.util.List;

public final class MetadataPayloads {

	private MetadataPayloads() {
	}

	public record MetadataItem(
		String label,
		String value
	) {
	}

	public record MetadataGroup(
		String id,
		String label,
		List<MetadataItem> entries
	) {
	}

	public record EditableMetadata(
		String description,
		String artist,
		String copyright,
		String capturedAt
	) {
	}

	public record ImageMetadataPayload(
		List<MetadataItem> summary,
		List<MetadataGroup> groups,
		EditableMetadata editable,
		String thumbnailDataUrl
	) {
	}

	public record AudioTechnicalMetadata(
		Integer sampleRate,
		Integer channelCount,
		String codec,
		String container
	) {
	}

	public record AudioMetadataPayload(
		List<MetadataItem> summary,
		List<MetadataGroup> groups,
		String artworkDataUrl,
		String searchableText,
		AudioTechnicalMetadata technical
	) {
	}

	public record MetadataInspectManifest(
		String operation,
		String family,
		ImageMetadataPayload imagePayload,
		AudioMetadataPayload audioPayload,
		List<String> warnings
	) {
	}

	public record MetadataExportManifest(
		String mode,
		String fileName,
		List<String> warnings
	) {
	}

}
