package com.syedmsidsm.abdulsscanner

import com.syedmsidsm.abdulsscanner.data.db.entity.QrValueType
import com.syedmsidsm.abdulsscanner.domain.qr.ParsedQr
import com.syedmsidsm.abdulsscanner.domain.qr.QrParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrParserTest {

    @Test
    fun parseWifi() {
        val raw = "WIFI:T:WPA;S:HomeNetwork;P:SuperSecret123;H:false;;"
        val parsed = QrParser.parse(raw)
        assertTrue(parsed is ParsedQr.Wifi)
        val wifi = parsed as ParsedQr.Wifi
        assertEquals("HomeNetwork", wifi.ssid)
        assertEquals("SuperSecret123", wifi.password)
        assertEquals("WPA", wifi.encryptionType)
        assertEquals(false, wifi.isHidden)
        assertEquals(QrValueType.WIFI, wifi.valueType)
    }

    @Test
    fun parseUrl() {
        val raw = "https://example.com/document?id=42"
        val parsed = QrParser.parse(raw)
        assertTrue(parsed is ParsedQr.Url)
        val url = parsed as ParsedQr.Url
        assertEquals("https://example.com/document?id=42", url.url)
        assertEquals(QrValueType.URL, url.valueType)
    }

    @Test
    fun parsePhone() {
        val raw = "tel:+966501234567"
        val parsed = QrParser.parse(raw)
        assertTrue(parsed is ParsedQr.Phone)
        val phone = parsed as ParsedQr.Phone
        assertEquals("+966501234567", phone.number)
        assertEquals(QrValueType.PHONE, phone.valueType)
    }

    @Test
    fun parseEmail() {
        val raw = "mailto:scanner@example.com?subject=Contract&body=Please+sign"
        val parsed = QrParser.parse(raw)
        assertTrue(parsed is ParsedQr.Email)
        val email = parsed as ParsedQr.Email
        assertEquals("scanner@example.com", email.address)
        assertEquals("Contract", email.subject)
        assertEquals("Please sign", email.body)
        assertEquals(QrValueType.EMAIL, email.valueType)
    }

    @Test
    fun parseSms() {
        val raw = "smsto:+123456789:Check this scan"
        val parsed = QrParser.parse(raw)
        assertTrue(parsed is ParsedQr.Sms)
        val sms = parsed as ParsedQr.Sms
        assertEquals("+123456789", sms.number)
        assertEquals("Check this scan", sms.message)
        assertEquals(QrValueType.SMS, sms.valueType)
    }

    @Test
    fun parseGeo() {
        val raw = "geo:24.7136,46.6753?q=Riyadh"
        val parsed = QrParser.parse(raw)
        assertTrue(parsed is ParsedQr.Geo)
        val geo = parsed as ParsedQr.Geo
        assertEquals(24.7136, geo.latitude, 0.0001)
        assertEquals(46.6753, geo.longitude, 0.0001)
        assertEquals("Riyadh", geo.query)
        assertEquals(QrValueType.GEO, geo.valueType)
    }

    @Test
    fun parseVCard() {
        val raw = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Abdul Syed
            TEL;TYPE=CELL:+1234567890
            EMAIL:abdul@example.com
            ORG:Scanner Corp
            END:VCARD
        """.trimIndent()
        val parsed = QrParser.parse(raw)
        assertTrue(parsed is ParsedQr.Contact)
        val contact = parsed as ParsedQr.Contact
        assertEquals("Abdul Syed", contact.name)
        assertEquals("+1234567890", contact.phone)
        assertEquals("abdul@example.com", contact.email)
        assertEquals("Scanner Corp", contact.org)
        assertEquals(QrValueType.CONTACT, contact.valueType)
    }

    @Test
    fun parsePlainText() {
        val raw = "Just a regular invoice description note."
        val parsed = QrParser.parse(raw)
        assertTrue(parsed is ParsedQr.Text)
        val text = parsed as ParsedQr.Text
        assertEquals(raw, text.text)
        assertEquals(QrValueType.TEXT, text.valueType)
    }
}
