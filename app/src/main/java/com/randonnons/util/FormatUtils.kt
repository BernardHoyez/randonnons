package com.randonnons.util

import android.util.Xml
import com.randonnons.model.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlSerializer
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

// ── Formats de date GPX / KML ─────────────────────────────────────────────────
private val ISO8601 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

// =============================================================================
//  EXPORTATION GPX
// =============================================================================
object GpxExporter {

    fun exporter(trace: TraceAvecPoints, output: OutputStream) {
        val ser: XmlSerializer = Xml.newSerializer()
        ser.setOutput(output, "UTF-8")
        ser.startDocument("UTF-8", true)
        ser.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)

        ser.startTag("", "gpx")
        ser.attribute("", "version", "1.1")
        ser.attribute("", "creator", "Randonnons")
        ser.attribute("", "xmlns", "http://www.topografix.com/GPX/1/1")
        ser.attribute("", "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance")

        // Métadonnées
        ser.startTag("", "metadata")
        ser.tag("name", trace.trace.nom)
        ser.tag("desc", trace.trace.description)
        ser.tag("time", ISO8601.format(Date(trace.trace.dateDebut)))
        ser.endTag("", "metadata")

        // Waypoints
        trace.waypoints.forEach { wp ->
            ser.startTag("", "wpt")
            ser.attribute("", "lat", wp.latitude.toString())
            ser.attribute("", "lon", wp.longitude.toString())
            ser.tag("ele", wp.altitude.toString())
            ser.tag("time", ISO8601.format(Date(wp.timestamp)))
            ser.tag("name", wp.nom)
            ser.tag("desc", wp.description)
            ser.tag("sym", wp.symbole)
            ser.endTag("", "wpt")
        }

        // Trace (trkseg)
        ser.startTag("", "trk")
        ser.tag("name", trace.trace.nom)
        ser.startTag("", "trkseg")
        trace.points.forEach { pt ->
            ser.startTag("", "trkpt")
            ser.attribute("", "lat", pt.latitude.toString())
            ser.attribute("", "lon", pt.longitude.toString())
            ser.tag("ele", pt.altitude.toString())
            ser.tag("time", ISO8601.format(Date(pt.timestamp)))
            if (pt.vitesse > 0f) {
                ser.startTag("", "extensions")
                ser.tag("speed", pt.vitesse.toString())
                ser.endTag("", "extensions")
            }
            ser.endTag("", "trkpt")
        }
        ser.endTag("", "trkseg")
        ser.endTag("", "trk")
        ser.endTag("", "gpx")
        ser.endDocument()
    }

    fun exporterRoute(route: RouteAvecPoints, output: OutputStream) {
        val ser: XmlSerializer = Xml.newSerializer()
        ser.setOutput(output, "UTF-8")
        ser.startDocument("UTF-8", true)
        ser.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)

        ser.startTag("", "gpx")
        ser.attribute("", "version", "1.1")
        ser.attribute("", "creator", "Randonnons")
        ser.attribute("", "xmlns", "http://www.topografix.com/GPX/1/1")

        // Route GPX (rte)
        ser.startTag("", "rte")
        ser.tag("name", route.route.nom)
        ser.tag("desc", route.route.description)
        route.points.sortedBy { it.ordre }.forEach { pt ->
            ser.startTag("", "rtept")
            ser.attribute("", "lat", pt.latitude.toString())
            ser.attribute("", "lon", pt.longitude.toString())
            ser.tag("ele", pt.altitude.toString())
            ser.endTag("", "rtept")
        }
        ser.endTag("", "rte")
        ser.endTag("", "gpx")
        ser.endDocument()
    }

    private fun XmlSerializer.tag(name: String, value: String) {
        startTag("", name)
        text(value)
        endTag("", name)
    }
}

// =============================================================================
//  IMPORTATION GPX
// =============================================================================
object GpxImporter {

    data class GpxData(
        val nom: String,
        val description: String,
        val points: List<PointTrace>,
        val waypoints: List<Waypoint>,
        val routePoints: List<PointRoute>
    )

    fun importer(input: InputStream): GpxData {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(input, "UTF-8")

        val points = mutableListOf<PointTrace>()
        val waypoints = mutableListOf<Waypoint>()
        val routePoints = mutableListOf<PointRoute>()
        var nom = "Randonnée importée"
        var desc = ""
        var enTrkpt = false; var enWpt = false; var enRtept = false
        var curLat = 0.0; var curLon = 0.0; var curAlt = 0.0
        var curTime = 0L; var curNom = ""; var curDesc = ""; var curSym = "Flag"
        var rtepOrdre = 0

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "trkpt" -> {
                        enTrkpt = true
                        curLat = parser.getAttributeValue("", "lat").toDoubleOrNull() ?: 0.0
                        curLon = parser.getAttributeValue("", "lon").toDoubleOrNull() ?: 0.0
                    }
                    "wpt" -> {
                        enWpt = true
                        curLat = parser.getAttributeValue("", "lat").toDoubleOrNull() ?: 0.0
                        curLon = parser.getAttributeValue("", "lon").toDoubleOrNull() ?: 0.0
                        curNom = ""; curDesc = ""; curSym = "Flag"; curAlt = 0.0; curTime = 0
                    }
                    "rtept" -> {
                        enRtept = true
                        curLat = parser.getAttributeValue("", "lat").toDoubleOrNull() ?: 0.0
                        curLon = parser.getAttributeValue("", "lon").toDoubleOrNull() ?: 0.0
                    }
                    "name" -> if (!enTrkpt && !enWpt && !enRtept) {
                        nom = parser.nextText().trim()
                    } else if (enWpt) {
                        curNom = parser.nextText().trim()
                    }
                    "desc" -> if (!enTrkpt && !enWpt && !enRtept) {
                        desc = parser.nextText().trim()
                    } else if (enWpt) {
                        curDesc = parser.nextText().trim()
                    }
                    "ele" -> curAlt = parser.nextText().toDoubleOrNull() ?: 0.0
                    "time" -> curTime = ISO8601.parse(parser.nextText().trim())?.time ?: 0L
                    "sym" -> curSym = parser.nextText().trim()
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "trkpt" -> {
                        points.add(PointTrace(0, 0, curLat, curLon, curAlt, curTime, 0f, 0f))
                        enTrkpt = false
                    }
                    "wpt" -> {
                        waypoints.add(Waypoint(0, null, curNom.ifEmpty { "Point" },
                            curDesc, curLat, curLon, curAlt, curTime, curSym))
                        enWpt = false
                    }
                    "rtept" -> {
                        routePoints.add(PointRoute(0, 0, rtepOrdre++, curLat, curLon, curAlt))
                        enRtept = false
                    }
                }
            }
            event = parser.next()
        }
        return GpxData(nom, desc, points, waypoints, routePoints)
    }
}

