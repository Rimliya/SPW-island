// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.platform

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import io.github.gaboron.spwisland.core.SpoutSettings
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.swing.SwingUtilities

/** One native owner thread; one pending frame and at most three reusable pixel arrays. */
internal class SpoutSender(private val report: (Throwable) -> Unit) : AutoCloseable {
    private val lock = Object()
    private var desired: SpoutSettings? = null
    private var generation = 0L
    private var failedGeneration = -1L
    private var closed = false
    private var worker: Thread? = null
    private data class Frame(val pixels: IntArray, val generation: Long)
    private val available = ArrayDeque<IntArray>()
    private var pending: Frame? = null
    @Volatile var sentFrames = 0L; private set
    @Volatile var droppedFrames = 0L; private set
    @Volatile var lastUploadNs = 0L; private set
    @Volatile var maxUploadNs = 0L; private set

    fun configure(settings: SpoutSettings?) = synchronized(lock) {
        if (closed || desired == settings) return@synchronized
        desired = settings; generation++; pending = null; available.clear()
        if (settings != null) {
            repeat(3) { available.add(IntArray(settings.width * settings.height)) }
            if (worker == null) worker = Thread(::run, "SPW Spout sender").apply { isDaemon = true; start() }
        }
        lock.notifyAll()
    }

    val ready: Boolean get() = synchronized(lock) { !closed && desired != null && generation != failedGeneration }

    fun offer(pixels: IntArray) {
        val frame = synchronized(lock) {
            if (!ready || desired!!.width * desired!!.height != pixels.size) return
            // Reclaim a queued (never in-flight) frame before copying. The sender never waits for the EDT.
            pending?.let { available.add(it.pixels); pending = null; droppedFrames++ }
            if (available.isEmpty()) { droppedFrames++; return }
            Frame(available.removeFirst(), generation)
        }
        pixels.copyInto(frame.pixels)
        synchronized(lock) {
            if (closed || generation != frame.generation) return
            pending = frame; lock.notifyAll()
        }
    }

    private fun run() {
        var api: Bridge? = null
        var handle: Pointer? = null
        var memory: Memory? = null
        var currentGeneration = -1L
        try {
            while (true) {
                val state = synchronized(lock) {
                    while (!closed && generation == currentGeneration && pending == null) lock.wait()
                    if (closed) return
                    Triple(generation, desired, pending.also { pending = null })
                }
                val (version, settings, frame) = state
                try {
                    if (version != currentGeneration) {
                        handle?.let { api!!.spw_spout_destroy(it) }; handle = null
                        memory?.close(); memory = null
                        currentGeneration = version
                        if (settings != null) {
                            if (api == null) api = loadBridge()
                            handle = api.spw_spout_create(settings.name, settings.adapter)
                                ?: error("Spout2 初始化失败：${api.spw_spout_error()}。请检查显卡选择，并重新开关输出。")
                            memory = Memory(settings.width.toLong() * settings.height * 4)
                            println("[SPW Spout] Sender=${api.spw_spout_name(handle)} adapter=${settings.adapter} ${settings.width}x${settings.height} BGRA_PRE")
                        }
                    }
                    if (frame != null && settings != null && handle != null && frame.generation == version) {
                        val start = System.nanoTime()
                        memory!!.write(0, frame.pixels, 0, frame.pixels.size)
                        check(api!!.spw_spout_send(handle, memory, settings.width, settings.height) == 1) {
                            "Spout2 发送失败：${api.spw_spout_error()}。请重新开关输出。"
                        }
                        lastUploadNs = System.nanoTime() - start
                        maxUploadNs = maxOf(maxUploadNs, lastUploadNs)
                        sentFrames++
                    }
                } catch (error: Throwable) {
                    handle?.let { api?.spw_spout_destroy(it) }; handle = null
                    memory?.close(); memory = null
                    synchronized(lock) { if (generation == version) { failedGeneration = version; pending = null } }
                    SwingUtilities.invokeLater { if (!synchronized(lock) { closed }) report(error) }
                } finally {
                    if (frame != null) synchronized(lock) {
                        if (generation == frame.generation && !closed) available.add(frame.pixels)
                    }
                }
            }
        } finally {
            handle?.let { api?.spw_spout_destroy(it) }; memory?.close()
        }
    }

    override fun close() = synchronized(lock) {
        closed = true; pending = null; available.clear(); desired = null; lock.notifyAll()
    }

    private interface Bridge : Library {
        fun spw_spout_create(name: String, adapter: Int): Pointer?
        fun spw_spout_send(handle: Pointer, pixels: Pointer, width: Int, height: Int): Int
        fun spw_spout_destroy(handle: Pointer)
        fun spw_spout_error(): String
        fun spw_spout_name(handle: Pointer): String
    }

    companion object {
        private val bridge: Bridge by lazy {
            check(Native.POINTER_SIZE == 8) { "Spout2 需要 Windows x64 JVM。" }
            val bytes = SpoutSender::class.java.getResourceAsStream("/native/spw-spout.dll").use { input ->
                checkNotNull(input) { "插件包缺少 native/spw-spout.dll。" }
                input.readBytes()
            }
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(bytes).joinToString("") { "%02x".format(it) }
            val directory = Files.createDirectories(Path.of(System.getProperty("java.io.tmpdir"), "spw-spout-native"))
            // Reuse identical DLLs across runs; loaded Windows DLLs cannot be deleted at JVM shutdown.
            // Loading a copy also keeps the installed plugin directory replaceable during an update.
            val dll = directory.resolve("$hash.dll")
            if (!Files.exists(dll)) {
                val temporary = Files.createTempFile(directory, "extract-", ".tmp")
                try {
                    Files.write(temporary, bytes)
                    try { Files.move(temporary, dll, StandardCopyOption.ATOMIC_MOVE) }
                    catch (error: java.io.IOException) { if (!Files.exists(dll)) throw error }
                } finally { Files.deleteIfExists(temporary) }
            }
            check(digest.digest(Files.readAllBytes(dll)).contentEquals(digest.digest(bytes))) { "Spout2 原生库校验失败。" }
            // Keep the current binary and two previous versions. Another JVM may still hold an old DLL;
            // Windows refuses that deletion, so it is retried at the next load, without affecting output.
            Files.list(directory).use { files ->
                files.filter { it != dll && it.fileName.toString().matches(Regex("[0-9a-f]{64}\\.dll")) }
                    .sorted(compareByDescending { Files.getLastModifiedTime(it).toMillis() })
                    .skip(2).forEach { runCatching { Files.deleteIfExists(it) } }
            }
            Native.load(dll.toAbsolutePath().toString(), Bridge::class.java)
        }
        private fun loadBridge(): Bridge = bridge
    }
}
