package com.bmdstudios.marked.editor

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bmdstudios.marked.MarkedExitCta
import com.bmdstudios.marked.model.Layer
import com.bmdstudios.marked.model.LayerStyle
import com.bmdstudios.marked.model.LayerTransform
import com.bmdstudios.marked.model.LayerType
import com.bmdstudios.marked.model.Shadow
import com.bmdstudios.marked.model.TimestampFormat
import com.bmdstudios.marked.model.TimestampPosition
import com.bmdstudios.marked.settings.MarkedSettings
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalTime

private const val TAG = "MarkedEditor"
private const val MAX_SIGNATURE_POINTS = 2048
private const val DEFAULT_STICKER_EMOJI = "😀"
private val STICKER_EMOJI_CHOICES = listOf(
    "😀", "😂", "😍", "👍", "❤️", "🎉", "⭐", "🔥", "✌️", "😎",
)
private const val TEXT_TIMESTAMP_FONT_SIZE = 64f
private val GRADE_CONTRAST_MIN = 0.5f
private val GRADE_CONTRAST_MAX = 2f
private val GRADE_EXPOSURE_MIN = 0.5f
private val GRADE_EXPOSURE_MAX = 2f
private val GRADE_SATURATION_MIN = 0f
private val GRADE_SATURATION_MAX = 2f

/**
 * One media item the user picked for marking on the Phase 1 landing screen.
 *
 * @property id Stable content-URI based identifier used for multi-select state.
 * @property bitmap Decoded bitmap for image media; null for video media.
 * @property videoUri Content URI of the video; null for image media.
 */
data class MarkedMedia(
    val id: String,
    val bitmap: Bitmap?,
    val videoUri: Uri?,
)

/** Top-level navigation routes of the mark flow. */
private enum class MarkedRoute {
    Landing,
    MarkEditor,
    Settings,
    Recipes,
    Assets,
}

/** Swatch colors offered by the text / signature mark color pickers. */
private val MARK_TEXT_SWATCH_COLORS = intArrayOf(
    0xFFFFFFFF.toInt(),
    0xFF000000.toInt(),
    0xFFFF0000.toInt(),
    0xFF00FF00.toInt(),
    0xFF0000FF.toInt(),
    0xFFFFFF00.toInt(),
    0xFF00FFFF.toInt(),
    0xFFFF00FF.toInt(),
    0xFFFF8800.toInt(),
    0xFF8844FF.toInt(),
)

/**
 * Computes the transform that anchors a timestamp mark to a corner [position] preset.
 *
 * Text-like layers anchor top-left; corner presets shift that anchor so the timestamp hugs
 * the chosen edge/corner of the source media.
 */
internal fun timestampAnchor(
    sourceWidth: Float,
    sourceHeight: Float,
    position: TimestampPosition,
    textWidth: Float,
    textHeight: Float,
    scale: Float = 1f,
): LayerTransform = LayerTransform(
    x = when (position) {
        TimestampPosition.TOP_LEFT -> 0f
        TimestampPosition.TOP_RIGHT -> (sourceWidth - textWidth).coerceAtLeast(0f)
        TimestampPosition.BOTTOM_LEFT -> 0f
        TimestampPosition.BOTTOM_RIGHT -> (sourceWidth - textWidth).coerceAtLeast(0f)
    },
    y = when (position) {
        TimestampPosition.TOP_LEFT -> 0f
        TimestampPosition.TOP_RIGHT -> 0f
        TimestampPosition.BOTTOM_LEFT -> (sourceHeight - textHeight).coerceAtLeast(0f)
        TimestampPosition.BOTTOM_RIGHT -> (sourceHeight - textHeight).coerceAtLeast(0f)
    },
    scale = scale,
)

/** Measures display-width / height of [text] at [fontSize] using an anti-aliased paint. */
internal fun measureTextDimensions(text: String, fontSize: Float): Pair<Float, Float> {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.textSize = fontSize }
    val textWidth = paint.measureText(text)
    val textHeight = (paint.fontMetrics.descent - paint.fontMetrics.ascent).coerceAtLeast(fontSize * 0.8f)
    return textWidth to textHeight
}

/**
 * Converts a signature stroke point captured in display space into the layer's authored
 * space: authored = display / scale - (transform.x, transform.y).
 */
internal fun signatureAuthoredPoint(
    layer: Layer.SignatureLayer,
    displayX: Float,
    displayY: Float,
): Layer.SignaturePoint = Layer.SignaturePoint(
    x = displayX / layer.transform.scale - layer.transform.x,
    y = displayY / layer.transform.scale - layer.transform.y,
)

/**
 * Pointer-input modifier that captures freehand signature strokes: every move of the tracked
 * pointer emits a point through [onPoint]. Stroke finalization is implicit (the last captured
 * point stays; a stroke renders from >= 2 points).
 */
private fun Modifier.signatureStroke(
    layerId: String,
    onPoint: (Layer.SignaturePoint) -> Unit,
): Modifier = pointerInput(layerId) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val pointerId = down.id
        var tracking = true
        while (tracking) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
            if (change.pressed) {
                onPoint(Layer.SignaturePoint(x = change.position.x, y = change.position.y))
            } else {
                tracking = false
            }
        }
    }
}

/**
 * Editor core (Phase 1 photo / Phase 2 video): pick media, place marks, export.
 */
