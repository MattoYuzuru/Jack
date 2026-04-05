package com.keykomi.jack.processing.domain;

public record ImageProcessingRequest(
	String operation,
	String targetExtension,
	Integer maxWidth,
	Integer maxHeight,
	Double quality,
	String backgroundColor,
	String presetLabel
) {
}
