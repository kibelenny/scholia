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

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew assembleRelease
cp app/build/outputs/apk/release/app-release.apk release/reader.apk
```

`./gradlew assembleDebug` builds a debug APK in `app/build/outputs/apk/debug/`.

The release build is signed with this machine's Android debug key (`~/.android/debug.keystore`).
An APK built on another machine has a different signature. It won't install over this one until
you uninstall the old app, which deletes its library and notes.

## Using it

- **Import:** tap **Import Book** and pick a PDF or EPUB. The file stays where it is. Scholia
  keeps permission to reopen it.
- **Read:**
  - **PDFs:** swipe sideways, one page per screen. Pinch to zoom; drag to pan while zoomed.
  - **EPUBs:** scroll vertically.
  - The arrows at the bottom step pages (PDF) or chapters (EPUB).
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