@Composable
fun MarkedEditorScreen(onExit: () -> Unit = {}) {
    val context = LocalContext.current
    var route by remember { mutableStateOf(MarkedRoute.Landing) }

    // Donation CTA visibility: owned here so every screen can open it from its back affordance.
    var exitCtaVisible by remember { mutableStateOf(false) }

    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var layers by remember { mutableStateOf<List<Layer>>(emptyList()) }
    var selectedLayerId by remember { mutableStateOf<String?>(null) }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var isExportingVideo by remember { mutableStateOf(false) }
    var videoFrameProgress by remember { mutableStateOf(0L) }
    val undoStack = remember { mutableStateOf<List<List<Layer>>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    var recipes by remember { mutableStateOf<List<MarkedRecipe>>(emptyList()) }
    var showSaveRecipeDialog by remember { mutableStateOf(false) }
    var showBatchRecipeDialog by remember { mutableStateOf(false) }
    var batchRecipe by remember { mutableStateOf<MarkedRecipe?>(null) }
    var settings by remember { mutableStateOf(MarkedSettings()) }

    // Phase 1 landing media list + multi-select state.
    var mediaItems by remember { mutableStateOf<List<MarkedMedia>>(emptyList()) }
    var selectedMediaIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        settings = MarkedSettings.load(context)
        recipes = loadRecipes(context)
    }

    /** Loads a picked media item into the mark editor and switches to the mark editor route. */
    fun loadMediaIntoEditor(media: MarkedMedia) {
        Log.d(TAG, "Loading picked media into mark editor: ${media.id}")
        if (media.videoUri != null) {
            photo = null
            layers = emptyList()
            selectedLayerId = null
            videoUri = media.videoUri
        } else {
            photo = media.bitmap
            layers = emptyList()
            selectedLayerId = null
            videoUri = null
        }
    }

    /** Adds a freshly picked media item to the landing list and selects it. */
    fun addPickedMedia(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        val contentType = context.contentResolver.getType(uri)
        val id = uri.toString()
        if (contentType?.startsWith("video/", ignoreCase = true) == true) {
            Log.d(TAG, "Landing picked video: $uri")
            mediaItems = mediaItems + MarkedMedia(id = id, bitmap = null, videoUri = uri)
            selectedMediaIds = setOf(id)
            return
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
        if (bitmap == null) return
        Log.d(TAG, "Landing decoded photo bitmap: ${bitmap.width}x${bitmap.height}")
        mediaItems = mediaItems + MarkedMedia(id = id, bitmap = bitmap, videoUri = null)
        selectedMediaIds = setOf(id)
    }

    val markInkColor = MaterialTheme.colorScheme.primary.toArgb()
    val compositeBitmap = remember(photo, layers) {
        photo?.let { renderMark(it, layers) }
    }

    fun handlePickedImage(uri: Uri, onBitmap: (Bitmap?) -> Unit) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        val contentType = context.contentResolver.getType(uri)
        if (contentType?.startsWith("image/", ignoreCase = true) != true) return
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
        onBitmap(bitmap)
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            Log.d(TAG, "Media picked via system picker: $uri")
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            val contentType = context.contentResolver.getType(uri)
            if (contentType?.startsWith("video/", ignoreCase = true) == true) {
                Log.d(TAG, "Picked video: $uri (${contentType})")
                photo = null
                layers = emptyList()
                selectedLayerId = null
                videoUri = uri
            } else {
                val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
                if (bitmap != null) {
                    Log.d(TAG, "Decoded photo bitmap: ${bitmap.width}x${bitmap.height}")
                    photo = bitmap
                    layers = emptyList()
                    selectedLayerId = null
                    videoUri = null
                }
            }
        },
    )

    val landingMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            Log.d(TAG, "Landing media picked: $uri")
            addPickedMedia(uri)
        },
    )

    val batchPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
        onResult = { uris: List<Uri>? ->
            if (uris == null) return@rememberLauncherForActivityResult
            val layersToApply = batchRecipe?.layers?.mapIndexed { index, recipeLayer ->
                recipeLayer.toLayer(index)
            } ?: layers
            Log.d(TAG, "Batch picked ${uris.size} photos")
            uris.forEach { uri ->
                handlePickedImage(uri) { bitmap ->
                    val composite = bitmap?.let { source -> renderMark(source, layersToApply) }
                    if (composite != null) {
                        exportMarkToGallery(context, composite, settings)?.let { exported ->
                            Log.d(TAG, "Batch exported marked photo: $exported")
                        }
                    }
                }
            }
        },
    )

    val batchVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
        onResult = { uris: List<Uri>? ->
            if (uris == null) return@rememberLauncherForActivityResult
            val layersToApply = batchRecipe?.layers?.mapIndexed { index, recipeLayer ->
                recipeLayer.toLayer(index)
            } ?: layers
            Log.d(TAG, "Batch picked ${uris.size} videos")
            uris.forEach { uri ->
                coroutineScope.launch {
                    val outputFile = File(context.cacheDir, "Marked_${System.currentTimeMillis()}.mp4")
                    val encoded = encodeMarkedVideo(
                        context = context,
                        sourceUri = uri,
                        outputFile = outputFile,
                        layers = layersToApply,
                        onProgress = { frames -> videoFrameProgress = frames },
                    )
                    if (encoded != null) {
                        exportVideoToGallery(context, encoded, settings)?.let { exported ->
                            Log.d(TAG, "Batch exported marked video: $exported")
                        }
                    }
                }
            }
        },
    )

    val assetPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            Log.d(TAG, "Asset picked via system picker: $uri")
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            val contentType = context.contentResolver.getType(uri)
            if (contentType?.startsWith("image/", ignoreCase = true) != true) return@rememberLauncherForActivityResult
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
            if (bitmap == null) return@rememberLauncherForActivityResult
            val layer = Layer.AssetLayer(
                id = "asset-${layers.size + 1}-${System.currentTimeMillis()}",
                type = LayerType.ASSET,
                transform = LayerTransform(x = 0f, y = 0f, scale = 1f),
                style = LayerStyle(),
                bitmap = bitmap,
            )
            val snapshot: List<Layer> = layers
            val updated: MutableList<List<Layer>> = ArrayList(undoStack.value)
            updated.add(snapshot)
            undoStack.value = updated
            layers = layers + layer
            selectedLayerId = layer.id
        },
    )

    val transformState = rememberTransformableState { zoomChange, panChange, rotationChange ->
        val layer = layers.firstOrNull { it.id == selectedLayerId } ?: return@rememberTransformableState
        val updated = layer.applyGesture(zoomChange = zoomChange, panDeltaX = panChange.x, panDeltaY = panChange.y)
        if (updated !== layer) {
            layers = layers.map { if (it.id == layer.id) updated else it }
        }
    }

    fun commitLayers(next: List<Layer>) {
        val updated: MutableList<List<Layer>> = ArrayList(undoStack.value)
        updated.add(layers)
        undoStack.value = updated
        layers = next
    }

    fun undoLastLayerCommit() {
        val previous = undoStack.value.lastOrNull() ?: return
        undoStack.value = undoStack.value.dropLast(1)
        layers = previous
        if (previous.none { it.id == selectedLayerId }) selectedLayerId = null
    }

    fun deleteLayerById(id: String) {
        commitLayers(layers.filterNot { it.id == id })
        if (selectedLayerId == id) selectedLayerId = null
    }

    fun moveLayerById(id: String, towardsTop: Boolean) {
        val currentIndex = layers.indexOfFirst { it.id == id }
        if (currentIndex < 0) return
        val mutable = layers.toMutableList()
        val layer = mutable.removeAt(currentIndex)
        val insertAt = if (towardsTop) mutable.lastIndex + 1 else 0
        mutable.add(insertAt.coerceIn(0, mutable.size), layer)
        commitLayers(mutable.toList())
    }

    fun createTextLayer(color: Int = MARK_INK_COLOR): Layer.TextLayer {
        val layer = Layer.TextLayer(
            id = "text-${layers.size + 1}-${System.currentTimeMillis()}",
            type = LayerType.TEXT,
            transform = LayerTransform(
                x = photo?.width?.toFloat()?.div(2f) ?: 0f,
                y = photo?.height?.toFloat()?.div(2f) ?: 0f,
                scale = 1f,
            ),
            style = LayerStyle(),
            text = "Your text",
            fontSize = 64f,
            color = color,
        )
        commitLayers(layers + layer)
        selectedLayerId = layer.id
        return layer
    }

    fun createTimestampLayer(
        format: TimestampFormat = TimestampFormat.HH_MM_SS,
        position: TimestampPosition = TimestampPosition.BOTTOM_RIGHT,
    ): Layer.TimestampLayer {
        val text = format.format(LocalTime.now())
        val (textWidth, textHeight) = measureTextDimensions(text, TEXT_TIMESTAMP_FONT_SIZE)
        val layer = Layer.TimestampLayer(
            id = "timestamp-${layers.size + 1}-${System.currentTimeMillis()}",
            type = LayerType.TIMESTAMP,
            transform = timestampAnchor(
                sourceWidth = photo?.width?.toFloat() ?: 0f,
                sourceHeight = photo?.height?.toFloat() ?: 0f,
                position = position,
                textWidth = textWidth,
                textHeight = textHeight,
                scale = 1f,
            ),
            style = LayerStyle(),
            format = format,
            fontSize = TEXT_TIMESTAMP_FONT_SIZE,
            position = position,
        )
        commitLayers(layers + layer)
        selectedLayerId = layer.id
        return layer
    }

    fun createSignatureLayer(color: Int = MARK_INK_COLOR): Layer.SignatureLayer {
        val layer = Layer.SignatureLayer(
            id = "signature-${layers.size + 1}-${System.currentTimeMillis()}",
            type = LayerType.SIGNATURE,
            transform = LayerTransform(
                x = (photo?.width?.toFloat() ?: 0f) / 2f,
                y = (photo?.height?.toFloat() ?: 0f) / 2f,
                scale = 1f,
            ),
            style = LayerStyle(),
            color = color,
            width = 6f,
        )
        commitLayers(layers + layer)
        selectedLayerId = layer.id
        return layer
    }

    fun createStickerLayer(emoji: String): Layer.StickerLayer {
        val layer = Layer.StickerLayer(
            id = "sticker-${layers.size + 1}-${System.currentTimeMillis()}",
            type = LayerType.STICKER,
            transform = LayerTransform(
                x = (photo?.width?.toFloat() ?: 0f) / 2f,
                y = (photo?.height?.toFloat() ?: 0f) / 2f,
                scale = 1f,
            ),
            style = LayerStyle(),
            emoji = emoji,
            fontSize = 64f,
        )
        commitLayers(layers + layer)
        selectedLayerId = layer.id
        return layer
    }

    fun createSvgLayer(mark: BuiltinSvgMark): Layer.SvgLayer {
        val layer = Layer.SvgLayer(
            id = "svg-${layers.size + 1}-${System.currentTimeMillis()}",
            type = LayerType.SVG,
            transform = LayerTransform(
                x = (photo?.width?.toFloat() ?: 0f) / 2f - mark.width / 2f,
                y = (photo?.height?.toFloat() ?: 0f) / 2f - mark.height / 2f,
                scale = 1f,
            ),
            style = LayerStyle(),
            svgSource = mark.svgSource,
        )
        commitLayers(layers + layer)
        selectedLayerId = layer.id
        return layer
    }

    fun recipePayloadFor(layer: Layer): RecipePayload? = when (layer) {
        is Layer.TextLayer -> RecipePayload.Text(
            text = layer.text,
            fontSize = layer.fontSize,
            color = layer.color,
            bold = layer.bold,
            fontFamily = layer.fontFamily,
            tracking = layer.tracking,
        )

        is Layer.TimestampLayer -> RecipePayload.Timestamp(
            format = layer.format,
            fontSize = layer.fontSize,
            position = layer.position,
        )

        is Layer.SignatureLayer -> RecipePayload.Signature(
            color = layer.color,
            width = layer.width,
            points = layer.points,
        )

        is Layer.StickerLayer -> RecipePayload.Sticker(
            emoji = layer.emoji,
            fontSize = layer.fontSize,
        )

        is Layer.AssetLayer -> null
        is Layer.SvgLayer -> RecipePayload.Svg(svgSource = layer.svgSource)
    }

    fun saveCurrentMarksAsRecipe(name: String) {
        val recipe = MarkedRecipe(
            name = name,
            layers = layers.map { layer ->
                RecipeLayer(
                    type = layer.type.name,
                    transform = layer.transform,
                    style = layer.style,
                    payload = recipePayloadFor(layer),
                )
            },
        )
        saveRecipe(context, recipe)
        recipes = loadRecipes(context)
        showSaveRecipeDialog = false
    }

    fun applyRecipe(recipe: MarkedRecipe) {
        val mapped = recipe.layers.mapIndexed { index, recipeLayer -> recipeLayer.toLayer(index) }
        commitLayers(mapped)
        selectedLayerId = mapped.lastOrNull()?.id
    }

    /** Applies the current mark layers to the picked photo and exports the result. */
    fun processMarkedPhoto() {
        val composite = photo?.let { renderMark(it, layers) } ?: return
        Log.d(TAG, "Processing marked photo with ${layers.size} layers")
        shareMark(context, composite, settings)
    }

    // System back: on the landing screen the donation CTA owns the double-back exit (first back
    // shows the CTA, second closes it and exits the app); on every other screen back navigates to
    // the landing screen.
    BackHandler(enabled = true) {
        if (route == MarkedRoute.Landing) {
            if (exitCtaVisible) {
                exitCtaVisible = false
                onExit()
            } else {
                exitCtaVisible = true
            }
        } else {
            route = MarkedRoute.Landing
        }
        true
    }

    when (route) {
        MarkedRoute.MarkEditor -> {
    val selectedLayer: Layer? = layers.firstOrNull { it.id == selectedLayerId }
    val selectedSignatureLayer: Layer.SignatureLayer? = selectedLayer as? Layer.SignatureLayer

    val activeVideoUri = videoUri
    if (activeVideoUri != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            MarkedVideoPreview(videoUri = activeVideoUri, modifier = Modifier.fillMaxSize())

            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { route = MarkedRoute.Landing }) {
                    Text(text = "<-Back")
                }
            }
            if (isExportingVideo) {
                Text(
                    text = "Encoding marked video… ${videoFrameProgress} frames",
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            selectedLayer?.let { layer ->
                MarkLayerControls(
                    layer = layer,
                    sourceWidth = photo?.width?.toFloat() ?: 0f,
                    sourceHeight = photo?.height?.toFloat() ?: 0f,
                    onLayerChange = { updated ->
                        layers = layers.map { if (it.id == updated.id) updated else it }
                    },
                )
            }

            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        if (isExportingVideo) return@OutlinedButton
                        isExportingVideo = true
                        videoFrameProgress = 0L
                        coroutineScope.launch {
                            val outputFile = File(context.cacheDir, "Marked_${System.currentTimeMillis()}.mp4")
                            val encoded = encodeMarkedVideo(
                                context = context,
                                sourceUri = activeVideoUri,
                                outputFile = outputFile,
                                layers = layers,
                                onProgress = { frames -> videoFrameProgress = frames },
                            )
                            isExportingVideo = false
                            if (encoded != null) {
                                exportVideoToGallery(context, encoded, settings)?.let { uri -> shareVideo(context, uri, settings) }
                            }
                        }
                    },
                ) {
                    Text(text = if (isExportingVideo) "Encoding…" else "Process marked video")
                }
                OutlinedButton(
                    onClick = { if (layers.isNotEmpty()) showBatchRecipeDialog = true },
                    enabled = layers.isNotEmpty(),
                ) {
                    Text(text = "Batch")
                }
            }

            AddMarkMenu(
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                recipes = recipes,
                onAddText = { createTextLayer(markInkColor) },
                onAddTimestamp = { createTimestampLayer(TimestampFormat.HH_MM_SS, TimestampPosition.BOTTOM_RIGHT) },
                onAddSignature = { createSignatureLayer(markInkColor) },
                onAddSticker = { emoji -> createStickerLayer(emoji) },
                onAddAsset = {
                    assetPicker.launch(
                        PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            .build(),
                    )
                },
                onAddSvg = { mark -> createSvgLayer(mark) },
                onSaveMarks = { showSaveRecipeDialog = true },
                onApplyRecipe = { recipe -> applyRecipe(recipe) },
            )

            SaveRecipeDialog(
                visible = showSaveRecipeDialog,
                initialName = "Mark set ${recipes.size + 1}",
                onDismiss = { showSaveRecipeDialog = false },
                onSave = { trimmed -> if (trimmed.isNotEmpty()) saveCurrentMarksAsRecipe(trimmed) },
            )

            BatchRecipeDialog(
                visible = showBatchRecipeDialog,
                recipes = recipes,
                onDismiss = { showBatchRecipeDialog = false },
                onSelect = { recipe ->
                    batchRecipe = recipe
                    showBatchRecipeDialog = false
                    batchVideoPicker.launch(
                        PickVisualMediaRequest.Builder()
                            .setMaxItems(10)
                            .setMediaType(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            .build(),
                    )
                },
            )

        }
    } else if (photo == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { route = MarkedRoute.Landing }) {
                    Text(text = "<-Back")
                }
            }
            OutlinedButton(
                onClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            .build(),
                    )
                },
            ) {
                Text(text = "Add a photo")
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .transformable(state = transformState),
        ) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { route = MarkedRoute.Landing }) {
                    Text(text = "<-Back")
                }
            }

            if (compositeBitmap != null) {
                Image(
                    painter = BitmapPainter(compositeBitmap.asImageBitmap()),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            selectedLayer?.let { layer ->
                MarkLayerControls(
                    layer = layer,
                    sourceWidth = photo?.width?.toFloat() ?: 0f,
                    sourceHeight = photo?.height?.toFloat() ?: 0f,
                    onLayerChange = { updated ->
                        layers = layers.map { if (it.id == updated.id) updated else it }
                    },
                )
            }

            selectedSignatureLayer?.let { signature ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .signatureStroke(layerId = signature.id) { point ->
                            val current = layers.firstOrNull { it.id == signature.id } as? Layer.SignatureLayer ?: return@signatureStroke
                            val authored = signatureAuthoredPoint(
                                layer = current,
                                displayX = point.x,
                                displayY = point.y,
                            )
                            val last = current.points.lastOrNull()
                            if (last != null && last.x == authored.x && last.y == authored.y) return@signatureStroke
                            val updated = current.copy(points = (current.points + authored).takeLast(MAX_SIGNATURE_POINTS))
                            layers = layers.map { if (it.id == current.id) updated else it }
                        },
                )
            }

            Text(
                text = "Marked",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )

            LayerTray(
                layers = layers,
                selectedLayerId = selectedLayerId,
                canUndo = undoStack.value.isNotEmpty(),
                onSelect = { id -> selectedLayerId = id },
                onMove = { id, towardsTop -> moveLayerById(id, towardsTop) },
                onDelete = { id -> deleteLayerById(id) },
                onUndo = { undoLastLayerCommit() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 88.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { processMarkedPhoto() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "Process")
                    }
                    if (layers.isNotEmpty()) {
                        OutlinedButton(onClick = { if (layers.isNotEmpty()) showBatchRecipeDialog = true }) {
                            Text(text = "Batch")
                        }
                    }
                }
            }

            AddMarkMenu(
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                recipes = recipes,
                onAddText = { createTextLayer(markInkColor) },
                onAddTimestamp = { createTimestampLayer(TimestampFormat.HH_MM_SS, TimestampPosition.BOTTOM_RIGHT) },
                onAddSignature = { createSignatureLayer(markInkColor) },
                onAddSticker = { emoji -> createStickerLayer(emoji) },
                onAddAsset = {
                    assetPicker.launch(
                        PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            .build(),
                    )
                },
                onAddSvg = { mark -> createSvgLayer(mark) },
                onSaveMarks = { showSaveRecipeDialog = true },
                onApplyRecipe = { recipe -> applyRecipe(recipe) },
            )

            SaveRecipeDialog(
                visible = showSaveRecipeDialog,
                initialName = "Mark set ${recipes.size + 1}",
                onDismiss = { showSaveRecipeDialog = false },
                onSave = { trimmed -> if (trimmed.isNotEmpty()) saveCurrentMarksAsRecipe(trimmed) },
            )

            BatchRecipeDialog(
                visible = showBatchRecipeDialog,
                recipes = recipes,
                onDismiss = { showBatchRecipeDialog = false },
                onSelect = { recipe ->
                    batchRecipe = recipe
                    showBatchRecipeDialog = false
                    batchPhotoPicker.launch(
                        PickVisualMediaRequest.Builder()
                            .setMaxItems(10)
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            .build(),
                    )
                },
            )
        }
    }
        }
        MarkedRoute.Landing -> MarkedLandingScreen(
            media = mediaItems,
            selection = selectedMediaIds,
            onToggleSelect = { id ->
                val next = selectedMediaIds.toMutableSet()
                if (next.contains(id)) next.remove(id) else next.add(id)
                selectedMediaIds = next
            },
            onAddMark = { media ->
                loadMediaIntoEditor(media)
                route = MarkedRoute.MarkEditor
            },
            onBack = { exitCtaVisible = true },
            onOpenSettings = { route = MarkedRoute.Settings },
            onOpenAssets = { route = MarkedRoute.Assets },
            onOpenRecipes = { route = MarkedRoute.Recipes },
            onPickMedia = {
                landingMediaPicker.launch(
                    PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        .build(),
                )
            },
        )

        MarkedRoute.Settings -> MarkedSettingsScreen(
            settings = settings,
            onSave = { updated ->
                settings = updated
                MarkedSettings.save(context, updated)
            },
            onBack = { route = MarkedRoute.Landing },
        )

        MarkedRoute.Recipes -> MarkedRecipesScreen(
            recipes = recipes,
            onApplyRecipe = { recipe -> applyRecipe(recipe) },
            onBack = { route = MarkedRoute.Landing },
        )

        MarkedRoute.Assets -> MarkedAssetsScreen(
            media = mediaItems,
            selection = selectedMediaIds,
            onToggleSelect = { id ->
                val next = selectedMediaIds.toMutableSet()
                if (next.contains(id)) next.remove(id) else next.add(id)
                selectedMediaIds = next
            },
            onAddMark = { media ->
                loadMediaIntoEditor(media)
                route = MarkedRoute.MarkEditor
            },
            onImportAsset = { assetPicker.launch(
                PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    .build(),
            ) },
            onBack = { route = MarkedRoute.Landing },
        )
    }

    if (route == MarkedRoute.Landing) {
        MarkedExitCta(
            visible = exitCtaVisible,
            onVisibleChange = { exitCtaVisible = it },
            onExit = onExit,
        )
    }
}

