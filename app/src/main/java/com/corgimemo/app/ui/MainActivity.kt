package com.corgimemo.app.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.activity.compose.setContent
import com.corgimemo.app.ui.theme.CorgiMemoTheme

/**
 * 应用的主 Activity
 * 
 * 作为应用的入口点，负责设置 Compose 内容和主题
 */
class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置 Compose 内容
        setContent {
            // 应用主题
            CorgiMemoTheme {
                // 表面容器，提供背景色
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 应用主内容（导航宿主）
                    CorgiMemoApp()
                }
            }
        }
    }
}