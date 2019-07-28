package com.eyeem.watchadoin

import java.io.File
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Convert Timelines report to a simple SVG
 *
 * @param timelines output of [Stopwatch.report]
 */
class SvgReport(val timelines: List<Timeline>, timeaxisPlaceholder: Boolean = false) {

    val padding = 10
    private val timelineHeight = 30
    private val fontSize = 12
    private val smallFontSize = 8
    val xAxisHeight = 8
    val scaleGridDistance = 50

    private val maxPageWidth = 1200
    private val renderedSvg : String

    private val svgWidth : Long
    val svgHeight : Long
    var xScale : Float
        private set
    val totalDurationMs : Long

    val svgWidthNormalized
        get() = svgWidth * xScale

    init {
        xScale = 1.0f
        totalDurationMs = timelines.map { it.duration + it.relativeStart }.maxBy { it }
            ?: throw IllegalStateException("no maximum found")
        val scaleSteps = totalDurationMs / scaleGridDistance
        svgWidth = totalDurationMs + padding * 2
        if (svgWidth > (maxPageWidth - padding * 2)) {
            xScale = maxPageWidth.toFloat() / totalDurationMs.toFloat()
        }

        var timelinesSvg = "" // timelines as svg tags

        // collects timelines as we draw them, int here is a raw in which we draw timeline
        val rects = HashMap<Int, ArrayList<Rect>>()
        timelines.forEachIndexed { index, timeline ->

            val heightIndex = rects.firstAvailableRow(timeline)

            val _y1 = (heightIndex + 1) * padding + heightIndex * timelineHeight
            val _y1Text = (_y1 + (timelineHeight - fontSize)).toLong()
            val _x1 = padding + (timeline.relativeStart * xScale).toLong()
            val _rectWidth = (timeline.duration * xScale).toLong()
            val _rectHeight = timelineHeight.toLong()

            val rect = Rect(
                x1 = _x1,
                y1 = _y1.toLong(),
                y1Text = _y1Text,
                x2 = _x1 + _rectWidth,
                y2 = _y1 + _rectHeight,
                timeline = timeline,
                fillColor = if (!timeline.timeout) {
                    "76,175,80"
                } else {
                    "244,67,54"
                },
                alpha = 0.25f + max(0f, 0.75f - 0.2f * timeline.nestLvl),
                fontSize = fontSize,
                smallFontSize = smallFontSize,
                padding = padding,
                clipIndex = index,
                rowIndex = heightIndex
            )

            val rowTimelines = rects[heightIndex] ?: ArrayList<Rect>().apply {
                rects[heightIndex] = this
            }
            rowTimelines += rect

            timelinesSvg += rect.asSvgTimelineTag()
        }

        val rowCount = rects.values.size
        svgHeight = ((rowCount + 1) * padding + rowCount * timelineHeight + xAxisHeight + padding).toLong()

        var output = """<svg id="stopwatch" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $svgWidthNormalized $svgHeight" width="$svgWidthNormalized" height="$svgHeight">"""

        // draw the timeline grid and axis
        if (timeaxisPlaceholder) {
            output += """<g id="timeaxis"></g>"""
        } else {
            val omit = (1f / xScale).roundToLong()

            for (scaleStep in 0..scaleSteps) {

                if (scaleStep % omit != 0L) {
                    continue
                }

                val x = (scaleGridDistance * scaleStep * xScale).toLong() + padding
                val y1 = padding
                val yLength = (rowCount - 1) * padding + rowCount * timelineHeight


                output += """<g>
                            <line stroke-dasharray="5, 5" x1="$x" y1="$y1" x2="$x" y2="${y1 + yLength}" style="stroke-width:1;stroke:rgba(0,0,0,0.5)"/>
                            <text x="$x" y="${y1 + yLength + padding}" font-family="Verdana" font-size="$xAxisHeight" fill="#00000077">${scaleStep * scaleGridDistance}ms</text>
                         </g>""".trimIndent()
            }
        }

        output += timelinesSvg

        output += "</svg>"
        renderedSvg = output
    }


    /**
     * @return SVG formatted string with the report in it
     */
    override fun toString(): String = renderedSvg
}

fun Stopwatch.asSvgReport(timeaxisPlaceholder: Boolean = false): SvgReport = SvgReport(timelines(includeParent = true), timeaxisPlaceholder)