@Composable
fun AddMarkMenu(
    modifier: Modifier = Modifier,
    recipes: List<MarkedRecipe> = emptyList(),
    onAddText: () -> Unit,
    onAddTimestamp: () -> Unit,
    onAddSignature: () -> Unit,
    onAddSticker: (String) -> Unit,
    onAddAsset: () -> Unit,
    onAddSvg: (BuiltinSvgMark) -> Unit,
    onSaveMarks: () -> Unit,
    onApplyRecipe: (MarkedRecipe) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(56.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                )
                .clickable { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(text = "Text") },
                onClick = { expanded = false; onAddText() },
            )
            DropdownMenuItem(
                text = { Text(text = "Timestamp") },
                onClick = { expanded = false; onAddTimestamp() },
            )
            DropdownMenuItem(
                text = { Text(text = "Signature") },
                onClick = { expanded = false; onAddSignature() },
            )
            DropdownMenuItem(
                text = { Text(text = "Sticker") },
                onClick = { expanded = false; onAddSticker(DEFAULT_STICKER_EMOJI) },
            )
            DropdownMenuItem(
                text = { Text(text = "Image") },
                onClick = { expanded = false; onAddAsset() },
            )
            BUILTIN_SVG_MARKS.forEach { mark ->
                DropdownMenuItem(
                    text = { Text(text = "SVG: ${mark.name}") },
                    onClick = { expanded = false; onAddSvg(mark) },
                )
            }
            DropdownMenuItem(
                text = { Text(text = "Save marks…") },
                onClick = { expanded = false; onSaveMarks() },
            )
            recipes.forEach { recipe ->
                DropdownMenuItem(
                    text = { Text(text = "Apply: ${recipe.name}") },
                    onClick = { expanded = false; onApplyRecipe(recipe) },
                )
            }
        }
    }
}

