package com.example.vpn

/**
 * VMess configuration dedicated for Irancell and Rightel networks.
 * Decoded from user-provided VMess URI:
 * vmess://eyJhZGQiOiJpay5tYWhkaXMtbmV0LmlyIiwiYWlkIjowLCJob3N0IjoiaWsubWFoZGlzLW5ldC5pciIsImlkIjoiMzExOTYxM2QtY2UxYS00NjcxLWIyNTItYTBlODAwN2FkMzgwIiwibmV0Ijoid3MiLCJwYXRoIjoiL3dzLXNwZWVkIiwicG9ydCI6MzE4MzksInBzIjoiY3h4Y3ggLSB1c2VyX3hrc2w4NzYiLCJ0bHMiOiIiLCJ0eXBlIjoibm9uZSIsInYiOiIyIn0=
 */
data class VmessConfig(
    val serverHost: String = "ik.mahdis-net.ir",
    val serverPort: Int = 31839,
    val uuid: String = "3119613d-ce1a-4671-b252-a0e8007ad380",
    val wsHost: String = "ik.mahdis-net.ir",
    val wsPath: String = "/ws-speed",
    val alterId: Int = 0,
    val security: String = "auto",
    val remark: String = "cxxcx - user_xksl876"
)

object VmessDefaultConfig {
    val INSTANCE = VmessConfig()
}
