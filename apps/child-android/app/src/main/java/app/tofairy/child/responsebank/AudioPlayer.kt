package app.tofairy.child.responsebank

import android.content.Context
import android.media.MediaPlayer
import android.util.Log

/**
 * 사전 녹음 클립의 온디바이스 재생 (CLAUDE.md §6). 지연 0 목표.
 * 클립은 `assets/responsebank/audio/<clip>.ogg` 에 번들. 누락 시 무해하게 스킵(텍스트만 노출).
 */
interface AudioPlayer {
    fun play(clip: String?)
    fun stop()
}

/** assets 의 ogg 클립을 재생하는 기본 구현. 자산이 없으면 조용히 스킵. */
class AssetAudioPlayer(private val context: Context) : AudioPlayer {
    private var player: MediaPlayer? = null

    override fun play(clip: String?) {
        if (clip == null) return
        stop()
        val path = "responsebank/audio/$clip.ogg"
        try {
            context.assets.openFd(path).use { afd ->
                player = MediaPlayer().apply {
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    setOnCompletionListener { it.release() }
                    prepare()
                    start()
                }
            }
        } catch (e: Exception) {
            // 골격 단계: 클립 미존재가 정상. 텍스트 버블만으로 동작.
            Log.d(TAG, "audio clip unavailable, skipping: $clip")
        }
    }

    override fun stop() {
        player?.let { runCatching { it.release() } }
        player = null
    }

    private companion object {
        const val TAG = "FairyAudio"
    }
}

/** 오디오 자산이 없거나 음소거 환경(미리보기/테스트)용 무동작 구현. */
object NoopAudioPlayer : AudioPlayer {
    override fun play(clip: String?) = Unit
    override fun stop() = Unit
}