@Composable
fun SaveRecipeDialog(
    visible: Boolean,
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    if (visible) {
        Dialog(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(text = "Save marks as recipe", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Recipe name") },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = "Cancel")
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) onSave(name.trim())
                            else onDismiss()
                        },
                    ) {
                        Text(text = "Save")
                    }
                }
            }
        }
    }
}

@Composable
fun BatchRecipeDialog(
    visible: Boolean,
    recipes: List<MarkedRecipe>,
    onDismiss: () -> Unit,
    onSelect: (MarkedRecipe) -> Unit,
) {
    if (visible) {
        Dialog(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(text = "Apply marks to batch", style = MaterialTheme.typography.titleMedium)
                if (recipes.isEmpty()) {
                    Text(text = "No saved recipes yet. Save marks first, then batch-apply them.")
                }
                recipes.forEach { recipe ->
                    TextButton(
                        onClick = { onSelect(recipe) },
                    ) {
                        Text(text = "Apply: ${recipe.name}")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(text = "Cancel")
                }
            }
        }
    }
}

@Composable
fun MarkLayerControls(
    layer: Layer,
    sourceWidth: Float,
    sourceHeight: Float,
    onLayerChange: (Layer) -> Unit,
) {
    when (layer) {
        is Layer.TextLayer -> TextMarkControls(
            layer = layer,
            onLayerChange = onLayerChange,
            onTextChange = { onLayerChange(layer.copy(text = it)) },
            onFontSizeChange = { onLayerChange(layer.copy(fontSize = it)) },
            onFontFamilyChange = { onLayerChange(layer.copy(fontFamily = it)) },
            onColorChange = { onLayerChange(layer.copy(color = it)) },
            onBoldChange = { onLayerChange(layer.copy(bold = it)) },
            onTrackingChange = { onLayerChange(layer.copy(tracking = it)) },
            onOpacityChange = { onLayerChange(layer.copy(style = layer.style.copy(opacity = it))) },
            onShadowChange = { enabled ->
                onLayerChange(
                    layer.copy(
                        style = layer.style.copy(
                            shadow = if (enabled) layer.style.shadow ?: Shadow(color = 0xFF000000.toInt()) else null,
                        ),
                    ),
                )
            },
            onOutlineChange = { onLayerChange(layer.copy(style = layer.style.copy(outline = it))) },
            onOutlineWidthChange = { onLayerChange(layer.copy(style = layer.style.copy(outlineWidth = it))) },
            onPillChange = { onLayerChange(layer.copy(style = layer.style.copy(pill = it))) },
        )

        is Layer.TimestampLayer -> TimestampMarkControls(
            layer = layer,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            onLayerChange = onLayerChange,
        )

        is Layer.SignatureLayer -> SignatureMarkControls(
            layer = layer,
            onLayerChange = onLayerChange,
        )

        is Layer.StickerLayer -> StickerMarkControls(
            layer = layer,
            onLayerChange = onLayerChange,
        )

        is Layer.AssetLayer -> AssetMarkControls(
            layer = layer,
            onLayerChange = onLayerChange,
        )

        is Layer.SvgLayer -> SvgMarkControls(
            layer = layer,
            onLayerChange = onLayerChange,
        )
    }
}

