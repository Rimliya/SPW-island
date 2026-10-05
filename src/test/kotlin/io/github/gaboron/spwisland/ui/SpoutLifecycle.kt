// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.SpoutSettings
import io.github.gaboron.spwisland.platform.SpoutSender
import java.util.concurrent.atomic.AtomicInteger

object SpoutLifecycle {
    @JvmStatic fun main(args: Array<String>) {
        val failures = AtomicInteger()
        val sender = SpoutSender { failures.incrementAndGet() }
        fun await(label: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 5_000_000_000L
            while (!condition() && System.nanoTime() < deadline) Thread.sleep(10)
            check(condition()) { label }
        }
        try {
            val options = SpoutSettings(true, "SPW Lifecycle Check", 60, 320, 128, 31)
            val pixels = IntArray(320 * 128) { 0x80800000.toInt() }
            sender.configure(options)
            await("Invalid adapter must report failure") { failures.get() == 1 }
            repeat(100) { sender.offer(pixels) }
            Thread.sleep(100)
            check(failures.get() == 1 && !sender.ready) { "Failure must latch without repeating notifications" }
            repeat(5) {
                sender.configure(null)
                check(!sender.ready)
                sender.configure(options.copy(adapter = 0))
                val before = sender.sentFrames
                repeat(1000) { sender.offer(pixels) }
                await("Sender must recover and drain latest frame") { sender.sentFrames > before }
            }
            sender.configure(options.copy(adapter = 0, width = 640, height = 256))
            val before = sender.sentFrames
            sender.offer(IntArray(640 * 256) { 0xff00ff00.toInt() })
            await("Resize must recreate the native buffer") { sender.sentFrames > before }
            check(failures.get() == 1)
        } finally { sender.close() }
        await("Sender thread must terminate") {
            Thread.getAllStackTraces().keys.none { it.name == "SPW Spout sender" && it.isAlive }
        }
        println("PASS native error latching, recovery, overload latest-frame policy, resize, thread cleanup; dropped=${sender.droppedFrames}")
    }
}
