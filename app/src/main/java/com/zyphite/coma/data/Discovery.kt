package com.zyphite.coma.data

import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL

/** A machine on this network that has Coma pairing started. [port] is where it takes the pairing code. */
data class Found(val name: String, val ip: String, val port: Int)

/** What a host hands over for the right pairing code. */
data class Pairing(val url: String, val key: String, val name: String)

/**
 * Finds hosts on the local network. The coma plugin listens for a UDP broadcast and answers with the
 * machine's name; the address and key are only given out over [pair], for the six-digit code
 * the host shows. Both calls block; run them off the main thread.
 */
object Discovery {
    private const val PORT = 48642
    private val SEARCH = "COMA?".toByteArray()

    /** Broadcasts a search and collects the answers for [waitMs]. [also] are extra addresses to ask directly. */
    fun search(also: List<String> = emptyList(), waitMs: Int = 1800): List<Found> {
        val found = linkedMapOf<String, Found>()
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 300
                val ask = {
                    (listOf("255.255.255.255") + also).forEach { target ->
                        runCatching { socket.send(DatagramPacket(SEARCH, SEARCH.size, InetAddress.getByName(target), PORT)) }
                    }
                }
                val buffer = ByteArray(512)
                val deadline = System.currentTimeMillis() + waitMs
                var asked = 0L
                while (System.currentTimeMillis() < deadline) {
                    // A lost datagram is common on Wi-Fi, so ask more than once.
                    if (System.currentTimeMillis() - asked > 600) { ask(); asked = System.currentTimeMillis() }
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (quiet: SocketTimeoutException) {
                        continue
                    }
                    val answer = runCatching { JSONObject(String(packet.data, 0, packet.length)) }.getOrNull() ?: continue
                    val ip = packet.address.hostAddress ?: continue
                    if (answer.optInt("coma") == 1 && answer.optInt("port") > 0) {
                        found[ip] = Found(answer.optString("name").ifBlank { ip }, ip, answer.optInt("port"))
                    }
                }
            }
        } catch (unreachable: IOException) {
            // No network to search; the list stays empty.
        }
        return found.values.toList()
    }

    /** Trades the code the host shows for its address and key. */
    fun pair(host: Found, code: String): Pairing {
        val connection = try {
            (URL("http://${host.ip}:${host.port}/pair").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5_000
                readTimeout = 5_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(JSONObject().put("code", code).toString().toByteArray()) }
            }
        } catch (failure: IOException) {
            throw HermesException(0, "${host.name} stopped answering. Is pairing still running on it?")
        }
        try {
            if (connection.responseCode == 403) throw HermesException(403, "That isn't the code ${host.name} is showing.")
            val answer = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            return Pairing(answer.getString("url"), answer.getString("key"), answer.optString("name").ifBlank { host.name })
        } catch (failure: HermesException) {
            throw failure
        } catch (failure: Exception) {
            throw HermesException(0, "${host.name} stopped answering. Is pairing still running on it?")
        } finally {
            connection.disconnect()
        }
    }
}
