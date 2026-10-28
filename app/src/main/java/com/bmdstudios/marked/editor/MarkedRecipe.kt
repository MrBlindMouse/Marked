package com.bmdstudios.marked.editor

import android.content.Context
import com.bmdstudios.marked.model.Layer
import com.bmdstudios.marked.model.LayerStyle
import com.bmdstudios.marked.model.LayerTransform
import com.bmdstudios.marked.model.LayerType
import com.bmdstudios.marked.model.TimestampFormat
import com.bmdstudios.marked.model.TimestampPosition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * A saved mark stack that can be re-applied to photos or video frames (Phase 5 templates).
 *
 * @property name Human-readable recipe name.
 * @property layers Serializable layer snapshots (text / signature / sticker / timestamp / svg marks;
 * bitmap payloads are not JSON serializable so asset marks are not part of v1 recipes; svg marks
 * carry their source string instead).
 */
@Serializable
data class MarkedRecipe(
    val name: String,
    val layers: List<RecipeLayer>,
)

/** Serializable snapshot of a single mark layer plus its type-specific payload. */
@Serializable
data class RecipeLayer(
    val type: String,
    val transform: LayerTransform,
    val style: LayerStyle,
    val payload: RecipePayload? = null,
) {
    /** Rehydrates a renderable [Layer] from this snapshot; [index] keeps ids unique per layer. */
    fun toLayer(index: Int): Layer = when (type) {
        LayerType.TEXT.name -> {
            val payload = payload as? RecipePayload.Text ?: RecipePayload.Text()
            Layer.TextLayer(
                id = "recipe-layer-$index",
                type = LayerType.TEXT,
                transform = transform,
                style = style,
                text = payload.text,
                fontSize = payload.fontSize,
                color = payload.color,
                bold = payload.bold,
                fontFamily = payload.fontFamily,
                tracking = payload.tracking,
            )
        }

        LayerType.TIMESTAMP.name -> {
            val payload = payload as? RecipePayload.Timestamp ?: RecipePayload.Timestamp()
            Layer.TimestampLayer(
                id = "recipe-layer-$index",
                type = LayerType.TIMESTAMP,
                transform = transform,
                style = style,
                format = payload.format,
                fontSize = payload.fontSize,
                position = payload.position,
            )
        }

        LayerType.SIGNATURE.name -> {
            val payload = payload as? RecipePayload.Signature ?: RecipePayload.Signature()
            Layer.SignatureLayer(
                id = "recipe-layer-$index",
                type = LayerType.SIGNATURE,
                transform = transform,
                style = style,
                color = payload.color,
                width = payload.width,
                points = payload.points,
            )
        }

        LayerType.STICKER.name -> {
            val payload = payload as? RecipePayload.Sticker ?: RecipePayload.Sticker()
            Layer.StickerLayer(
                id = "recipe-layer-$index",
                type = LayerType.STICKER,
                transform = transform,
                style = style,
                emoji = payload.emoji,
                fontSize = payload.fontSize,
            )
        }

        LayerType.SVG.name -> {
            val payload = payload as? RecipePayload.Svg ?: RecipePayload.Svg()
            Layer.SvgLayer(
                id = "recipe-layer-$index",
                type = LayerType.SVG,
                transform = transform,
                style = style,
                svgSource = payload.svgSource,
            )
        }

        else -> throw IllegalArgumentException("Unsupported recipe layer type: $type")
    }
}

/** Type-specific payload carried by a [RecipeLayer]. */
@Serializable
sealed interface RecipePayload {
    @Serializable
    data class Text(
        val text: String = "Your text",
        val fontSize: Float = 48f,
        val color: Int = -0x1, // opaque white (ARGB 0xFFFFFFFF)
        val bold: Boolean = false,
        val fontFamily: String? = null,
        val tracking: Float = 0f,
    ) : RecipePayload

    @Serializable
    data class Timestamp(
        val format: TimestampFormat = TimestampFormat.HH_MM_SS,
        val fontSize: Float = 64f,
        val position: TimestampPosition = TimestampPosition.TOP_LEFT,
    ) : RecipePayload

    @Serializable
    data class Signature(
        val color: Int = -16777216, // opaque black (ARGB 0xFF000000)
        val width: Float = 6f,
        val points: List<Layer.SignaturePoint> = emptyList(),
    ) : RecipePayload

    @Serializable
    data class Sticker(
        val emoji: String = "😀",
        val fontSize: Float = 64f,
    ) : RecipePayload

    /** Vector SVG mark source; bitmap payloads are not serializable, source strings are. */
    @Serializable
    data class Svg(
        val svgSource: String = "",
    ) : RecipePayload
}

private val recipeJson = Json { ignoreUnknownKeys = true }

/** Persists a recipe into the app's private files dir. */
fun saveRecipe(context: Context, recipe: MarkedRecipe) {
    val dir = java.io.File(context.filesDir, "recipes").apply { mkdirs() }
    val file = java.io.File(dir, "${recipe.name.replace(Regex("[^A-Za-z0-9]"), "_")}.json")
    file.writeText(recipeJson.encodeToString(MarkedRecipe.serializer(), recipe))
}

/** Loads all saved recipes, sorted by name. */
fun loadRecipes(context: Context): List<MarkedRecipe> {
    val dir = java.io.File(context.filesDir, "recipes")
    if (!dir.isDirectory) return emptyList()
    return dir.listFiles()
        ?.filter { it.extension == "json" }
        ?.mapNotNull { file ->
            runCatching { recipeJson.decodeFromString(MarkedRecipe.serializer(), file.readText()) }.getOrNull()
        }
        ?.sortedBy { it.name }
        ?: emptyList()
}


