package com.example.audio.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.media.*
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.audio.R
import com.example.audio.repository.DataStoreRepository
import com.example.audio.service.AudioService
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.*


class MainActivity : AppCompatActivity() {

    private lateinit var permissionLauncher: ActivityResultLauncher<String>

    //Audio
    private lateinit var mOn: ImageButton
    private lateinit var play: ImageButton
    private lateinit var stop: ImageButton
    private lateinit var pathTextView: TextView
    private lateinit var isRecordingOnSwitch: SwitchMaterial
    private var isOn = false
    private var audioTrack: AudioTrack? = null

    private lateinit var dataStoreRepo: DataStoreRepository

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        dataStoreRepo = DataStoreRepository(this)
        val serviceIntent = Intent(this, AudioService::class.java)
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {}

        mOn = findViewById(R.id.button)
        play = findViewById(R.id.play)
        stop = findViewById(R.id.stop)
        pathTextView = findViewById(R.id.pathTextView)
        isRecordingOnSwitch = findViewById(R.id.isRecordingOnSwitch)

        lifecycleScope.launch {
            val isRecordingOn: Boolean = dataStoreRepo.readFromDataStore.first()
            isRecordingOnSwitch.isChecked = isRecordingOn
        }
        isRecordingOnSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                dataStoreRepo.saveToDataStore(isChecked)
            }
        }

        pathTextView.text =
            getExternalFilesDir(null)?.path + "\n" + filesDir.path + "\n" + externalMediaDirs.firstOrNull()?.absolutePath + "\n" + externalMediaDirs.firstOrNull()
                ?.listFiles()?.joinToString { if (it.isFile) it.path else "" }
        isOn = false

        if (isMyServiceRunning(AudioService::class.java)) {
            isOn = true
            mOn.background.colorFilter = PorterDuffColorFilter(
                resources.getColor(
                    R.color.colorOn, null
                ), PorterDuff.Mode.SRC_ATOP
            )
        } else {
            isOn = false
            mOn.background.colorFilter = PorterDuffColorFilter(
                resources.getColor(
                    R.color.colorOff, null
                ), PorterDuff.Mode.SRC_ATOP
            )
        }
        play.setOnClickListener {
            playRecord(File("${externalMediaDirs.first().absoluteFile}/1652966017151.pcm"))
        }
        stop.setOnClickListener {
            audioTrack?.stop()
        }
        mOn.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                mOn.background.colorFilter = PorterDuffColorFilter(
                    resources.getColor(
                        if (!isOn) R.color.colorOn else R.color.colorOff, null
                    ), PorterDuff.Mode.SRC_ATOP
                )
                isOn = !isOn
                if (isOn) {
                    if (!isMyServiceRunning(AudioService::class.java)) {
                        ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
                    } else {
                        Toast.makeText(this@MainActivity, "Running", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    stopService(serviceIntent)
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun playRecord(file: File) {
        //Read the file
        val musicLength: Int = (file.length() / 2).toInt()
        val music = ShortArray(musicLength)
        try {
            val inputStream: InputStream = FileInputStream(file)
            val dis = DataInputStream(BufferedInputStream(inputStream))
            var i = 0
            while (dis.available() > 0) {
                music[i] = dis.readShort()
                i++
            }
            dis.close()
            val sampleRate = getSampleRate()
            audioTrack = AudioTrack(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                AudioFormat.Builder().setEncoding(format).setSampleRate(sampleRate)
                    .setChannelMask(channelOut).build(),
                musicLength * 2,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            audioTrack?.play()
            audioTrack?.write(music, 0, musicLength)
//            audioTrack.stop()
        } catch (t: Throwable) {
            Log.e("TAG", "Play failed")
        }
    }

    private val channelIn: Int = AudioFormat.CHANNEL_IN_MONO
    private val channelOut: Int = AudioFormat.CHANNEL_OUT_MONO
    private val format: Int = AudioFormat.ENCODING_PCM_16BIT
    private fun getSampleRate(): Int {
        //Find a sample rate that works with the device
        for (rate in intArrayOf(8000, 11025, 16000, 22050, 44100, 48000)) {
            val buffer = AudioRecord.getMinBufferSize(rate, channelIn, format)
            if (buffer > 0) return rate
        }
        return -1
    }
}