package com.syedmsidsm.abdulsscanner.domain.qr

import com.syedmsidsm.abdulsscanner.data.db.entity.QrValueType
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

sealed interface ParsedQr {
    val rawValue: String
    val valueType: QrValueType

    data class Url(
        override val rawValue: String,
        val url: String,
        val title: String? = null
    ) : ParsedQr {
        override val valueType = QrValueType.URL
    }

    data class Wifi(
        override val rawValue: String,
        val ssid: String,
        val password: String,
        val encryptionType: String = "WPA",
        val isHidden: Boolean = false
    ) : ParsedQr {
        override val valueType = QrValueType.WIFI
    }

    data class Phone(
        override val rawValue: String,
        val number: String
    ) : ParsedQr {
        override val valueType = QrValueType.PHONE
    }

    data class Email(
        override val rawValue: String,
        val address: String,
        val subject: String? = null,
        val body: String? = null
    ) : ParsedQr {
        override val valueType = QrValueType.EMAIL
    }

    data class Sms(
        override val rawValue: String,
        val number: String,
        val message: String? = null
    ) : ParsedQr {
        override val valueType = QrValueType.SMS
    }

    data class Geo(
        override val rawValue: String,
        val latitude: Double,
        val longitude: Double,
        val query: String? = null
    ) : ParsedQr {
        override val valueType = QrValueType.GEO
    }

    data class Contact(
        override val rawValue: String,
        val name: String,
        val phone: String? = null,
        val email: String? = null,
        val org: String? = null
    ) : ParsedQr {
        override val valueType = QrValueType.CONTACT
    }

    data class Text(
        override val rawValue: String,
        val text: String
    ) : ParsedQr {
        override val valueType = QrValueType.TEXT
    }
}

object QrParser {

    private fun decode(s: String): String = try {
        URLDecoder.decode(s, StandardCharsets.UTF_8.name())
    } catch (_: Exception) {
        s
    }

    fun parse(raw: String): ParsedQr {
        val trimmed = raw.trim()

        // 1. Wi-Fi: WIFI:S:MyNetwork;T:WPA;P:secret;;
        if (trimmed.startsWith("WIFI:", ignoreCase = true)) {
            val content = trimmed.substring(5)
            val parts = content.split(';')
            var ssid = ""
            var password = ""
            var type = "WPA"
            var hidden = false

            for (part in parts) {
                when {
                    part.startsWith("S:", ignoreCase = true) -> ssid = part.substring(2)
                    part.startsWith("P:", ignoreCase = true) -> password = part.substring(2)
                    part.startsWith("T:", ignoreCase = true) -> type = part.substring(2).uppercase()
                    part.startsWith("H:", ignoreCase = true) -> hidden = part.substring(2).equals("true", ignoreCase = true)
                }
            }
            return ParsedQr.Wifi(
                rawValue = trimmed,
                ssid = ssid,
                password = password,
                encryptionType = if (type.isBlank()) "WPA" else type,
                isHidden = hidden
            )
        }

        // 2. URL
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("www.", ignoreCase = true)
        ) {
            val url = if (trimmed.startsWith("www.", ignoreCase = true)) "https://$trimmed" else trimmed
            return ParsedQr.Url(rawValue = trimmed, url = url)
        }

        // 3. Tel: tel:+1234567890
        if (trimmed.startsWith("tel:", ignoreCase = true)) {
            val number = trimmed.substring(4).trim()
            return ParsedQr.Phone(rawValue = trimmed, number = number)
        }

        // 4. Email: mailto:test@example.com?subject=Hi&body=Hello
        if (trimmed.startsWith("mailto:", ignoreCase = true)) {
            val withoutScheme = trimmed.substring(7)
            val questionIdx = withoutScheme.indexOf('?')
            val address = if (questionIdx >= 0) withoutScheme.substring(0, questionIdx) else withoutScheme
            var subject: String? = null
            var body: String? = null

            if (questionIdx >= 0) {
                val query = withoutScheme.substring(questionIdx + 1)
                val params = query.split('&')
                for (p in params) {
                    val eq = p.indexOf('=')
                    if (eq >= 0) {
                        val k = p.substring(0, eq).lowercase()
                        val v = decode(p.substring(eq + 1))
                        if (k == "subject") subject = v
                        if (k == "body") body = v
                    }
                }
            }
            return ParsedQr.Email(rawValue = trimmed, address = address, subject = subject, body = body)
        }

