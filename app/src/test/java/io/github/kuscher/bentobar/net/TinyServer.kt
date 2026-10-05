package io.github.kuscher.bentobar.net

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPOutputStream

/**
 * A web server on this machine, just enough of one for [HttpTransportTest]: it reads a request's
 * first line and headers, hands them to [answer], and writes back what that returns. One request per
 * connection, in clear text, on a port the system picks.
 */
class TinyServer(private val answer: (Seen) -> Answer) : AutoCloseable {
    /** What a request looked like when it arrived. Header names are in lower case. */
    class Seen(val path: String, val headers: Map<String, String>)

    class Answer(val status: Int = 200, val body: ByteArray = ByteArray(0), val headers: Map<String, String> = mapOf("Content-Type" to "application/json"),
                 val delayMs: Long = 0,
                 /** Above 0: the body is sent a tenth at a time, with this long a pause before each part. */
                 val tricklesMs: Long = 0) {
        constructor(text: String) : this(body = text.toByteArray(Charsets.UTF_8))
    }

    /** Every request that arrived, in order. */
    val seen = CopyOnWriteArrayList<Seen>()

    private val socket = ServerSocket(0, 16, InetAddress.getLoopbackAddress())
    val port: Int get() = socket.localPort

    private val thread = Thread {
        while (!socket.isClosed) {
            val client = try { socket.accept() } catch (e: Exception) { break }
            try { serve(client) } catch (e: Exception) { /* the client hung up: the test decides what that means */ } finally { runCatching { client.close() } }
        }
    }.apply { isDaemon = true; start() }

    private fun serve(client: Socket) {
        val input = client.getInputStream()
        val head = ByteArrayOutputStream()
        // Up to the empty line that ends the headers.
        while (true) {
            val b = input.read()
            if (b < 0) return
            head.write(b)
            val bytes = head.toByteArray()
            val n = bytes.size
            if (n >= 4 && bytes[n - 4] == '\r'.code.toByte() && bytes[n - 3] == '\n'.code.toByte() && bytes[n - 2] == '\r'.code.toByte() && bytes[n - 1] == '\n'.code.toByte()) break
        }
        val lines = head.toString(Charsets.ISO_8859_1.name()).split("\r\n").filter { it.isNotEmpty() }
        val request = Seen(lines.first().split(' ')[1], lines.drop(1).associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() })
        seen += request
        val reply = answer(request)
        if (reply.delayMs > 0) Thread.sleep(reply.delayMs)
        val out = client.getOutputStream()
        val headers = reply.headers + mapOf("Content-Length" to reply.body.size.toString(), "Connection" to "close")
        out.write(("HTTP/1.1 ${reply.status} X\r\n" + headers.entries.joinToString("") { "${it.key}: ${it.value}\r\n" } + "\r\n").toByteArray(Charsets.ISO_8859_1))
        if (reply.tricklesMs <= 0) out.write(reply.body)
        else {
            out.flush()
            val part = (reply.body.size / 10).coerceAtLeast(1)
            var at = 0
            while (at < reply.body.size) {
                Thread.sleep(reply.tricklesMs)
                val n = minOf(part, reply.body.size - at)
                out.write(reply.body, at, n); out.flush()
                at += n
            }
        }
        out.flush()
    }

    /**
     * A transport that sends what would go to one of BentoBar's hosts to this server instead: the
     * address is the real one, with its beginning (`https://host`) swapped for this machine's.
     */
    fun transport(readMs: Int = 2_000, maxBytes: Int = Http.MAX_BYTES, totalMs: Int = Http.TOTAL_MS) = HttpTransport(
        open = { address -> URL(address.replaceFirst(Regex("^https://[^/]+"), "http://127.0.0.1:$port")).openConnection() as HttpURLConnection },
        connectMs = 2_000, readMs = readMs, maxBytes = maxBytes, totalMs = totalMs,
    )

    override fun close() { runCatching { socket.close() } }

    companion object {
        fun gzip(text: String): ByteArray = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) } }.toByteArray()
    }
}
