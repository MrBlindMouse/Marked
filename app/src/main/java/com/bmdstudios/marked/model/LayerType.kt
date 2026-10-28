package com.bmdstudios.marked.model

import java.time.LocalTime

/**
 * Kind of mark this layer places onto the media.
 *
 * Flat model contract (see DESIGN.md): a layer always carries a [LayerStyle] and a
 * [LayerTransform] plus the type-specific payload it needs to render.
 */
enum class LayerType {
    /** PNG asset placed over the media. */
    ASSET,

    /** Vector SVG placed over the media (rendered via the AndroidSVG renderer). */
    SVG,

    /** Text mark. */
    TEXT,

    /** Freehand signature stroke. */
    SIGNATURE,

    /** Timestamp preset. */
    TIMESTAMP,

    /** Sticker / emoji. */
    STICKER,
}

/** Corner placement preset for timestamp marks (DESIGN.md: "format + position preset"). */
enum class TimestampPosition(val label: String) {
    TOP_LEFT("Top left"),
    TOP_RIGHT("Top right"),
    BOTTOM_LEFT("Bottom left"),
    BOTTOM_RIGHT("Bottom right"),
}

/** Fixed timestamp presets supported by the editor. */
enum class TimestampFormat(val label: String) {
    HH_MM_SS("HH:MM:SS"),
    HH_MM("HH:MM"),
    MM_SS("MM:SS");

    /** Formats a [LocalTime] into this preset's display string. */
    fun format(time: LocalTime): String = when (this) {
        HH_MM_SS -> "%02d:%02d:%02d".format(time.hour, time.minute, time.second)
        HH_MM -> "%02d:%02d".format(time.hour, time.minute)
        MM_SS -> "%02d:%02d".format(time.minute, time.second)
    };
}
