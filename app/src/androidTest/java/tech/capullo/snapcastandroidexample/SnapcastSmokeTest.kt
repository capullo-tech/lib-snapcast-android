package tech.capullo.snapcastandroidexample

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class SnapcastSmokeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
    private val spawnedProcesses = CopyOnWriteArrayList<Process>()

    @After
    fun tearDown() {
        spawnedProcesses.forEach { process ->
            if (process.isRunning()) {
                process.destroy()
                if (!process.waitForSeconds(2)) {
                    process.forceKill()
                    process.waitForSeconds(2)
                }
            }
        }
        spawnedProcesses.clear()
    }

    @Test
    fun nativeBinariesArePackagedAndExecutable() {
        val expectedBinaries = listOf(
            "libsnapclient.so",
            "libsnapserver.so",
            "libsnapcontrol.so",
        )
        for (name in expectedBinaries) {
            val file = File(nativeLibDir, name)
            assertTrue("Missing native binary: ${file.absolutePath}", file.exists())
            assertTrue("Native binary is not executable: ${file.absolutePath}", file.canExecute())
        }
    }

    @Test
    fun snapclientAndSnapserverReportVersion() {
        val clientResult = runToCompletion(
            File(nativeLibDir, "libsnapclient.so").absolutePath,
            "--version",
        )
        assertEquals(
            "libsnapclient.so --version failed:\n${clientResult.output}",
            0,
            clientResult.exitCode,
        )
        assertTrue(
            "Unexpected snapclient --version output:\n${clientResult.output}",
            clientResult.output.contains("snapclient v"),
        )

        val serverResult = runToCompletion(
            File(nativeLibDir, "libsnapserver.so").absolutePath,
            "--version",
        )
        assertEquals(
            "libsnapserver.so --version failed:\n${serverResult.output}",
            0,
            serverResult.exitCode,
        )
        assertTrue(
            "Unexpected snapserver --version output:\n${serverResult.output}",
            serverResult.output.contains("snapserver v"),
        )
    }

    @Test
    fun snapcontrolBridgesAbstractUnixSocket() {
        val socketName = "snapcast-smoke-${UUID.randomUUID().toString().take(8)}"
        val executor = Executors.newSingleThreadExecutor()
        // LocalServerSocket only implements Closeable from API 28, so close it explicitly.
        val serverSocket = LocalServerSocket(socketName)
        try {
            val serverFuture = executor.submit<String> {
                serverSocket.accept().use { client ->
                    val reader = BufferedReader(InputStreamReader(client.inputStream))
                    val writer = BufferedWriter(OutputStreamWriter(client.outputStream))
                    val received = reader.readLine()
                    writer.write("""{"jsonrpc":"2.0","id":1,"result":"pong"}""")
                    writer.newLine()
                    writer.flush()
                    received
                }
            }

            // Keep stderr apart: stdout carries the bridged JSON-RPC, and the pre-O (API < 26)
            // linker writes "unsupported flags DT_FLAGS_1" warnings for PIE binaries to stderr.
            val process = startProcessWithSeparateStderr(
                File(nativeLibDir, "libsnapcontrol.so").absolutePath,
                "--socket-name=$socketName",
            )
            drainAsync(process.errorStream, StringBuilder())

            val procWriter = BufferedWriter(OutputStreamWriter(process.outputStream))
            val procReader = BufferedReader(InputStreamReader(process.inputStream))

            val outbound = """{"jsonrpc":"2.0","id":1,"method":"ping"}"""
            procWriter.write(outbound)
            procWriter.newLine()
            procWriter.flush()

            val reply = procReader.readLine()
            assertEquals("""{"jsonrpc":"2.0","id":1,"result":"pong"}""", reply)

            val receivedByServer = serverFuture.get(5, TimeUnit.SECONDS)
            assertEquals(outbound, receivedByServer)

            procWriter.close()
            assertTrue("libsnapcontrol.so did not exit in time", process.waitForSeconds(5))
            assertEquals(0, process.exitValue())
        } finally {
            serverSocket.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun snapcontrolFailsCleanlyWhenSocketDoesNotExist() {
        val missingSocket = "nonexistent-snapcontrol-${UUID.randomUUID().toString().take(8)}"
        val result = runToCompletion(
            File(nativeLibDir, "libsnapcontrol.so").absolutePath,
            "--socket-name=$missingSocket",
        )
        assertNotEquals("Expected non-zero exit when socket is missing", 0, result.exitCode)
    }

    @Test
    fun snapclientConnectsToSnapserverOverIpv4Loopback() {
        verifyClientServerHandshake(
            bindAddress = "127.0.0.1",
            clientUriHost = "127.0.0.1",
            hostId = "smoke-ipv4-${UUID.randomUUID().toString().take(8)}",
        )
    }

    @Test
    fun snapclientConnectsToSnapserverOverIpv6Loopback() {
        verifyClientServerHandshake(
            bindAddress = "::1",
            clientUriHost = "[::1]",
            hostId = "smoke-ipv6-${UUID.randomUUID().toString().take(8)}",
        )
    }

    @Test
    fun oboePlayerStartsAndSwitchesChannelAtRuntime() {
        val bindAddress = "127.0.0.1"
        val hostId = "smoke-oboe-${UUID.randomUUID().toString().take(8)}"
        val streamPort = findFreePort(bindAddress)
        val controlPort = findFreePort(bindAddress)
        val sourcePort = findFreePort(bindAddress)

        val dataDir = File(context.cacheDir, "snapserver-$hostId").apply {
            deleteRecursively()
            mkdirs()
        }
        val emptyConfig = File(dataDir, "snapserver.conf").apply { writeText("") }

        val serverLogs = StringBuilder()
        val clientLogs = StringBuilder()

        try {
            val serverProcess = startProcess(
                File(nativeLibDir, "libsnapserver.so").absolutePath,
                "--config", emptyConfig.absolutePath,
                "--server.datadir=${dataDir.absolutePath}",
                "--server.mdns_enabled=false",
                "--http.enabled=false",
                "--tcp-control.enabled=true",
                "--tcp-control.bind_to_address=$bindAddress",
                "--tcp-control.port=$controlPort",
                "--tcp-control.publish=false",
                "--tcp-streaming.enabled=true",
                "--tcp-streaming.bind_to_address=$bindAddress",
                "--tcp-streaming.port=$streamPort",
                "--tcp-streaming.publish=false",
                "--stream.source=tcp://$bindAddress:$sourcePort?name=SmokeTest&mode=server",
                "--logging.sink=stdout",
            )
            drainAsync(serverProcess, serverLogs)

            waitUntilControlPortReady(bindAddress, controlPort, serverProcess, serverLogs)

            val clientProcess = startProcess(
                File(nativeLibDir, "libsnapclient.so").absolutePath,
                "--hostID", hostId,
                "--player", "oboe",
                "--channel", "stereo",
                "--logsink", "stdout",
                "tcp://$bindAddress:$streamPort",
            )
            drainAsync(clientProcess, clientLogs)

            // Evaluate each condition before building its message: the message embeds the logs,
            // and Kotlin evaluates arguments left to right.
            val listening = waitForChannelListener(clientProcess, 10)
            assertTrue(
                "OboePlayer did not start channel control listener:\n${snapshot(clientLogs)}",
                listening,
            )
            val initDone = waitForLog(clientLogs, "Init done", clientProcess)
            assertTrue("OboePlayer did not finish init:\n${snapshot(clientLogs)}", initDone)
            val initLogs = snapshot(clientLogs)
            assertTrue(
                "OboePlayer failed to build AudioStream:\n$initLogs",
                !initLogs.contains("Error building AudioStream"),
            )

            val switchedLeft = switchChannel("left", 1, clientLogs, clientProcess)
            assertTrue("OboePlayer did not switch channel to left:\n${snapshot(clientLogs)}", switchedLeft)

            val switchedRight = switchChannel("right", 2, clientLogs, clientProcess)
            assertTrue("OboePlayer did not switch channel to right:\n${snapshot(clientLogs)}", switchedRight)

            val switchedStereo = switchChannel("stereo", 0, clientLogs, clientProcess)
            assertTrue("OboePlayer did not switch channel back to stereo:\n${snapshot(clientLogs)}", switchedStereo)
        } finally {
            dataDir.deleteRecursively()
        }
    }

    // Lines logged by snapclient's channel-control thread are not reliable evidence: AixLog keeps
    // the current severity and condition process-wide, so a DEBUG or rate-limited log on another
    // thread (main-thread init logs, the audio thread's "Failed to get chunk") can make it drop a
    // concurrent INFO line. strace confirms the commands themselves always arrive. Hence the
    // listener is probed directly, and channel switches are resent until their log line appears.

    /** Waits until something accepts connections on @snapclient_channel. */
    private fun waitForChannelListener(process: Process, timeoutSeconds: Long): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (System.nanoTime() < deadline) {
            if (!process.isRunning()) return false
            try {
                // An empty connection is ignored by the listener (it only acts on n > 0 bytes).
                LocalSocket().use { socket ->
                    socket.connect(
                        LocalSocketAddress("snapclient_channel", LocalSocketAddress.Namespace.ABSTRACT),
                    )
                }
                return true
            } catch (_: IOException) {
                Thread.sleep(100)
            }
        }
        return false
    }

    /**
     * Sends [mode] until snapclient logs that the channel is now [expectedIndex]. Commands are
     * idempotent and the match is on the resulting state ("→ 1 (left)"), not the transition, so a
     * resend after a dropped log line still proves the listener applies them.
     */
    private fun switchChannel(
        mode: String,
        expectedIndex: Int,
        clientLogs: StringBuilder,
        clientProcess: Process,
    ): Boolean {
        repeat(3) {
            sendChannelCommand(mode)
            if (waitForLog(clientLogs, "→ $expectedIndex ($mode)", clientProcess, 3)) return true
        }
        return false
    }

    private fun sendChannelCommand(mode: String) {
        LocalSocket().use { socket ->
            socket.connect(
                LocalSocketAddress("snapclient_channel", LocalSocketAddress.Namespace.ABSTRACT),
            )
            socket.outputStream.write("$mode\n".toByteArray(Charsets.UTF_8))
            socket.outputStream.flush()
        }
    }

    private fun snapshot(sink: StringBuilder): String = synchronized(sink) { sink.toString() }

    private fun waitForLog(
        sink: StringBuilder,
        needle: String,
        process: Process,
        timeoutSeconds: Long = 10,
    ): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (System.nanoTime() < deadline) {
            val found = synchronized(sink) { sink.contains(needle) }
            if (found) return true
            if (!process.isRunning()) return false
            Thread.sleep(100)
        }
        return false
    }

    private fun verifyClientServerHandshake(
        bindAddress: String,
        clientUriHost: String,
        hostId: String,
    ) {
        val streamPort = findFreePort(bindAddress)
        val controlPort = findFreePort(bindAddress)
        val sourcePort = findFreePort(bindAddress)

        val dataDir = File(context.cacheDir, "snapserver-$hostId").apply {
            deleteRecursively()
            mkdirs()
        }
        val emptyConfig = File(dataDir, "snapserver.conf").apply { writeText("") }
        val pcmOutputFile = File(dataDir, "decoded.pcm")

        val serverLogs = StringBuilder()
        val clientLogs = StringBuilder()
        val stopFeeding = AtomicBoolean(false)
        var feederThread: Thread? = null

        try {
            val serverProcess = startProcess(
                File(nativeLibDir, "libsnapserver.so").absolutePath,
                "--config", emptyConfig.absolutePath,
                "--server.datadir=${dataDir.absolutePath}",
                "--server.mdns_enabled=false",
                "--http.enabled=false",
                "--tcp-control.enabled=true",
                "--tcp-control.bind_to_address=$bindAddress",
                "--tcp-control.port=$controlPort",
                "--tcp-control.publish=false",
                "--tcp-streaming.enabled=true",
                "--tcp-streaming.bind_to_address=$bindAddress",
                "--tcp-streaming.port=$streamPort",
                "--tcp-streaming.publish=false",
                "--stream.buffer=200",
                "--stream.source=tcp://$clientUriHost:$sourcePort?name=SmokeTest&mode=server",
                "--logging.sink=stdout",
            )
            drainAsync(serverProcess, serverLogs)

            waitUntilControlPortReady(bindAddress, controlPort, serverProcess, serverLogs)

            val clientProcess = startProcess(
                File(nativeLibDir, "libsnapclient.so").absolutePath,
                "--hostID", hostId,
                "--player", "file:filename=${pcmOutputFile.absolutePath}",
                "--logsink", "stdout",
                "tcp://$clientUriHost:$streamPort",
            )
            drainAsync(clientProcess, clientLogs)

            val connected = waitUntilClientConnected(
                bindAddress = bindAddress,
                controlPort = controlPort,
                hostId = hostId,
                serverProcess = serverProcess,
                clientProcess = clientProcess,
            )

            assertTrue(
                "Snapclient ($hostId) did not appear as connected on $bindAddress:$streamPort.\n" +
                    "--- snapserver logs ---\n$serverLogs\n" +
                    "--- snapclient logs ---\n$clientLogs",
                connected,
            )

            feederThread = startPcmFeeder(bindAddress, sourcePort, stopFeeding)

            val receivedAudio = waitUntilNonZeroPcmDecoded(
                pcmOutputFile = pcmOutputFile,
                serverProcess = serverProcess,
                clientProcess = clientProcess,
            )

            assertTrue(
                "Snapclient ($hostId) did not decode non-zero PCM audio " +
                    "(file size=${pcmOutputFile.length()} bytes).\n" +
                    "--- snapserver logs ---\n$serverLogs\n" +
                    "--- snapclient logs ---\n$clientLogs",
                receivedAudio,
            )
        } finally {
            stopFeeding.set(true)
            feederThread?.join(2000)
            dataDir.deleteRecursively()
        }
    }

    private fun startPcmFeeder(
        bindAddress: String,
        sourcePort: Int,
        stopFlag: AtomicBoolean,
    ): Thread {
        // 48kHz, 16-bit signed LE, stereo -> 20ms chunk = 960 frames * 4 bytes = 3840 bytes
        val sampleRate = 48000
        val framesPerChunk = 960
        val chunk = ByteArray(framesPerChunk * 4)
        var sampleIndex = 0
        for (i in 0 until framesPerChunk) {
            val sample = (sin(2.0 * PI * 440.0 * sampleIndex++ / sampleRate) * 16000.0).toInt().toShort()
            val lo = (sample.toInt() and 0xFF).toByte()
            val hi = ((sample.toInt() shr 8) and 0xFF).toByte()
            val offset = i * 4
            // Left channel
            chunk[offset] = lo
            chunk[offset + 1] = hi
            // Right channel
            chunk[offset + 2] = lo
            chunk[offset + 3] = hi
        }

        return Thread {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(InetAddress.getByName(bindAddress), sourcePort), 5000)
                    val out = socket.getOutputStream()
                    while (!stopFlag.get()) {
                        out.write(chunk)
                        out.flush()
                        Thread.sleep(15)
                    }
                }
            } catch (_: Exception) {
                // Socket closed or interrupted during teardown
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun waitUntilNonZeroPcmDecoded(
        pcmOutputFile: File,
        serverProcess: Process,
        clientProcess: Process,
    ): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (!serverProcess.isRunning() || !clientProcess.isRunning()) {
                return false
            }
            if (pcmOutputFile.exists() && pcmOutputFile.length() > 0 && hasNonZeroBytes(pcmOutputFile)) {
                return true
            }
            Thread.sleep(150)
        }
        return false
    }

    private fun hasNonZeroBytes(file: File): Boolean {
        val buf = ByteArray(8192)
        file.inputStream().use { input ->
            while (true) {
                val read = input.read(buf)
                if (read <= 0) return false
                for (i in 0 until read) {
                    if (buf[i].toInt() != 0) return true
                }
            }
        }
    }

    private fun waitUntilControlPortReady(
        bindAddress: String,
        controlPort: Int,
        serverProcess: Process,
        serverLogs: StringBuilder,
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (!serverProcess.isRunning()) {
                throw AssertionError(
                    "libsnapserver.so exited early with code ${serverProcess.exitValue()}:\n$serverLogs",
                )
            }
            try {
                queryServerStatus(bindAddress, controlPort)
                return
            } catch (_: Exception) {
                Thread.sleep(150)
            }
        }
        throw AssertionError(
            "Timed out waiting for snapserver control port $bindAddress:$controlPort:\n$serverLogs",
        )
    }

    private fun waitUntilClientConnected(
        bindAddress: String,
        controlPort: Int,
        hostId: String,
        serverProcess: Process,
        clientProcess: Process,
    ): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline) {
            if (!serverProcess.isRunning() || !clientProcess.isRunning()) {
                return false
            }
            try {
                val status = queryServerStatus(bindAddress, controlPort)
                val groups = status.optJSONObject("result")
                    ?.optJSONObject("server")
                    ?.optJSONArray("groups")
                if (groups != null) {
                    for (i in 0 until groups.length()) {
                        val clients = groups.getJSONObject(i).optJSONArray("clients") ?: continue
                        for (j in 0 until clients.length()) {
                            val client = clients.getJSONObject(j)
                            if (client.optString("id") == hostId && client.optBoolean("connected")) {
                                return true
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Retry until deadline
            }
            Thread.sleep(200)
        }
        return false
    }

    private fun queryServerStatus(bindAddress: String, controlPort: Int): JSONObject {
        Socket().use { socket ->
            socket.soTimeout = 2000
            socket.connect(InetSocketAddress(InetAddress.getByName(bindAddress), controlPort), 2000)
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            writer.write("""{"id":1,"jsonrpc":"2.0","method":"Server.GetStatus"}""")
            writer.newLine()
            writer.flush()
            val line = reader.readLine() ?: throw IllegalStateException("Empty JSON-RPC response")
            return JSONObject(line)
        }
    }

    private fun findFreePort(bindAddress: String): Int {
        ServerSocket(0, 1, InetAddress.getByName(bindAddress)).use { socket ->
            socket.reuseAddress = true
            return socket.localPort
        }
    }

    // No default arguments on these vararg helpers: Android 6.0's x86_64 dex2oat miscompiled the
    // synthetic `$default` bridge (null array, "Attempt to get length of null array"). The test APK
    // is also interpreted there now (vmSafeMode, see androidTest/AndroidManifest.xml).

    private fun startProcess(vararg command: String): Process =
        track(ProcessBuilder(*command).redirectErrorStream(true).start())

    /** Like [startProcess], but stderr stays separate ([Process.getErrorStream]). */
    private fun startProcessWithSeparateStderr(vararg command: String): Process =
        track(ProcessBuilder(*command).start())

    private fun track(process: Process): Process {
        spawnedProcesses.add(process)
        return process
    }

    private fun runToCompletion(vararg command: String, timeoutSeconds: Long = 10): ProcessResult {
        val process = startProcess(*command)
        val output = StringBuilder()
        val readerThread = drainAsync(process, output)
        val finished = process.waitForSeconds(timeoutSeconds)
        if (!finished) {
            process.forceKill()
            throw AssertionError("Process timed out (${command.joinToString(" ")}):\n$output")
        }
        readerThread.join(2000)
        return ProcessResult(process.exitValue(), output.toString())
    }

    private fun drainAsync(process: Process, sink: StringBuilder): Thread =
        drainAsync(process.inputStream, sink)

    private fun drainAsync(stream: InputStream, sink: StringBuilder): Thread {
        return Thread {
            try {
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        synchronized(sink) {
                            sink.appendLine(line)
                        }
                        line = reader.readLine()
                    }
                }
            } catch (_: Exception) {
                // Stream closed on process exit
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private data class ProcessResult(val exitCode: Int, val output: String)
}

// Process.isAlive(), waitFor(long, TimeUnit) and destroyForcibly() only exist from API 26.
// These helpers keep the smoke test runnable down to minSdk 23.

private fun Process.isRunning(): Boolean = try {
    exitValue()
    false
} catch (_: IllegalThreadStateException) {
    true
}

private fun Process.waitForSeconds(timeoutSeconds: Long): Boolean {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
    while (isRunning()) {
        if (System.nanoTime() >= deadline) return false
        Thread.sleep(50)
    }
    return true
}

private fun Process.forceKill() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) destroyForcibly() else destroy()
}