fun Stopwatch.saveAsSvg(file: File, dryRun: Boolean = false) {
    val svgOutput = asSvgReport().toString()
    if (dryRun) {
        println(svgOutput)
    } else {
        file.printWriter().use { out -> out.println(svgOutput) }
    }
}

fun Stopwatch.saveAsHtml(file: File, dryRun: Boolean = false) {
    val svgReport = asSvgReport(timeaxisPlaceholder = true)
    val htmlOutput = htmlTemplate(report = svgReport)
    if (dryRun) {
        println(svgReport)
    } else {
        file.printWriter().use { out -> out.println(htmlOutput) }
    }
}

private data class Rect(
    val x1: Long, val y1: Long,
    val y1Text: Long,
    val x2: Long, val y2: Long,
    val timeline: Timeline,
    val fillColor: String,
    val alpha: Float,
    val padding: Int,
    val clipIndex: Int,
    val rowIndex: Int,
    val fontSize: Int,
    val smallFontSize: Int
)

private val Rect.width
    get() = x2 - x1

private val Rect.height
    get() = y2 - y1

private fun Rect.asSvgTimelineTag() =
    """<g>
         <rect x="$x1" y="$y1" width="$width" height="$height" style="fill:rgba($fillColor,$alpha);"></rect>
         <rect x="${x2 - 1}" y="$y1" width="2" height="$height" style="fill:rgba(0,0,0,1);"></rect>
         <text x="${x1 + padding}" y="$y1Text" font-family="Verdana" font-size="$fontSize" fill="#000000" clip-path="url(#clip$clipIndex)">${timeline.name.escapeXml()}</text>
         <text x="${x1 + padding}" y="${y1Text+fontSize * 0.8}" font-family="Verdana" font-size="$smallFontSize" fill="#000000" clip-path="url(#clip$clipIndex)">tid=${timeline.tid}</text>
         <clipPath id="clip$clipIndex">
           <rect x="$x1" y="$y1" width="$width" height="$height" class="clipRect"/>
         </clipPath>
       </g>""".trimIndent()


private fun Long.between(lower: Long, upper: Long): Boolean = this > lower && this < upper

private infix fun Timeline.collidesWith(other: Timeline): Boolean {
    val start = this.relativeStart
    val end = this.relativeStart + this.duration
    val otherStart = other.relativeStart
    val otherEnd = other.relativeStart + other.duration

    if (start == otherStart || end == otherEnd) return true

    return start.between(otherStart, otherEnd) || end.between(otherStart, otherEnd) || otherStart.between(
        start,
        end
    ) || otherEnd.between(start, end)
}

private fun HashMap<Int, ArrayList<Rect>>.findParentRect(timeline: Timeline): Rect? {
    var parent = timeline.parent ?: return null
    val n = keys.maxBy { it } ?: 0
    for (i in 0..n) {
        val timelines = this[i] ?: return null
        timelines.forEach {
            if (it.timeline == parent) {
                return it
            }
        }
    }

    return null
}

private fun List<Rect>?.isColliding(timeline: Timeline) : Boolean {
    if (this == null)
        return false

    forEach {
        if (it.timeline collidesWith timeline) {
            return true
        }
    }
    return false
}

private fun HashMap<Int, ArrayList<Rect>>.firstAvailableRow(timeline: Timeline): Int {
    val parentRect = findParentRect(timeline)
    var currentRow = parentRect?.rowIndex?.let { it + 1 } ?: 0

    while (true) {
        if (!this[currentRow].isColliding(timeline)) {
            return currentRow
        }
        currentRow++
    }
}

internal val xmlEscapeMap = mapOf(
    '"' to "&quot;",
    '\'' to "&apos;",
    '<' to "&lt;",
    '>' to "&gt;",
    '&' to "&amp;"
)
internal fun String.escapeXml() : String {
    val sb = StringBuilder()

    forEach { char ->
        sb.append(xmlEscapeMap[char] ?: char)
    }

    return sb.toString()
}