        // 5. SMS: smsto:123456:message or sms:123456?body=message
        if (trimmed.startsWith("smsto:", ignoreCase = true)) {
            val withoutScheme = trimmed.substring(6)
            val colon = withoutScheme.indexOf(':')
            val number = if (colon >= 0) withoutScheme.substring(0, colon) else withoutScheme
            val message = if (colon >= 0) withoutScheme.substring(colon + 1) else null
            return ParsedQr.Sms(rawValue = trimmed, number = number, message = message)
        }
        if (trimmed.startsWith("sms:", ignoreCase = true)) {
            val withoutScheme = trimmed.substring(4)
            val question = withoutScheme.indexOf('?')
            val number = if (question >= 0) withoutScheme.substring(0, question) else withoutScheme
            var body: String? = null
            if (question >= 0) {
                val query = withoutScheme.substring(question + 1)
                val params = query.split('&')
                for (p in params) {
                    val eq = p.indexOf('=')
                    if (eq >= 0 && p.substring(0, eq).equals("body", ignoreCase = true)) {
                        body = decode(p.substring(eq + 1))
                    }
                }
            }
            return ParsedQr.Sms(rawValue = trimmed, number = number, message = body)
        }

        // 6. Geo: geo:37.7749,-122.4194 or geo:0,0?q=London
        if (trimmed.startsWith("geo:", ignoreCase = true)) {
            val content = trimmed.substring(4)
            val queryIdx = content.indexOf('?')
            val coordsPart = if (queryIdx >= 0) content.substring(0, queryIdx) else content
            val query = if (queryIdx >= 0) decode(content.substring(queryIdx + 1).removePrefix("q=")) else null
            val latLng = coordsPart.split(',')
            val lat = latLng.getOrNull(0)?.toDoubleOrNull() ?: 0.0
            val lng = latLng.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            return ParsedQr.Geo(rawValue = trimmed, latitude = lat, longitude = lng, query = query)
        }

        // 7. Contact: BEGIN:VCARD ... END:VCARD or MECARD:N:Smith,John;TEL:123;EMAIL:a@b.com;;
        if (trimmed.startsWith("BEGIN:VCARD", ignoreCase = true)) {
            var name = ""
            var phone: String? = null
            var email: String? = null
            var org: String? = null

            val lines = trimmed.lines()
            for (line in lines) {
                val upper = line.uppercase()
                when {
                    upper.startsWith("FN:") -> name = line.substring(3).trim()
                    name.isEmpty() && upper.startsWith("N:") -> name = line.substring(2).replace(';', ' ').trim()
                    upper.startsWith("TEL") -> {
                        val colon = line.indexOf(':')
                        if (colon >= 0) phone = line.substring(colon + 1).trim()
                    }
                    upper.startsWith("EMAIL") -> {
                        val colon = line.indexOf(':')
                        if (colon >= 0) email = line.substring(colon + 1).trim()
                    }
                    upper.startsWith("ORG:") -> org = line.substring(4).trim()
                }
            }
            return ParsedQr.Contact(
                rawValue = trimmed,
                name = name.ifBlank { "Contact" },
                phone = phone,
                email = email,
                org = org
            )
        }
        if (trimmed.startsWith("MECARD:", ignoreCase = true)) {
            var name = ""
            var phone: String? = null
            var email: String? = null
            var org: String? = null

            val fields = trimmed.substring(7).split(';')
            for (f in fields) {
                when {
                    f.startsWith("N:", ignoreCase = true) -> name = f.substring(2).replace(',', ' ').trim()
                    f.startsWith("TEL:", ignoreCase = true) -> phone = f.substring(4).trim()
                    f.startsWith("EMAIL:", ignoreCase = true) -> email = f.substring(6).trim()
                    f.startsWith("ORG:", ignoreCase = true) -> org = f.substring(4).trim()
                }
            }
            return ParsedQr.Contact(
                rawValue = trimmed,
                name = name.ifBlank { "Contact" },
                phone = phone,
                email = email,
                org = org
            )
        }

        // Fallback: Text
        return ParsedQr.Text(rawValue = trimmed, text = trimmed)
    }
}
