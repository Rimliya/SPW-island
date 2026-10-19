// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.core

/** Headless regressions for startup playback recovery; no host or OBS required. */
fun main() {
    // A default paused signal during host startup must not disable the heartbeat fallback.
    val recovery = PlaybackHeartbeatRecovery()
    recovery.trackChanged()
    recovery.playingChanged(false)
    recovery.positionChanged(1_000)
    check(!recovery.positionChanged(1_000)) { "A frozen clock was treated as playback" }
    check(recovery.positionChanged(2_000)) { "Forward heartbeats did not recover playback" }

    // A real pause keeps the host clock frozen; repeated samples must stay paused.
    val paused = PlaybackHeartbeatRecovery()
    paused.trackChanged()
    paused.playingChanged(false)
    paused.positionChanged(5_000)
    check(!paused.positionChanged(5_000)) { "A paused position was treated as playback" }
    check(!paused.positionChanged(5_000)) { "A paused position was treated as playback" }

    // A seek while paused establishes a new baseline but is not a forward heartbeat.
    paused.seeked()
    paused.positionChanged(30_000)
    check(!paused.positionChanged(30_000)) { "A paused seek was treated as playback" }

    // End-to-end timeline: attach-time playing=false followed by a running clock.
    var now = 0L
    val timeline = PlaybackTimeline { now }
    timeline.trackChanged(Track("Test", "Artist", "test.flac"))
    timeline.stateChanged(PlaybackStatus.READY)
    timeline.playingChanged(false)
    now = 1_000_000_000L
    timeline.positionChanged(1_000)
    check(!timeline.snapshot().playing) { "Timeline reported playback before two heartbeats" }
    now = 2_000_000_000L
    timeline.positionChanged(2_000)
    val snapshot = timeline.snapshot()
    check(snapshot.playing) { "Timeline stayed paused after running heartbeats" }
    check(snapshot.status == PlaybackStatus.READY) { "Timeline did not restore READY state" }

    println("Playback checks passed")
}
