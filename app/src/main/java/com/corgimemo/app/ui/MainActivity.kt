package com.corgimemo.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.data.repository.CorgiRepository
import com.corgimemo.app.data.repository.TodoRepository
import com.corgimemo.app.ui.theme.CorgiMemoTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var todoRepository: TodoRepository

    @Inject
    lateinit var corgiRepository: CorgiRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化数据库
        initDatabase()

        setContent {
            CorgiMemoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "CorgiMemo",
                            fontSize = 32.sp,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Text(
                            text = "你的专属备忘录",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "数据库已初始化",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }

    private fun initDatabase() {
        lifecycleScope.launch {
            // 检查是否已有柯基数据
            val existingCorgi = corgiRepository.getCorgiData()
            if (existingCorgi == null) {
                // 创建初始柯基数据
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val today = dateFormat.format(Date())
                
                val newCorgi = CorgiData(
                    name = "小柯基",
                    lastActiveDate = today
                )
                corgiRepository.insertCorgi(newCorgi)
            }
        }
    }
}