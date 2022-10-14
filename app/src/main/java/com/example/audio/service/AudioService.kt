package com.example.audio.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.BassBoost
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.audio.App.Companion.channelId
import com.example.audio.R
import com.example.audio.activity.MainActivity
import com.example.audio.repository.DataStoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.*


class AudioService : Service() {
    private var isRecording = false
    private var record: AudioRecord? = null
    private var player: AudioTrack? = null
    private var recordState = 0
    private var playerState: Int = 0
    private var minBuffer = 0
    private var initiated = false

    //Audio Settings
    private val source = MediaRecorder.AudioSource.MIC
    private val channelIn: Int = AudioFormat.CHANNEL_IN_MONO
    private val channelOut: Int = AudioFormat.CHANNEL_OUT_MONO
    private val format: Int = AudioFormat.ENCODING_PCM_16BIT

    private lateinit var dataStoreRepo: DataStoreRepository
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var fos: FileOutputStream? = null
    private var dos: DataOutputStream? = null

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        dataStoreRepo = DataStoreRepository(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification =
            NotificationCompat.Builder(this, channelId).setContentTitle("Audio Service")
//            .setContentText("this is my notification")
                .setSmallIcon(R.mipmap.ic_launcher).setContentIntent(pendingIntent).build()
        startForeground(1, notification)
        if (!initiated) {
            isRecording = false
            initAudio()
        }
        object : Thread() {
            override fun run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
                startAudio()
            }
        }.start()
        return START_STICKY
    }

    override fun onDestroy() {
        endAudio()
        super.onDestroy()
        job.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun initAudio() {
        //Tests all sample rates before selecting one that works
        val sampleRate = getSampleRate()
        minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelIn, format)
        record = AudioRecord(source, sampleRate, channelIn, format, minBuffer)
        recordState = record!!.state
        val id = record!!.audioSessionId
        Log.d("Record", "ID: $id")
        playerState = 0
        player = AudioTrack(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
            AudioFormat.Builder().setEncoding(format).setSampleRate(sampleRate)
                .setChannelMask(channelOut).build(),
            minBuffer,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        playerState = player!!.state
        // Formatting Audio
        if (AcousticEchoCanceler.isAvailable()) {
            val echo = AcousticEchoCanceler.create(id)
            echo.enabled = true
            Log.d("Echo", "Off")
        }
        if (NoiseSuppressor.isAvailable()) {
            val noise = NoiseSuppressor.create(id)
            noise.enabled = true
            Log.d("Noise", "Off")
        }
        if (AutomaticGainControl.isAvailable()) {
            val gain = AutomaticGainControl.create(id)
            gain.enabled = false
            Log.d("Gain", "Off")
        }
        val base = BassBoost(1, player!!.audioSessionId)
        base.setStrength(1000.toShort())
        initiated = true
    }

    private fun startAudio() {
        var read: Int
        var write: Int
        if (recordState == AudioRecord.STATE_INITIALIZED && playerState == AudioTrack.STATE_INITIALIZED) {
            record?.startRecording()
            player?.play()
            isRecording = true
            Log.d("Record", "Recording...")
        }
        scope.launch {
            val isRecordingOn = dataStoreRepo.readFromDataStore.first()
            Log.d("isRecordingOn: ", "$isRecordingOn")
            Log.d("Storage Path: ", "${externalMediaDirs.firstOrNull()?.absolutePath}")

            if (isRecordingOn) {
                val audioFile = File(getFilename)
                try {
                    fos = FileOutputStream(audioFile)
                    dos = DataOutputStream(BufferedOutputStream(fos))
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            while (isRecording) {
                val audioData = ShortArray(minBuffer)
                read = if (record != null) record!!.read(audioData, 0, minBuffer) else break
                Log.d("Record", "Read: $read")

//            var amplitude = 0
//            if (read < 0) {
//                amplitude = 0
//            }
//
//            var sum = 0
//            for (i in 0 until read) {
//                sum += abs(audioData[i].toInt())
//            }
//
//            if (read > 0) {
//                amplitude = sum / read
//            }
//            if(amplitude>=18000){
//                //clap detected
//
//            }
                write = if (player != null) player!!.write(audioData, 0, read) else break
                Log.d("Record", "Write: $write")
                if (isRecordingOn) {
                    for (element in audioData) {
                        try {
                            dos?.writeShort(element.toInt())
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
//                try {
//                    fos?.write(short2byte(audioData))
//                } catch (e: IOException) {
//                    e.printStackTrace()
//                }
                }
            }
        }
        try {
            dos?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }


    private fun endAudio() {
        if (record != null) {
            if (record?.recordingState == AudioRecord.RECORDSTATE_RECORDING) record?.stop()
            isRecording = false
            Log.d("Record", "Stopping...")
        }
        if (player != null) {
            if (player?.playState == AudioTrack.PLAYSTATE_PLAYING) player?.stop()
            isRecording = false
            Log.d("Player", "Stopping...")
        }
    }

    private fun getSampleRate(): Int {
        //Find a sample rate that works with the device
        for (rate in intArrayOf(8000, 11025, 16000, 22050, 44100, 48000)) {
            val buffer = AudioRecord.getMinBufferSize(rate, channelIn, format)
            if (buffer > 0) return rate
        }
        return -1
    }

    private val getFilename: String
        get() {
            val file = externalMediaDirs.first().absoluteFile
            if (!file.exists()) {
                file.mkdirs()
            }
            return "${file.absolutePath}/${System.currentTimeMillis()}.pcm"
        }
}