private fun htmlTemplate(report: SvgReport) = """
<!DOCTYPE html>
<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8">

  <title>Watcha Doin?</title>

  <style i type="text/css">
    svg * {
      -webkit-user-select: none; /* Safari 3.1+ */
        -moz-user-select: none; /* Firefox 2+ */
        -ms-user-select: none; /* IE 10+ */
        user-select: none; /* Standard syntax */
    }

    svg text {
      fill: #000;
      font-family: "Verdana";
    }

  </style>

  <script src="https://code.easypz.io/easypz.latest.min.js"></script>

</head>
<body>

$report

<script src="https://cdnjs.cloudflare.com/ajax/libs/svg.js/2.7.1/svg.min.js"></script>
<script type="text/javascript">
    var svg = document.getElementById('stopwatch')
    var stopwatch = SVG.get('stopwatch')
    var timeaxis = SVG.adopt(svg.getElementById('timeaxis'))
    var groups = Array.from(svg.getElementsByTagName('g'))
    var width = ${report.svgWidthNormalized}
    var height = ${report.svgHeight}
    var padding = ${report.padding}
    var xScale = ${report.xScale}
    var totalDurationMs = ${report.totalDurationMs}
    var xAxisHeight = ${report.xAxisHeight}

    var lastScale
    function drawTimeAxis(scale) {
      if (lastScale === scale) {
      	return
      }
      lastScale = scale
      timeaxis.clear()
      var scaleGridDistance = 50
      var omitNotRounded = 1.0 / xScale / scale
      var omit = Math.round(omitNotRounded)

      // when omit is under 0.5 we must set it to 1 but decrease scale grid distance
      if (omitNotRounded < 0.5) {
      	omit = 1.0
      	if (omitNotRounded < 0.25) {
      		scaleGridDistance = 10
      	} else {
      		scaleGridDistance = 25
      	}
      }

      var scaleSteps = totalDurationMs / scaleGridDistance

      var scaleStep;
      for (scaleStep = 0; scaleStep < scaleSteps; scaleStep++) { 
        if (scaleStep % omit != 0) {
        	continue
        }

        var x = ((scaleGridDistance * scaleStep * xScale) + padding) * scale
        var y1 = padding
        var y2 = height - padding - xAxisHeight
        timeaxis.line(x, y1, x, y2).stroke({ width: 1, color: '#0000007f', dasharray: '5, 5'})

        var timeMs = scaleStep * scaleGridDistance
        timeaxis.text(timeMs + "ms").attr({"font-family": "Verdana", "font-size": xAxisHeight, "x": x, "y": y2 - padding})
      }
    }

    drawTimeAxis(1.0)

    var maxScale = Math.max(1.0, Math.round((totalDurationMs/width)*12))

    new EasyPZ(svg, function(transform) {

      drawTimeAxis(transform.scale)

      // Use transform.scale, transform.translateX, transform.translateY to update your visualization
      stopwatch.viewbox(-transform.translateX, 0, width, height)
      groups.forEach(function(group) {
      	if(group.getAttribute('id') === "timeaxis") {
      		return
      	}
        group.setAttribute("transform", "scale(" + transform.scale + " 1)");

        var rects = Array.from(group.getElementsByTagName('rect'))

        var firstRect = rects[0]
        if (firstRect === undefined) {
          return;
        }

        var x = firstRect.getAttribute('x') * 1
        var blockWidth = firstRect.getAttribute('width') * 1

        var markingRect = rects[1]
        if (markingRect === undefined) {
          return;
        }

        markingRect.setAttribute("transform", "scale(" + 1/transform.scale +" 1)");
        markingRect.setAttribute("x", (x + blockWidth) * transform.scale - 1);

        var clipRect = Array.from(group.getElementsByClassName('clipRect'))[0]
        clipRect.setAttribute("transform", "scale(" + transform.scale +" 1)");

        var texts = Array.from(group.getElementsByTagName('text'))
        texts.forEach(function(text) {
            text.setAttribute("transform", "scale(" + 1/transform.scale +" 1)");
            text.setAttribute("x", x * transform.scale + 10);
        });
      })
    },
    { minScale: 0.5, maxScale: maxScale, bounds: { top: 0, right: 0, bottom: 0, left: 0 } }, ["FLICK_PAN", "WHEEL_ZOOM", "PINCH_ZOOM", "DBLCLICK_ZOOM_IN", "DBLRIGHTCLICK_ZOOM_OUT"]);

</script>

</body></html>
""".trimIndent()