package com.example.audio.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.audio.databinding.ActivitySpleshBinding

class SplashActivity : AppCompatActivity() {

    lateinit var binding: ActivitySpleshBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpleshBinding.inflate(layoutInflater)
        setContentView(binding.root)


    }
}