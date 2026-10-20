# Scholia

A personal Android reader for PDFs and EPUBs. You can highlight passages, attach notes, and
review every note per book.

## Install

**Copy `release/reader.apk` to your phone, open it, and allow "Install unknown apps" when asked.**

Or, with the phone connected over USB or wireless debugging:

```sh
adb install -r release/reader.apk
```

Android 8.0 (API 26) or newer.

## Rebuild

Requires JDK 21 and the Android SDK (platform 37, build-tools 37.0.0). Point `local.properties` at
the SDK, e.g. `sdk.dir=/opt/homebrew/share/android-commandlinetools`.

**Release build** (signed, installs as an update over any earlier version and keeps your library):

```sh
./scripts/release.sh
```

This writes `release/scholia-v<version>.apk`, a copy at `release/reader.apk`, and a `.sha256`
checksum. Bump `versionCode` and `versionName` in `app/build.gradle.kts` before each release.

**Debug build:** `./gradlew assembleDebug`. It installs as a separate app, "Scholia (dev)"
(`com.scholia.reader.debug`), so testing never touches your real library.

### Signing

- **v1.0** was signed with this Mac's Android debug key.
- **From v1.1**, releases are signed with a dedicated release key and a key-rotation lineage
  (debug key → release key). Android 9+ accepts them as updates to v1.0. Older devices still see
  the debug key in the v2 signature.

The signing files live outside the repo, in `~/.config/scholia-signing/`:

| File | What it is |
|---|---|
| `release.jks` | the release key |
| `legacy-debug.keystore` | copy of the original debug key |
| `lineage.bin` | rotation lineage |
| `signing.properties` | paths and passwords |

**Back this folder up somewhere safe** (e.g. a password manager or encrypted drive). Without
`release.jks` and its password, no future build can update an installed copy. You would have to
uninstall, which deletes the library and notes. Set `SCHOLIA_SIGNING` to use the folder from
another location.

## Using it

- **Import:** tap **Import Book** and pick a PDF or EPUB. The file stays where it is. Scholia
  keeps permission to reopen it.
- **Read:**
  - **PDFs:** swipe sideways, one page per screen. Pinch to zoom; drag to pan while zoomed.
  - **EPUBs:** scroll vertically.
  - The arrows at the bottom step pages (PDF) or chapters (EPUB).
- **Zen mode:** tap the full-screen icon in the reader's top bar, or tap a blank part of the page.
  - The app bars and Android's status and navigation bars hide, and the page fills the screen.
  - Tap the page again, or press Back, to bring the controls back.
  - Swiping in from a screen edge peeks the system bars without leaving zen mode.
  - Scholia remembers the setting, so books open in zen mode until you turn it off.
- **Highlight:** long-press a word, then drag to extend the selection or adjust the handles.
  - Tap a colour to highlight.
  - Tap **Note** to highlight and write a note.
  - Tap an existing highlight to edit its note or colour, or delete it.
- **Notes:** the notes icon in the reader, or long-press a book in the library.
  - Filter by colour or search the text.
  - Swipe left to delete (with Undo).
  - **Jump to…** opens the book at that passage.
  - **Export** shares all notes as Markdown.
- **Remove a book:** long-press it in the library. This removes its highlights too; the file
  itself is untouched.

## Stack

Kotlin, Jetpack Compose (Material 3), single activity, Room, kotlinx-serialization.
EPUB uses the Readium Kotlin Toolkit 3.4 in scroll mode, with Readium Decorations for highlights.
UI design from the Stitch project in `docs/design/`.

**PDF engine: PdfiumAndroid, not `androidx.pdf`.** The spec preferred `androidx.pdf`'s
`PdfViewerFragment`. Its `PdfView` (1.0.0-beta01) only lays pages out as one continuous vertical
scroll; it has no one-page-per-screen horizontal paging. Horizontal page swiping is a core
requirement, so Scholia uses the spec's fallback, `io.legere:pdfiumandroid` (2.0.3):

- pages are rendered to bitmaps in a `HorizontalPager`;
- text selection, word snapping and highlight rectangles are built from pdfium's per-character
  bounding boxes.

PDF highlights are stored as `{page, start, end}` character ranges. EPUB highlights are stored as
Readium `Locator` JSON.

## Fonts

Merriweather and Plus Jakarta Sans are bundled under the SIL Open Font License 1.1; see
`licenses/`.
