package com.keykomi.jack.processing.web;

import com.keykomi.jack.processing.application.MarkdownRenderService;
import com.keykomi.jack.processing.domain.MarkdownRenderContract;
import java.util.Set;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/markdown")
public class MarkdownRenderController {

	private final MarkdownRenderService markdownRenderService;

	public MarkdownRenderController(MarkdownRenderService markdownRenderService) {
		this.markdownRenderService = markdownRenderService;
	}

	@PostMapping("/render")
	public MarkdownRenderContract render(@RequestBody MarkdownRenderRequest request) {
		return this.markdownRenderService.render(request.source(), request.profile(), request.extensions());
	}

	public record MarkdownRenderRequest(String source, String profile, Set<String> extensions) {
	}
}
