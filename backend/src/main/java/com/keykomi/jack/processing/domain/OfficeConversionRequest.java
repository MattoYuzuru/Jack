package com.keykomi.jack.processing.domain;

public record OfficeConversionRequest(
	String targetExtension,
	Integer maxWidth,
	Integer maxHeight,
	Double quality,
	String backgroundColor,
	String presetLabel
) {
}