// =============================================================================
//  EXPORTATION KML
// =============================================================================
object KmlExporter {

    fun exporter(trace: TraceAvecPoints, output: OutputStream) {
        val ser: XmlSerializer = Xml.newSerializer()
        ser.setOutput(output, "UTF-8")
        ser.startDocument("UTF-8", true)
        ser.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)

        ser.startTag("", "kml")
        ser.attribute("", "xmlns", "http://www.opengis.net/kml/2.2")
        ser.startTag("", "Document")
        ser.tag("name", trace.trace.nom)
        ser.tag("description", trace.trace.description)

        // Style trace
        ser.startTag("", "Style"); ser.attribute("", "id", "lineStyle")
        ser.startTag("", "LineStyle")
        ser.tag("color", "ff0000ff")   // rouge ABGR
        ser.tag("width", "3")
        ser.endTag("", "LineStyle"); ser.endTag("", "Style")

        // Placemark trace
        ser.startTag("", "Placemark")
        ser.tag("name", trace.trace.nom)
        ser.tag("styleUrl", "#lineStyle")
        ser.startTag("", "LineString")
        ser.tag("tessellate", "1")
        ser.tag("altitudeMode", "clampToGround")
        val coords = trace.points.joinToString(" ") {
            "${it.longitude},${it.latitude},${it.altitude}"
        }
        ser.tag("coordinates", coords)
        ser.endTag("", "LineString"); ser.endTag("", "Placemark")

        // Waypoints
        trace.waypoints.forEach { wp ->
            ser.startTag("", "Placemark")
            ser.tag("name", wp.nom)
            ser.tag("description", wp.description)
            ser.startTag("", "Point")
            ser.tag("coordinates", "${wp.longitude},${wp.latitude},${wp.altitude}")
            ser.endTag("", "Point"); ser.endTag("", "Placemark")
        }

        ser.endTag("", "Document"); ser.endTag("", "kml")
        ser.endDocument()
    }

    private fun XmlSerializer.tag(name: String, value: String) {
        startTag("", name); text(value); endTag("", name)
    }
}

// =============================================================================
//  IMPORTATION KML
// =============================================================================
object KmlImporter {

    fun importer(input: InputStream): GpxImporter.GpxData {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(input, "UTF-8")

        val points = mutableListOf<PointTrace>()
        val waypoints = mutableListOf<Waypoint>()
        var nom = "Import KML"; var desc = ""
        var enPlacemark = false; var enLineString = false; var enPoint = false
        var curNom = ""; var curDesc = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "Document" -> { /* root */ }
                    "Placemark" -> { enPlacemark = true; curNom = ""; curDesc = "" }
                    "LineString" -> enLineString = true
                    "Point" -> enPoint = true
                    "name" -> {
                        val txt = parser.nextText().trim()
                        if (enPlacemark) curNom = txt else nom = txt
                    }
                    "description" -> {
                        val txt = parser.nextText().trim()
                        if (enPlacemark) curDesc = txt else desc = txt
                    }
                    "coordinates" -> {
                        val raw = parser.nextText().trim()
                        val parsed = raw.split(Regex("\\s+")).mapNotNull { coord ->
                            val parts = coord.split(",")
                            if (parts.size >= 2) Triple(
                                parts[0].toDoubleOrNull() ?: return@mapNotNull null,
                                parts[1].toDoubleOrNull() ?: return@mapNotNull null,
                                parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0
                            ) else null
                        }
                        if (enLineString) {
                            parsed.forEach { (lon, lat, alt) ->
                                points.add(PointTrace(0,0,lat,lon,alt,0,0f,0f))
                            }
                        } else if (enPoint && parsed.isNotEmpty()) {
                            val (lon, lat, alt) = parsed.first()
                            waypoints.add(Waypoint(0, null,
                                curNom.ifEmpty { "Point" }, curDesc, lat, lon, alt))
                        }
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "Placemark"  -> { enPlacemark = false; enLineString = false; enPoint = false }
                    "LineString" -> enLineString = false
                    "Point"      -> enPoint = false
                }
            }
            event = parser.next()
        }
        return GpxImporter.GpxData(nom, desc, points, waypoints, emptyList())
    }
}
