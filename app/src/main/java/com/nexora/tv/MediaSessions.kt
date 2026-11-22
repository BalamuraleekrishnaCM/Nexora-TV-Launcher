package com.nexora.tv

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class MediaSessions(private val context: Context) : DefaultLifecycleObserver {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, NexoraNotificationListener::class.java)
    private val callbacks = mutableMapOf<android.media.session.MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { update(it.orEmpty()) }

    var accessGranted by mutableStateOf(false)
        private set
    var metadata by mutableStateOf<MediaMetadata?>(null)
        private set
    var playback by mutableStateOf<PlaybackState?>(null)
        private set
    private var active: MediaController? = null

    override fun onStart(owner: LifecycleOwner) {
        accessGranted = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
        if (!accessGranted) return
        try {
            manager.addOnActiveSessionsChangedListener(listener, listenerComponent)
            update(manager.getActiveSessions(listenerComponent))
        } catch (_: SecurityException) {
            accessGranted = false
            stop()
        }
    }

    override fun onStop(owner: LifecycleOwner) = stop()

    private fun stop() {
        manager.removeOnActiveSessionsChangedListener(listener)
        update(emptyList())
    }

    private fun update(controllers: List<MediaController>) {
        val tokens = controllers.map { it.sessionToken }.toSet()
        val previous = callbacks.toMap()
        callbacks.clear()
        previous.filterKeys { it !in tokens }.values.forEach { (controller, callback) ->
            controller.unregisterCallback(callback)
        }
        controllers.forEach { controller ->
            val token = controller.sessionToken
            callbacks[token] = previous[token] ?: run {
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
                    override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
                    override fun onSessionDestroyed() {
                        callbacks.remove(token)
                        controller.unregisterCallback(this)
                        publish()
                    }
                }
                controller.registerCallback(callback)
                controller to callback
            }
        }
        publish()
    }

    private fun publish() {
        val controllers = callbacks.values.map { it.first }
        active = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.metadata != null }
            ?: controllers.firstOrNull()
        metadata = active?.metadata
        playback = active?.playbackState
    }

    fun control(action: Long, fallback: () -> Unit) {
        val session = active
        if (session == null) {
            fallback()
            return
        }
        val actions = playback?.actions ?: 0L
        val controls = session.transportControls
        when {
            action == PlaybackState.ACTION_SKIP_TO_PREVIOUS && actions and action != 0L -> controls.skipToPrevious()
            action == PlaybackState.ACTION_SKIP_TO_NEXT && actions and action != 0L -> controls.skipToNext()
            action == PlaybackState.ACTION_PLAY_PAUSE && playback?.state == PlaybackState.STATE_PLAYING &&
                actions and PlaybackState.ACTION_PAUSE != 0L -> controls.pause()
            action == PlaybackState.ACTION_PLAY_PAUSE && playback?.state != PlaybackState.STATE_PLAYING &&
                actions and PlaybackState.ACTION_PLAY != 0L -> controls.play()
            action == PlaybackState.ACTION_PLAY_PAUSE && actions and action != 0L -> fallback()
        }
    }
}
