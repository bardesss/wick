package io.github.bardesss.wick.ui

import coil3.ImageLoader
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.svg.SvgDecoder
import okio.Buffer

// a declaration such as fill:color(display-p3 .98 .3 0), with or without its semicolon
private val WideGamutDeclaration = Regex("""[a-zA-Z-]+\s*:\s*color\([^)]*\)\s*;?""")

/**
 * Design tools write a plain colour followed by a display-p3 one, for example
 * `style="fill:#fb4c01;fill:color(display-p3 .98 .3 0)"`. The svg library takes the second as no
 * fill at all and draws nothing, so drop those declarations and the plain colour stays.
 */
fun withoutWideGamutColours(svg: String): String = WideGamutDeclaration.replace(svg, "")

/** Decodes svg icons through [withoutWideGamutColours]; anything else is left to other decoders. */
class SvgIconDecoder(private val result: SourceFetchResult, private val options: Options) : Decoder {
    override suspend fun decode(): DecodeResult {
        val text = result.source.source().use { it.readUtf8() }
        val source = ImageSource(Buffer().writeUtf8(withoutWideGamutColours(text)), options.fileSystem)
        return SvgDecoder(source, options).decode()
    }

    class Factory : Decoder.Factory {
        private val svg = SvgDecoder.Factory()

        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            // the stock factory only looks at the type and the first bytes, so ask it what is svg
            if (svg.create(result, options, imageLoader) == null) return null
            return SvgIconDecoder(result, options)
        }
    }
}
