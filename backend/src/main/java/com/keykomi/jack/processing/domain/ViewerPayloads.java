package com.keykomi.jack.processing.domain;

import java.util.List;
import java.util.UUID;

public final class ViewerPayloads {

	private ViewerPayloads() {
	}

	public record ViewerFact(
		String label,
		String value
	) {
	}

	public record ViewerBinaryArtifact(
		String kind,
		String fileName,
		String mediaType,
		long sizeBytes,
		String downloadPath
	) {
	}

	public record ViewerImagePayload(
		Integer width,
		Integer height,
		MetadataPayloads.ImageMetadataPayload metadata,
		List<String> warnings
	) {
	}

	public record ViewerDocumentPayload(
		List<DocumentPreviewPayload.DocumentFact> summary,
		String searchableText,
		List<String> warnings,
		DocumentPreviewPayload.DocumentLayoutPayload layout
	) {
	}

	public record ViewerVideoMetadata(
		String mimeType,
		String aspectRatio,
		String orientation,
		Long estimatedBitrateBitsPerSecond,
		Long sizeBytes
	) {
	}

	public record ViewerVideoLayout(
		String mode,
		Double durationSeconds,
		Integer width,
		Integer height,
		ViewerVideoMetadata metadata
	) {
	}

	public record ViewerVideoPayload(
		List<ViewerFact> summary,
		List<String> warnings,
		ViewerVideoLayout layout
	) {
	}

	public record ViewerAudioMetadata(
		String mimeType,
		Long estimatedBitrateBitsPerSecond,
		Integer sampleRate,
		Integer channelCount,
		String codec,
		String container,
		Long sizeBytes
	) {
	}

	public record ViewerAudioLayout(
		String mode,
		Double durationSeconds,
		List<Double> waveform,
		ViewerAudioMetadata metadata
	) {
	}

	public record ViewerAudioPayload(
		List<ViewerFact> summary,
		List<String> warnings,
		String searchableText,
		String artworkDataUrl,
		List<MetadataPayloads.MetadataGroup> metadataGroups,
		ViewerAudioLayout layout
	) {
	}

	public record ViewerResolveManifest(
		UUID uploadId,
		String originalFileName,
		String family,
		String kind,
		String previewLabel,
		ViewerBinaryArtifact binaryArtifact,
		ViewerImagePayload imagePayload,
		ViewerDocumentPayload documentPayload,
		ViewerVideoPayload videoPayload,
		ViewerAudioPayload audioPayload
	) {
	}

}
