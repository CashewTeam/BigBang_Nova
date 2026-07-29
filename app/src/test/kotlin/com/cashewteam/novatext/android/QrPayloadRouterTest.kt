package com.cashewteam.novatext.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadRouterTest {
    @Test
    fun classifiesOrdinaryTextWithoutChangingIt() {
        assertEquals(
            QrPayloadRouter.Payload.PlainText("大爆炸 文本"),
            QrPayloadRouter.classify("大爆炸 文本"),
        )
        assertEquals(
            QrPayloadRouter.Payload.PlainText("6901234567892"),
            QrPayloadRouter.classify("6901234567892"),
        )
    }

    @Test
    fun classifiesHttpAndHttpsAsBrowserUrls() {
        assertEquals(
            QrPayloadRouter.Payload.HttpUrl("https://example.com/path"),
            QrPayloadRouter.classify(" https://example.com/path "),
        )
        assertEquals(
            QrPayloadRouter.Payload.HttpUrl("HTTP://example.com"),
            QrPayloadRouter.classify("HTTP://example.com"),
        )
    }

    @Test
    fun classifiesNonHttpSchemesAsAppLinks() {
        assertEquals(
            QrPayloadRouter.Payload.AppLink("weixin://dl/business/"),
            QrPayloadRouter.classify("weixin://dl/business/"),
        )
    }

    @Test
    fun classifiesWechatPaymentCodeBeforeGenericAppLinks() {
        assertEquals(
            QrPayloadRouter.Payload.WeChatPaymentCode("wxp://f2f0-payment-code"),
            QrPayloadRouter.classify("wxp://f2f0-payment-code"),
        )
    }

    @Test
    fun parsesWifiPayloadWithEscapedSeparators() {
        assertEquals(
            QrPayloadRouter.Payload.Wifi("Cafe;Net", "pa;ss\\word"),
            QrPayloadRouter.classify("WIFI:T:WPA;S:Cafe\\;Net;P:pa\\;ss\\\\word;;"),
        )
    }

    @Test
    fun parsesPasswordlessWifiPayload() {
        assertEquals(
            QrPayloadRouter.Payload.Wifi("Guest", ""),
            QrPayloadRouter.classify("WIFI:T:nopass;S:Guest;;"),
        )
    }

    @Test
    fun malformedWifiPayloadFallsBackToText() {
        assertEquals(
            QrPayloadRouter.Payload.PlainText("WIFI:T:WPA;P:missing-ssid;;"),
            QrPayloadRouter.classify("WIFI:T:WPA;P:missing-ssid;;"),
        )
    }

    @Test
    fun removesBlankAndDuplicateQrResultsInOrder() {
        val results = QrPayloadRouter.distinctNonBlank(listOf(" first ", "", "first", "second", "  "))

        assertEquals(listOf("first", "second"), results)
        assertTrue(results.all(String::isNotBlank))
    }
}
