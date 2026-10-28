package com.bmdstudios.marked.editor

/**
 * A vector mark source offered as a built-in in the Add mark menu (Phase 3 SVG renderer).
 *
 * @property name Short display name for the mark (shown in the add-mark menu).
 * @property svgSource Raw SVG markup rendered through the AndroidSVG (svg-android) renderer.
 * @property width Intrinsic SVG width in user units, used to center the mark on the media.
 * @property height Intrinsic SVG height in user units, used to center the mark on the media.
 */
data class BuiltinSvgMark(
    val name: String,
    val svgSource: String,
    val width: Int = DEFAULT_SVG_SIZE,
    val height: Int = DEFAULT_SVG_SIZE,
)

/** Default intrinsic size (user units) of the built-in vector marks. */
const val DEFAULT_SVG_SIZE = 100

/** Vector marks offered by the editor's Add mark menu until richer SVG sources exist. */
val BUILTIN_SVG_MARKS: List<BuiltinSvgMark> = listOf(
    BuiltinSvgMark(
        name = "Star",
        svgSource = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\"><polygon points=\"50,5 60.58,35.44 92.8,36.09 67.12,55.56 76.45,86.41 50,68 23.55,86.41 32.88,55.56 7.2,36.09 39.42,35.44\" fill=\"#FFCC33\"/></svg>",
    ),
    BuiltinSvgMark(
        name = "Ring",
        svgSource = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\"><circle cx=\"50\" cy=\"50\" r=\"42\" fill=\"none\" stroke=\"#FFCC33\" stroke-width=\"10\"/></svg>",
    ),
)