/**
 * Basic editing grade controls (contrast / exposure / saturation) that live on the layer style,
 * so authored grades travel with the mark into recipes and batch re-application.
 */
@Composable
fun GradeSliderRow(
    layer: Layer,
    onLayerChange: (Layer) -> Unit,
) {
    val style = layer.style
    Text(text = "Grade", style = MaterialTheme.typography.labelLarge)
    Slider(
        value = style.contrast,
        onValueChange = { value -> onLayerChange(layer.withStyle(style.copy(contrast = value))) },
        valueRange = GRADE_CONTRAST_MIN..GRADE_CONTRAST_MAX,
    )
    Slider(
        value = style.exposure,
        onValueChange = { value -> onLayerChange(layer.withStyle(style.copy(exposure = value))) },
        valueRange = GRADE_EXPOSURE_MIN..GRADE_EXPOSURE_MAX,
    )
    Slider(
        value = style.saturation,
        onValueChange = { value -> onLayerChange(layer.withStyle(style.copy(saturation = value))) },
        valueRange = GRADE_SATURATION_MIN..GRADE_SATURATION_MAX,
    )
}

@Composable
private fun TextMarkControls(
    layer: Layer.TextLayer,
    onLayerChange: (Layer) -> Unit,
    onTextChange: (String) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onFontFamilyChange: (String) -> Unit,
    onColorChange: (Int) -> Unit,
    onBoldChange: (Boolean) -> Unit,
    onTrackingChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onShadowChange: (Boolean) -> Unit,
    onOutlineChange: (Boolean) -> Unit,
    onOutlineWidthChange: (Float) -> Unit,
    onPillChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 88.dp, start = 16.dp, end = 16.dp),
    ) {
        Text(text = "Mark style", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = layer.text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Text") },
        )
        Slider(
            value = layer.fontSize,
            onValueChange = onFontSizeChange,
            valueRange = 12f..200f,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            listOf("Sans" to "sans", "Serif" to "serif", "Mono" to "monospace").forEach { (label, fontName) ->
                Button(
                    onClick = { onFontFamilyChange(fontName) },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(text = label)
                }
            }
        }
        Text(text = "Color", style = MaterialTheme.typography.labelLarge)
        Row(modifier = Modifier.fillMaxWidth()) {
            MARK_TEXT_SWATCH_COLORS.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .padding(end = 8.dp)
                        .background(color = Color(color), shape = CircleShape)
                        .clickable { onColorChange(color) },
                )
            }
        }
        Slider(
            value = layer.tracking,
            onValueChange = onTrackingChange,
            valueRange = 0f..30f,
        )
        Slider(
            value = layer.style.opacity,
            onValueChange = { onLayerChange(layer.copy(style = layer.style.copy(opacity = it))) },
            valueRange = 0f..1f,
        )
        GradeSliderRow(layer = layer, onLayerChange = onLayerChange)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            Switch(
                checked = layer.bold,
                onCheckedChange = onBoldChange,
            )
            Switch(
                checked = layer.style.outline,
                onCheckedChange = onOutlineChange,
            )
            Switch(
                checked = layer.style.pill,
                onCheckedChange = onPillChange,
            )
            Switch(
                checked = layer.style.shadow != null,
                onCheckedChange = onShadowChange,
            )
        }
        if (layer.style.outline) {
            Slider(
                value = layer.style.outlineWidth,
                onValueChange = onOutlineWidthChange,
                valueRange = 2f..20f,
            )
        }
    }
}

