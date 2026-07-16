package com.keykomi.jack.processing.domain;

import java.util.List;
import java.util.Set;

public record MarkdownRenderContract(
	String profileVersion,
	String profile,
	String sanitizedHtml,
	String previewDocument,
	List<OutlineItem> outline,
	List<UnresolvedReference> unresolvedReferences,
	List<String> warnings,
	Set<String> enabledExtensions,
	Set<String> detectedFeatures
) {

	public record OutlineItem(String id, String label, int depth, String kind) {
	}

	public record UnresolvedReference(String kind, String target, String label) {
	}
}
