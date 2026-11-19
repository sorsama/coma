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

/** What a host offers for pairing: its relay, its id there, and a token that admits one phone. */
data class Offer(val relay: String, val host: String, val token: String, val name: String)

/**
 * Finds hosts on the local network. The coma plugin listens for a UDP broadcast and answers with the
 * machine's name; its pairing offer is only given out over [pair], for the six-digit code the host shows. Both calls block; run them off the main thread.
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

    /** Trades the code the host shows for its pairing offer. */
    fun pair(host: Found, code: String): Offer {
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
            return Offer(
                answer.getString("relay"), answer.getString("host"), answer.getString("token"),
                answer.optString("name").ifBlank { host.name },
            )
        } catch (failure: HermesException) {
            throw failure
        } catch (failure: Exception) {
            throw HermesException(0, "${host.name} stopped answering. Is pairing still running on it?")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Takes this phone's place on [offer]'s relay and returns its device id there. [purchaseToken] is
     * the phone's Plus subscription, if it has one, which lifts the host's limit on phones.
     */
    fun join(offer: Offer, deviceName: String, purchaseToken: String?): String {
        val body = JSONObject().put("token", offer.token).put("name", deviceName).put("public_key", DeviceKey.public())
        if (purchaseToken != null) body.put("purchase_token", purchaseToken)
        val relay = runCatching { URL(offer.relay).host }.getOrNull() ?: offer.relay
        val connection = try {
            (URL("${offer.relay}/v1/hosts/${offer.host}/devices").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(body.toString().toByteArray()) }
            }
        } catch (failure: IOException) {
            throw HermesException(0, "Can't reach the relay at $relay.")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val answer = runCatching { JSONObject(stream.bufferedReader().use { it.readText() }) }.getOrNull() ?: JSONObject()
            if (status !in 200..299) {
                throw HermesException(
                    status, answer.optJSONObject("error")?.optString("message").orEmpty().ifBlank { "The relay refused this phone ($status)." },
                )
            }
            return answer.getString("device_id")
        } catch (failure: HermesException) {
            throw failure
        } catch (failure: Exception) {
            throw HermesException(0, "Can't reach the relay at $relay.")
        } finally {
            connection.disconnect()
        }
    }
}
