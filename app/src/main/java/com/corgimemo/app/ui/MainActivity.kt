package com.corgimemo.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.data.repository.CorgiRepository
import com.corgimemo.app.data.repository.TodoRepository
import com.corgimemo.app.ui.navigation.AppNavHost
import com.corgimemo.app.ui.navigation.Screen
import com.corgimemo.app.ui.theme.CorgiMemoTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
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

    @Inject
    lateinit var corgiPreferences: CorgiPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        initDatabase()

        setContent {
            CorgiMemoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OnboardingRouter(corgiPreferences = corgiPreferences)
                }
            }
        }
    }

    private fun initDatabase() {
    }
}

/**
 * 引导路由组件
 *
 * 读取 isOnboardingCompleted 标志，决定起始页面
 *
 * @param corgiPreferences 偏好设置管理器
 */
@Composable
private fun OnboardingRouter(
    corgiPreferences: CorgiPreferences
) {
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val isCompleted = corgiPreferences.isOnboardingCompleted.first()
        startDestination = if (isCompleted) {
            Screen.Home.route
        } else {
            Screen.Onboarding.route
        }
    }

    val destination = startDestination
    if (destination == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                strokeWidth = 4.dp
            )
        }
    } else {
        val navController = rememberNavController()
        AppNavHost(
            navController = navController,
            startDestination = destination
        )
    }
}