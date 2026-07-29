package com.cashewteam.novatext.android

/** Classifies decoded QR payloads without depending on Android UI classes. */
internal object QrPayloadRouter {
    sealed interface Payload {
        data class PlainText(val text: String) : Payload
        data class HttpUrl(val url: String) : Payload
        data class AppLink(val uri: String) : Payload
        data class WeChatPaymentCode(val value: String) : Payload
        data class Wifi(val ssid: String, val password: String) : Payload
    }

    fun distinctNonBlank(values: Iterable<String>): List<String> =
        values.map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()

    fun classify(rawValue: String): Payload {
        val value = rawValue.trim()
        if (value.startsWith(WIFI_PREFIX, ignoreCase = true)) {
            return parseWifi(value) ?: Payload.PlainText(rawValue)
        }
        when {
            value.startsWith(WECHAT_PAYMENT_PREFIX, ignoreCase = true) -> return Payload.WeChatPaymentCode(value)
            value.startsWith("http://", ignoreCase = true) ||
                value.startsWith("https://", ignoreCase = true) -> return Payload.HttpUrl(value)
            URI_SCHEME.matches(value) -> return Payload.AppLink(value)
            else -> return Payload.PlainText(rawValue)
        }
    }

    private fun parseWifi(value: String): Payload.Wifi? {
        if (!value.startsWith(WIFI_PREFIX, ignoreCase = true)) return null
        val fields = linkedMapOf<String, String>()
        splitEscaped(value.substring(WIFI_PREFIX.length), ';').forEach { field ->
            val separator = field.indexOf(':')
            if (separator > 0) {
                fields.putIfAbsent(field.substring(0, separator), field.substring(separator + 1))
            }
        }
        val ssid = fields["S"]?.takeIf(String::isNotEmpty) ?: return null
        return Payload.Wifi(ssid = ssid, password = fields["P"].orEmpty())
    }

    private fun splitEscaped(value: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        value.forEach { character ->
            when {
                escaped -> {
                    current.append(character)
                    escaped = false
                }
                character == '\\' -> escaped = true
                character == delimiter -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(character)
            }
        }
        if (escaped) current.append('\\')
        result += current.toString()
        return result
    }

    private const val WIFI_PREFIX = "WIFI:"
    private const val WECHAT_PAYMENT_PREFIX = "wxp://"
    private val URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:.*")
}
