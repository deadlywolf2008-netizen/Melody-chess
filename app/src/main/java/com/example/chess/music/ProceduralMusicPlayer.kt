package com.example.chess.music

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

class ProceduralMusicPlayer {
    private val sampleRate = 22050 // Lower sample rate for lower CPU footprint
    private var audioTrack: AudioTrack? = null
    private var synthJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _volume = MutableStateFlow(0.5f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _selectedPreset = MutableStateFlow(0) // 0: Zen Chill, 1: Cosmic Quiet, 2: Retro Rain
    val selectedPreset: StateFlow<Int> = _selectedPreset.asStateFlow()

    val presets = listOf("Zen Chill", "Cosmic Quiet", "Retro Rain")

    fun setVolume(vol: Float) {
        _volume.value = vol.coerceIn(0f, 1f)
        audioTrack?.setVolume(_volume.value)
    }

    fun setPreset(index: Int) {
        _selectedPreset.value = index.coerceIn(0, presets.size - 1)
    }

    fun play() {
        if (_isPlaying.value) return
        _isPlaying.value = true

        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.setVolume(_volume.value)
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e("ProceduralMusicPlayer", "Failed to init AudioTrack: ${e.message}")
            _isPlaying.value = false
            return
        }

        // Start synthesising in the background
        synthJob = scope.launch {
            synthesizeLoop(bufferSize)
        }
    }

