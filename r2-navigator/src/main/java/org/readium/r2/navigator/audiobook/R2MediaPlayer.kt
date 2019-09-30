package org.readium.r2.navigator.audiobook

import android.annotation.TargetApi
import android.app.ProgressDialog
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.media.MediaPlayer.OnPreparedListener
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.readium.r2.shared.Link
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

class R2MediaPlayer(private var items: MutableList<Link>, private val publicationPath: String, private var callback: MediaPlayerCallback) : OnPreparedListener {

    /**
     * Inner class only ever used by the mediaplayer. Will only be called on android 6.0 and more.
     * Any device with a version inferior to it should not ever try to call this class.
     * MyMediaDataSource serves to provide a memory block fragment to the Android MediaPlayer api.
     *
     * data: ByteArray - the data that should be streamed to the media player.
     */
    @TargetApi(23)
    private inner class MyMediaDataSource(private val data: ByteArray) : MediaDataSource() {

        override fun getSize(): Long {
            return data.size.toLong()
        }

        override fun readAt(position: Long, buffer: ByteArray?, offset: Int, size: Int): Int {
            val length = data.size
            if (position >= length)
                return -1

            var rsize = size

            if (position + size >= length) {
                rsize = length - position.toInt() - 1
            }
            System.arraycopy(data, position.toInt(), buffer, offset, rsize)
            return rsize
        }
        override fun close() {
            // Nothing to do here. However, close() has to be overriden.
        }
    }

    private val uiScope = CoroutineScope(Dispatchers.Main)

    var progress: ProgressDialog? = null

    var mediaPlayer: MediaPlayer = MediaPlayer()

    val isPlaying: Boolean
        get() = mediaPlayer.isPlaying

    val duration: Double
        get() = mediaPlayer.duration.toDouble() // if (isPrepared) {mediaPlayer.duration.toDouble()}else {0.0}

    val currentPosition: Double
        get() = mediaPlayer.currentPosition.toDouble() // if (isPrepared) {mediaPlayer.currentPosition.toDouble()}else {0.0}

    var isPaused: Boolean
    var isPrepared: Boolean

    private var index: Int

    init {
        isPaused = false
        isPrepared = false
        index = 0
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
            mediaPlayer.release()
        }
        toggleProgress(true)
    }

    /**
     * Called when the media file is ready for playback.
     *
     * @param mp the MediaPlayer that is ready for playback
     */
    override fun onPrepared(mp: MediaPlayer?) {
        toggleProgress(false)
        this.start()
        callback.onPrepared()
        isPrepared = true
    }

    fun startPlayer() {
        mediaPlayer.reset()
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                //ANDROID 6 and later
                val fileZip = publicationPath
                val toFind = Uri.parse(items[index].href).toString()
                val zip = ZipFile(File(fileZip))
                val data = zip.getInputStream(zip.getEntry(toFind)).readBytes()
                mediaPlayer.setDataSource(MyMediaDataSource(data))
            } else {
                //ANDROID 5 and 5.1
                mediaPlayer.setDataSource(Uri.parse(items[index].href).toString())
            }

            mediaPlayer.setOnPreparedListener(this)
            mediaPlayer.prepareAsync()
            toggleProgress(true)

        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun toggleProgress(show: Boolean) {
        uiScope.launch {
            if (show) progress?.show()
            else progress?.hide()
        }
    }

    fun seekTo(progression: Any) {
        when (progression) {
            is Double -> mediaPlayer.seekTo(progression.toInt())
            is Int -> mediaPlayer.seekTo(progression)
            else -> mediaPlayer.seekTo(progression.toString().toInt())
        }
    }

    fun stop() {
        if (isPrepared) {
            mediaPlayer.stop()
            isPrepared = false
        }
    }

    fun pause() {
        if (isPrepared) {
            mediaPlayer.pause()
            isPaused = true
        }
    }

    fun start() {
        mediaPlayer.start()
        isPaused = false
        isPrepared = false
        mediaPlayer.setOnCompletionListener {
            callback.onComplete(index, it.currentPosition, it.duration)
        }
    }

    fun resume() {
        if (isPrepared) {
            mediaPlayer.start()
            isPaused = false
        }
    }

    fun goTo(index: Int) {
        this.index = index
        isPaused = false
        isPrepared = false
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        toggleProgress(true)
    }

    fun previous() {
        index -= 1
        isPaused = false
        isPrepared = false
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        toggleProgress(true)
    }

    fun next() {
        index += 1
        isPaused = false
        isPrepared = false
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        toggleProgress(true)
    }
}

interface MediaPlayerCallback {
    fun onPrepared()
    fun onComplete(index: Int, currentPosition: Int, duration: Int)
}