package com.scholia.reader.epub

import android.content.Context
import android.net.Uri
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/** Opens EPUBs straight from their content:// URI with Readium. */
class EpubOpener(context: Context) {
    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    private val publicationOpener = PublicationOpener(
        DefaultPublicationParser(context, httpClient, assetRetriever, pdfFactory = null),
    )

    suspend fun open(uri: Uri): Result<Publication> {
        val url = uri.toUrl() as? AbsoluteUrl
            ?: return Result.failure(IllegalArgumentException("Unsupported location: $uri"))
        val asset = assetRetriever.retrieve(url).getOrElse {
            return Result.failure(Exception("Couldn't read the file (${it.message})"))
        }
        val publication = publicationOpener.open(asset, allowUserInteraction = false).getOrElse {
            asset.close()
            return Result.failure(Exception("Not a readable EPUB (${it.message})"))
        }
        return Result.success(publication)
    }
}
