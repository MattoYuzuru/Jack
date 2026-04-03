# <img src="./assets/brand/logo.svg" alt="Jack logo" width="196">

**Jack** (`Jack of all trades`) is a web-based multi-tool workspace built around one idea: keep routine file operations, text tooling, and developer utilities in one fast interface.

The project starts as a mono-repo with:

- `backend/` - Spring Boot 4.0.5, Java 26
- `frontend/` - Vue 3 starter on the current stable release line
- `assets/brand/` - reusable logo and favicon source files

## What Jack Is For

Jack is planned as a practical daily-use toolbox for:

- file viewing and previewing
- converting files between popular formats
- compression with size targets
- PDF workflows
- text editing with live preview
- developer utilities like encoders, decoders, hash generators, validators, JWT tools, and short links

## Current Status

This repository currently contains the bootstrap foundation only:

- backend and frontend are separated into dedicated folders
- a basic branded landing page exists in the frontend
- logo and favicon assets are prepared for future UI usage
- roadmap and team workflow rules are documented for future iteration work

## Local Start

Backend:

```bash
cd backend
./gradlew bootRun
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

## Roadmap

### 0. Initial Bootstrap

- [ ] Empty but working project bootstrap
- [ ] Containerized local environment via `docker compose up -d --build`
- [ ] Project docs, repository rules, and basic development workflow
- [ ] Ready baseline for future iteration work

### 1. UI/UX Foundation

- [ ] Define the visual system for the whole product
- [ ] Lock in palette, spacing, typography, and interaction patterns
- [ ] Build a foundation where future tools mostly add new pages, blocks, or small components

### 2. File Viewer

- [ ] Unified upload flow and in-app preview workspace
- [ ] Support images, audio, video, office files, database files, and other useful formats

#### 2.1 Image Viewer

- [ ] Convenient image viewer UI
- [ ] Scale, zoom, fullscreen, rotation
- [ ] Metadata viewing and editing
- [ ] Color picker with magnifier
- [ ] Support: `jpg`, `jpeg`, `png`, `webp`, `avif`, `heic`, `gif`, `bmp`, `tiff`, `svg`, `raw`, `ico`

#### 2.2 Office Documents

- [ ] Beautiful preview and navigation UI
- [ ] Search, metadata, password-aware flows where possible
- [ ] Editing selected content when format support allows it
- [ ] Support: `doc`, `docx`, `pdf`, `txt`, `rtf`, `odt`, `xls`, `xlsx`, `csv`, `pptx`, `html`, `epub`, `db`, `sqlite`

#### 2.3 Video Viewer

- [ ] Comfortable player UI
- [ ] Rewind, speed control, and standard playback tools
- [ ] Support: `mp4`, `mov`, `avi`, `mkv`, `webm`, `wmv`, `flv`

#### 2.4 Audio Viewer

- [ ] Metadata support
- [ ] Custom player with practical navigation controls
- [ ] Support: `mp3`, `wav`, `aac`, `flac`, `ogg`, `opus`, `aiff`

#### 2.5 Other Formats

- [ ] Additional viewers for niche and utility-oriented formats

### 3. File Conversion

- [ ] Broad conversion coverage with a focus on practical real-world flows
- [ ] Image, document, spreadsheet, presentation, video, and audio conversion
- [ ] Media transformations between containers, codecs, sizes, and quality targets

#### 3.1 Common Image Conversion Flows

- [ ] `HEIC -> JPG`
- [ ] `PNG -> JPG`
- [ ] `JPG -> PNG`
- [ ] `JPG/PNG -> WebP`
- [ ] `JPG/PNG -> AVIF`
- [ ] `WebP -> JPG/PNG`
- [ ] `BMP -> JPG/PNG`
- [ ] `TIFF -> JPG/PDF`
- [ ] `PNG <-> WebP`
- [ ] `SVG -> PNG`
- [ ] `PNG -> SVG` via tracing/vectorization
- [ ] `RAW -> JPG`
- [ ] `RAW -> TIFF`
- [ ] `PSD -> JPG/PNG/WebP`
- [ ] `AI/EPS/SVG -> PNG/PDF`
- [ ] `PNG -> ICO`
- [ ] `SVG -> ICO`

#### 3.2 Common Office Conversion Flows

- [ ] `DOC -> DOCX`
- [ ] `DOCX -> PDF`
- [ ] `PDF -> DOCX`
- [ ] `DOCX -> TXT`
- [ ] `DOCX -> HTML`
- [ ] `RTF <-> DOCX`
- [ ] `ODT <-> DOCX`
- [ ] `PDF -> JPG/PNG`
- [ ] `JPG/PNG -> PDF`
- [ ] `PDF -> TXT`
- [ ] `PDF -> XLSX`
- [ ] `PDF -> PPTX`
- [ ] `DOCX/XLSX/PPTX -> PDF`
- [ ] `XLSX -> CSV`
- [ ] `CSV -> XLSX`
- [ ] `PDF -> CSV/XLSX`
- [ ] `XLSX -> PDF`
- [ ] `ODS <-> XLSX`
- [ ] `PPTX -> PDF`
- [ ] `PDF -> PPTX`
- [ ] `PPTX -> JPG/PNG`
- [ ] `PPTX -> video`

#### 3.3 Common Video and Audio Conversion Flows

- [ ] `MOV -> MP4`
- [ ] `MKV -> MP4`
- [ ] `AVI -> MP4`
- [ ] `WebM -> MP4`
- [ ] `MP4 -> WebM`
- [ ] `video -> GIF`
- [ ] `video -> MP3/WAV/AAC`
- [ ] `4K -> 1080p/720p`
- [ ] `H.265 -> H.264`
- [ ] `H.264 -> AV1`
- [ ] `WAV -> MP3`
- [ ] `FLAC -> MP3`
- [ ] `MP4 -> MP3`
- [ ] `M4A <-> MP3`
- [ ] `WAV <-> FLAC`

#### 3.4 Known Conversion Limits

- [ ] Account for layout loss in `PDF -> Word`
- [ ] Handle OCR prerequisites for scanned PDFs
- [ ] Explain CSV format limitations clearly
- [ ] Distinguish containers, codecs, bitrate, resolution, and FPS changes

### 4. Compression

- [ ] Compression for different file groups
- [ ] Maximum practical reduction mode
- [ ] Compression-to-target-size mode
- [ ] User-facing quality and limit controls

### 5. PDF Toolkit

- [ ] Redirects from compatible flows into PDF conversion
- [ ] Redirects into PDF viewer/editor flows
- [ ] `merge PDF`
- [ ] `split PDF`
- [ ] `rotate PDF`
- [ ] `OCR`
- [ ] `e-sign`
- [ ] `redact sensitive data`
- [ ] `page extract/reorder`
- [ ] `password protect / unlock`

### 6. Multi-Format Editor

- [ ] Rich editor block for multiple text-based formats
- [ ] Familiar shortcuts and toolbar equivalents
- [ ] Format-aware helpers for Markdown, HTML, CSS, JS, and more
- [ ] Syntax highlighting and live preview
- [ ] Safe validation against dangerous payloads and obvious abuse cases
- [ ] Export as plain text or finished files
- [ ] Built-in formatting similar to IDE-style code formatting actions

### 7. Dev Tools And Utils

- [ ] Encoders and decoders
- [ ] JWT decoder and helpers
- [ ] Hash generators
- [ ] Short-link tools
- [ ] Text format validators
- [ ] Additional daily-use developer utilities

## Iteration Model

This repo is intentionally prepared so a future task can be phrased like:

> "Do iteration 1 for the viewer, start with these formats"

After that, the agent should be able to create a fresh feature branch, implement the requested slice, add tests, update the roadmap, and finish the work as a normal MR-ready change.
