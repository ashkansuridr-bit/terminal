package app.terminalssh.secure.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps file extensions to appropriate Material icons. Suggestion #40 — differentiate
 * file types beyond the binary folder/file/symlink split.
 */
object FileTypeIcons {

    private val imageExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico", "tiff", "tif", "heic", "heif", "avif",
    )
    private val videoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "webm", "flv", "wmv", "m4v", "3gp", "ogv",
    )
    private val audioExtensions = setOf(
        "mp3", "flac", "wav", "ogg", "aac", "m4a", "wma", "opus", "aiff", "mid", "midi",
    )
    private val archiveExtensions = setOf(
        "zip", "tar", "gz", "tgz", "bz2", "xz", "7z", "rar", "zst", "lz4", "lzma",
        "deb", "rpm", "apk", "dmg", "iso",
    )
    private val codeExtensions = setOf(
        "kt", "kts", "java", "py", "rb", "js", "ts", "jsx", "tsx", "go", "rs", "c", "cpp", "h",
        "cs", "swift", "php", "lua", "r", "scala", "sh", "bash", "zsh", "fish",
        "html", "css", "scss", "less", "xml", "json", "yaml", "yml", "toml", "ini", "conf",
        "sql", "graphql", "proto",
    )
    private val documentExtensions = setOf(
        "doc", "docx", "odt", "rtf", "txt", "md", "markdown", "tex", "log", "csv", "tsv",
    )
    private val spreadsheetExtensions = setOf(
        "xls", "xlsx", "ods", "csv", "tsv",
    )
    private val pdfExtensions = setOf("pdf")

    /**
     * Returns the most appropriate icon for a file based on its extension.
     * Falls back to a generic document icon for unrecognized extensions.
     */
    fun forExtension(name: String): ImageVector {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext in pdfExtensions -> Icons.Outlined.PictureAsPdf
            ext in imageExtensions -> Icons.Outlined.Image
            ext in videoExtensions -> Icons.Outlined.SmartDisplay
            ext in audioExtensions -> Icons.Outlined.Audiotrack
            ext in archiveExtensions -> Icons.Outlined.Archive
            ext in codeExtensions -> Icons.Outlined.Code
            ext in spreadsheetExtensions -> Icons.Outlined.TableChart
            else -> Icons.Outlined.Description
        }
    }

    /** Human-readable file type label for display (e.g., "PDF", "Image", "Archive"). */
    fun typeLabel(name: String, labels: FileTypeLabels): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext in pdfExtensions -> labels.pdf
            ext in imageExtensions -> labels.image
            ext in videoExtensions -> labels.video
            ext in audioExtensions -> labels.audio
            ext in archiveExtensions -> labels.archive
            ext in codeExtensions -> labels.code
            ext in documentExtensions -> labels.document
            else -> ""
        }
    }

    /** Simple MIME type guess for file content type detection. */
    fun mimeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext in imageExtensions -> "image/$ext"
            ext in videoExtensions -> "video/$ext"
            ext in audioExtensions -> "audio/$ext"
            ext in pdfExtensions -> "application/pdf"
            ext in archiveExtensions -> "application/x-compressed"
            ext == "json" -> "application/json"
            ext == "xml" -> "text/xml"
            ext == "html" || ext == "htm" -> "text/html"
            ext == "csv" -> "text/csv"
            else -> "text/plain"
        }
    }

    /** Label set loaded once from string resources; avoids recomposition overhead. */
    data class FileTypeLabels(
        val image: String,
        val video: String,
        val audio: String,
        val archive: String,
        val code: String,
        val document: String,
        val pdf: String,
    )
}
