package com.keykomi.jack.processing.web;

import com.keykomi.jack.processing.application.CapabilityCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/capabilities")
public class CapabilityController {

	private final CapabilityCatalogService capabilityCatalogService;

	public CapabilityController(CapabilityCatalogService capabilityCatalogService) {
		this.capabilityCatalogService = capabilityCatalogService;
	}

	@GetMapping("/viewer")
	public CapabilityCatalogService.CapabilityScope viewerCapabilities() {
		return this.capabilityCatalogService.viewerCapabilities();
	}

	@GetMapping("/converter")
	public CapabilityCatalogService.CapabilityScope converterCapabilities() {
		return this.capabilityCatalogService.converterCapabilities();
	}

	@GetMapping("/compression")
	public CapabilityCatalogService.CapabilityScope compressionCapabilities() {
		return this.capabilityCatalogService.compressionCapabilities();
	}

	@GetMapping("/pdf-toolkit")
	public CapabilityCatalogService.CapabilityScope pdfToolkitCapabilities() {
		return this.capabilityCatalogService.pdfToolkitCapabilities();
	}

	@GetMapping("/platform")
	public CapabilityCatalogService.CapabilityScope platformCapabilities() {
		return this.capabilityCatalogService.platformCapabilities();
	}

}