@Composable
fun TimestampMarkControls(
    layer: Layer.TimestampLayer,
    sourceWidth: Float,
    sourceHeight: Float,
    onLayerChange: (Layer) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 88.dp, start = 16.dp, end = 16.dp),
    ) {
        Text(text = "Timestamp", style = MaterialTheme.typography.titleSmall)
        Text(text = "Format", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            TimestampFormat.values().forEach { format ->
                Button(
                    onClick = {
                        updateTimestampLayer(
                            layer = layer,
                            format = format,
                            position = layer.position,
                            sourceWidth = sourceWidth,
                            sourceHeight = sourceHeight,
                            scale = layer.transform.scale,
                            onChange = onLayerChange,
                        )
                    },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(text = format.label)
                }
            }
        }
        Text(text = "Position", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            TimestampPosition.values().forEach { position ->
                Button(
                    onClick = {
                        updateTimestampLayer(
                            layer = layer,
                            format = layer.format,
                            position = position,
                            sourceWidth = sourceWidth,
                            sourceHeight = sourceHeight,
                            scale = layer.transform.scale,
                            onChange = onLayerChange,
                        )
                    },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(text = position.label)
                }
            }
        }
        GradeSliderRow(layer = layer, onLayerChange = onLayerChange)
    }
}

private fun updateTimestampLayer(
    layer: Layer.TimestampLayer,
    format: TimestampFormat,
    position: TimestampPosition,
    sourceWidth: Float,
    sourceHeight: Float,
    scale: Float,
    onChange: (Layer) -> Unit,
) {
    val text = format.format(LocalTime.now())
    val (textWidth, textHeight) = measureTextDimensions(text, layer.fontSize)
    onChange(
        layer.copy(
            format = format,
            position = position,
            transform = timestampAnchor(
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                position = position,
                textWidth = textWidth,
                textHeight = textHeight,
                scale = scale,
            ),
        ),
    )
}