    fun pause() {
        if (!_isPlaying.value) return
        _isPlaying.value = false
        synthJob?.cancel()
        synthJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e("ProceduralMusicPlayer", "Error pausing AudioTrack: ${e.message}")
        }
        audioTrack = null
    }

    fun togglePlay() {
        if (_isPlaying.value) pause() else play()
    }

    fun release() {
        pause()
        scope.cancel()
    }

    // High performance sound synth loops
    private suspend fun CoroutineScope.synthesizeLoop(bufferSizeInBytes: Int) {
        val random = Random()
        val numSamples = bufferSizeInBytes / 2 // 16-bit is 2 bytes
        val buffer = ShortArray(numSamples)

        // Musical clock & synthesiser constants
        var elapsedSamples = 0L
        
        // Voice data class for active synth sounds
        class ActiveVoice(
            val freq: Double,
            val startSample: Long,
            val durationSamples: Long,
            val maxAmp: Double,
            val type: Int // 0: sine (mellow), 1: triangle (woodsy/warm), 2: quiet noise
        ) {
            fun getSample(currentSample: Long): Double {
                val t = (currentSample - startSample)
                if (t < 0 || t >= durationSamples) return 0.0
                
                // Linear ADSR envelope for smooth transitions (soft attack, slow decay)
                val attackSamples = (durationSamples * 0.1).toLong()
                val releaseSamples = (durationSamples * 0.4).toLong()
                val progress = t.toDouble() / durationSamples

                val envelope = when {
                    t < attackSamples -> t.toDouble() / attackSamples
                    t > (durationSamples - releaseSamples) -> {
                        val remaining = durationSamples - t
                        remaining.toDouble() / releaseSamples
                    }
                    else -> 1.0
                }

                val timeInSec = t.toDouble() / sampleRate
                val waveform = when (type) {
                    0 -> sin(2.0 * PI * freq * timeInSec) // Sine
                    1 -> {
                        // Custom Triangle Wave
                        val phase = (freq * timeInSec % 1.0)
                        if (phase < 0.5) 4.0 * phase - 1.0 else 3.0 - 4.0 * phase
                    }
                    else -> sin(2.0 * PI * freq * timeInSec)
                }

                return waveform * envelope * maxAmp
            }
        }

        val activeVoices = mutableListOf<ActiveVoice>()

        // Chord frequencies (Zen Lofi chords in A minor) Let's make them jazzy & deep
        val am7 = doubleArrayOf(110.0, 220.0, 261.63, 329.63, 392.00)  // A, C, E, G
        val fmaj9 = doubleArrayOf(87.31, 174.61, 261.63, 329.63, 349.23) // F, A, C, E
        val cmaj7 = doubleArrayOf(130.81, 261.63, 329.63, 392.00, 493.88) // C, E, G, B
        val dm7 = doubleArrayOf(146.83, 293.66, 349.23, 440.00, 523.25)  // D, F, A, C

        val progressions = listOf(am7, fmaj9, cmaj7, dm7)
        var currentChordIndex = 0

        // Melody pool (A Minor Pentatonic: A, C, D, E, G)
        val melodyNotes = doubleArrayOf(440.0, 523.25, 587.33, 659.25, 783.99, 880.0, 1046.5)

        val beatDuration = (sampleRate * 1.2).toLong() // 1.2 seconds per beat (approx 50 BPM)
        var lastBeatTriggered = -1L

        while (currentCoroutineContext().isActive && _isPlaying.value) {
            for (i in 0 until numSamples) {
                val absoluteSample = elapsedSamples + i

                // Trigger beat actions (runs on a grid)
                val currentBeat = absoluteSample / beatDuration
                if (currentBeat != lastBeatTriggered) {
                    lastBeatTriggered = currentBeat
                    
                    val stepInBar = (currentBeat % 8).toInt() // 8 beat bar
                    
                    // Trigger a chord on beat 0 & 4
                    if (stepInBar == 0 || stepInBar == 4) {
                        if (stepInBar == 0) {
                            // Cycle through chords
                            currentChordIndex = (currentChordIndex + 1) % progressions.size
                        }
                        
                        val selectedChord = progressions[currentChordIndex]
                        val chordDuration = beatDuration * 3
                        
                        // Select which chord voice type to use based on preset
                        val voiceType = if (_selectedPreset.value == 1) 0 else 1 // 0 is sine (cosmic), 1 is triangle (ambient)

                        // Trigger chord voices
                        for (noteFreq in selectedChord) {
                            activeVoices.add(
                                ActiveVoice(
                                    freq = noteFreq,
                                    startSample = absoluteSample,
                                    durationSamples = chordDuration,
                                    maxAmp = 0.15,
                                    type = voiceType
                                )
                            )
                        }
                    }

                    // Trigger quiet melodies on step 1, 3, 5, 6 with random probability (70%)
                    val isMelodyPreset = _selectedPreset.value != 1 // Cosmic is super drone quiet, others are melodic
                    if (isMelodyPreset && (stepInBar == 1 || stepInBar == 2 || stepInBar == 5 || stepInBar == 6 || stepInBar == 7)) {
                        if (random.nextDouble() < 0.6) {
                            val pickMelodyNote = melodyNotes[random.nextInt(melodyNotes.size)]
                            val melodyDuration = (beatDuration * 0.8).toLong()
                            activeVoices.add(
                                ActiveVoice(
                                    freq = pickMelodyNote,
                                    startSample = absoluteSample,
                                    durationSamples = melodyDuration,
                                    maxAmp = 0.08,
                                    type = 0 // Mellow Sine bells
                                )
                            )
                        }
                    }
                    
                    // Extra deep sub bass on beat 0
                    if (stepInBar == 0) {
                        val baseRoot = progressions[currentChordIndex][0] / 2.0 // Drop an octave
                        activeVoices.add(
                            ActiveVoice(
                                freq = baseRoot,
                                startSample = absoluteSample,
                                durationSamples = beatDuration * 3,
                                maxAmp = 0.22,
                                type = 0 // Thick pure sub sine
                            )
                        )
                    }
                }

                // Mix all active voices
                var mixedSample = 0.0
                
                // Clean up finished voices
                activeVoices.removeAll { absoluteSample > it.startSample + it.durationSamples }

                for (voice in activeVoices) {
                    mixedSample += voice.getSample(absoluteSample)
                }

                // 2. Add beautiful crackling texture (Lofi Vinyl rustle)
                // We add some light crackles / paper rustles for retro preset
                val hasCrackleText = _selectedPreset.value == 2 || _selectedPreset.value == 0
                if (hasCrackleText) {
                    // Constant weak pinkish noise
                    val randomVal = random.nextDouble() * 2.0 - 1.0
                    mixedSample += randomVal * 0.008
                    
                    // Rare short pop bursts
                    if (random.nextDouble() < 0.00015) {
                        mixedSample += (random.nextDouble() * 2.0 - 1.0) * 0.12
                    }
                }

                // Soft clip mixed sample
                mixedSample = mixedSample.coerceIn(-1.0, 1.0)

                // Scale to PCM short (16-bit integer)
                buffer[i] = (mixedSample * 32767.0).toInt().toShort()
            }

            // Write PCM to AudioTrack synchronously
            if (_isPlaying.value) {
                audioTrack?.write(buffer, 0, numSamples)
            }
            elapsedSamples += numSamples

            // Mild throttling to avoid blocking cpu
            delay(10)
        }
    }
}
