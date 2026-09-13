package com.example.util

/**
 * Utility to strictly remove, mask, and sanitize any server IP, domain, host,
 * or port information from VPN logs before storage and before display.
 */
object LogSanitizer {

    // Regex for IPv4 addresses with optional port (e.g. 185.123.45.67:2280)
    private val IP_WITH_PORT_REGEX = Regex("""\b(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?::[0-9]{1,5})?\b""")

    // Regex for common domain patterns (e.g. ssh.mahdis-net.ir, server1.vpn.com:443)
    private val DOMAIN_WITH_PORT_REGEX = Regex(
        """\b(?:[a-zA-Z0-9](?:[a-zA-Z0-9\-]{0,61}[a-zA-Z0-9])?\.)+(?:ir|com|net|org|io|me|xyz|info|co|de|uk|us|online|site|space|top|cloud|vip)(?::[0-9]{1,5})?\b""",
        RegexOption.IGNORE_CASE
    )

    // Regex to match "سرور: <something>" or "server: <something>"
    private val SERVER_LABEL_REGEX = Regex(
        """(?i)(?:سرور هدف|سرور|sshHost|host|server)\s*[:=]\s*[^\s|,]+""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Sanitizes a log message so that no server address, host IP, or server domain is exposed.
     */
    fun sanitize(message: String?): String {
        if (message.isNullOrBlank()) return ""

        var result = message

        // 1. Remove explicit server label assignments (e.g. "سرور هدف: 1.2.3.4:22")
        result = result.replace(SERVER_LABEL_REGEX, "سرور: [شبکه اختصاصی GMB]")

        // 2. Specific known server hosts/domains
        result = result.replace("ssh.mahdis-net.ir", "[سرور امن GMB]")
        result = result.replace("ip.connection-net.ir", "[سرور امن GMB]")
        result = result.replace("mahdis-net.ir", "[سرور امن GMB]")

        // 3. Mask any domain names (except official public brand URL if needed, but safe to mask in logs)
        result = result.replace(DOMAIN_WITH_PORT_REGEX) { match ->
            val matchVal = match.value
            // Allow general github api/raw references if needed, but mask any server domains
            if (matchVal.contains("github.com", ignoreCase = true)) {
                matchVal
            } else {
                "[سرور امن GMB]"
            }
        }

        // 4. Mask external IP addresses
        result = result.replace(IP_WITH_PORT_REGEX) { match ->
            val ip = match.value
            // Allow local VPN TUN virtual interface 10.0.0.2 to be recognizable as internal
            if (ip.startsWith("10.0.0.2")) {
                "10.0.0.2 (رابط محلی)"
            } else {
                "[سرور امن]"
            }
        }

        // 5. Clean up any duplicated placeholders
        result = result.replace("[سرور امن GMB]:[سرور امن]", "[سرور امن GMB]")
        result = result.replace("[سرور امن]:[0-9]+".toRegex(), "[سرور امن]")

        return result
    }
}