@Composable
fun SignatureMarkControls(
    layer: Layer.SignatureLayer,
    onLayerChange: (Layer) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 88.dp, start = 16.dp, end = 16.dp),
    ) {
        Text(text = "Signature", style = MaterialTheme.typography.titleSmall)
        Text(text = "Color", style = MaterialTheme.typography.labelLarge)
        Row(modifier = Modifier.fillMaxWidth()) {
            MARK_TEXT_SWATCH_COLORS.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .padding(end = 8.dp)
                        .background(color = Color(color), shape = CircleShape)
                        .clickable { onLayerChange(layer.copy(color = color)) },
                )
            }
        }
        Slider(
            value = layer.width,
            onValueChange = { onLayerChange(layer.copy(width = it)) },
            valueRange = 1f..40f,
        )
        Slider(
            value = layer.style.opacity,
            onValueChange = { onLayerChange(layer.copy(style = layer.style.copy(opacity = it))) },
            valueRange = 0f..1f,
        )
        GradeSliderRow(layer = layer, onLayerChange = onLayerChange)
    }
}

@Composable
fun StickerMarkControls(
    layer: Layer.StickerLayer,
    onLayerChange: (Layer) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 88.dp, start = 16.dp, end = 16.dp),
    ) {
        Text(text = "Sticker", style = MaterialTheme.typography.titleSmall)
        Text(text = layer.emoji, style = MaterialTheme.typography.headlineMedium)
        Row(modifier = Modifier.fillMaxWidth()) {
            STICKER_EMOJI_CHOICES.forEach { emoji ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(end = 8.dp)
                        .clickable { onLayerChange(layer.copy(emoji = emoji)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = emoji, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        Slider(
            value = layer.fontSize,
            onValueChange = { onLayerChange(layer.copy(fontSize = it)) },
            valueRange = 24f..200f,
        )
        Slider(
            value = layer.style.opacity,
            onValueChange = { onLayerChange(layer.copy(style = layer.style.copy(opacity = it))) },
            valueRange = 0f..1f,
        )
        GradeSliderRow(layer = layer, onLayerChange = onLayerChange)
    }
}

@Composable
fun AssetMarkControls(
    layer: Layer.AssetLayer,
    onLayerChange: (Layer) -> Unit,
) {
    val style = layer.style
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 88.dp, start = 16.dp, end = 16.dp),
    ) {
        Text(text = "Image mark", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = style.opacity,
            onValueChange = { onLayerChange(layer.copy(style = layer.style.copy(opacity = it))) },
            valueRange = 0f..1f,
        )
        GradeSliderRow(layer = layer, onLayerChange = onLayerChange)
        Switch(
            checked = style.outline,
            onCheckedChange = { onLayerChange(layer.copy(style = layer.style.copy(outline = it))) },
        )
        if (style.outline) {
            Slider(
                value = style.outlineWidth,
                onValueChange = { onLayerChange(layer.copy(style = layer.style.copy(outlineWidth = it))) },
                valueRange = 2f..20f,
            )
        }
        Switch(
            checked = style.pill,
            onCheckedChange = { onLayerChange(layer.copy(style = layer.style.copy(pill = it))) },
        )
        Switch(
            checked = style.shadow != null,
            onCheckedChange = { enabled ->
                onLayerChange(
                    layer.copy(
                        style = layer.style.copy(
                            shadow = if (enabled) layer.style.shadow ?: Shadow(color = 0xFF000000.toInt()) else null,
                        ),
                    ),
                )
            },
        )
    }
}

@Composable
fun SvgMarkControls(
    layer: Layer.SvgLayer,
    onLayerChange: (Layer) -> Unit,
) {
    val style = layer.style
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 88.dp, start = 16.dp, end = 16.dp),
    ) {
        Text(text = "SVG mark", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = style.opacity,
            onValueChange = { onLayerChange(layer.copy(style = layer.style.copy(opacity = it))) },
            valueRange = 0f..1f,
        )
        GradeSliderRow(layer = layer, onLayerChange = onLayerChange)
        Switch(
            checked = style.outline,
            onCheckedChange = { onLayerChange(layer.copy(style = layer.style.copy(outline = it))) },
        )
        if (style.outline) {
            Slider(
                value = style.outlineWidth,
                onValueChange = { onLayerChange(layer.copy(style = layer.style.copy(outlineWidth = it))) },
                valueRange = 2f..20f,
            )
        }
        Switch(
            checked = style.pill,
            onCheckedChange = { onLayerChange(layer.copy(style = layer.style.copy(pill = it))) },
        )
        Switch(
            checked = style.shadow != null,
            onCheckedChange = { enabled ->
                onLayerChange(
                    layer.copy(
                        style = layer.style.copy(
                            shadow = if (enabled) layer.style.shadow ?: Shadow(color = 0xFF000000.toInt()) else null,
                        ),
                    ),
                )
            },
        )
    }
}

@Composable
fun LayerTray(
    layers: List<Layer>,
    selectedLayerId: String?,
    canUndo: Boolean,
    onSelect: (String) -> Unit,
    onMove: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (layers.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Layers",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onUndo, enabled = canUndo) {
                Text(text = "Undo")
            }
        }
        layers.forEach { layer ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = layerLabel(layer),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(layer.id) },
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { onMove(layer.id, true) }) {
                    Text(text = "Up")
                }
                TextButton(onClick = { onMove(layer.id, false) }) {
                    Text(text = "Down")
                }
                TextButton(onClick = { onDelete(layer.id) }) {
                    Text(text = "Delete")
                }
            }
        }
    }
}

private fun layerLabel(layer: Layer): String = when (layer) {
    is Layer.TextLayer -> "Text: ${layer.text.take(24)}"
    is Layer.TimestampLayer -> "Time ${layer.format.label}"
    is Layer.SignatureLayer -> "Signature"
    is Layer.StickerLayer -> "Sticker ${layer.emoji}"
    is Layer.AssetLayer -> "Image"
    is Layer.SvgLayer -> "SVG"
}
