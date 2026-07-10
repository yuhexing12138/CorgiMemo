package com.corgimemo.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import com.corgimemo.app.ui.theme.ThemeManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.corgimemo.app.backup.BackupFileHandler
import com.corgimemo.app.backup.BackupManager
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.repository.CorgiRepository
import com.corgimemo.app.data.repository.TodoRepository
import com.corgimemo.app.ui.navigation.AppNavHost
import com.corgimemo.app.ui.navigation.Screen
import com.corgimemo.app.ui.theme.CorgiMemoTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var todoRepository: TodoRepository

    @Inject
    lateinit var corgiRepository: CorgiRepository

    @Inject
    lateinit var corgiPreferences: CorgiPreferences

    companion object {
        private const val NAVIGATE_TO = "navigate_to"
        private const val VALUE_CREATE_TODO = "create_todo"
        private const val VALUE_HOME = "home"
        private const val VALUE_EDIT_TODO = "edit_todo"
        private const val VALUE_BACKUP_HISTORY = "backup_history"
        private const val EXTRA_TODO_ID = "extra_todo_id"
    }

    private var pendingNavigation: NavigationTarget? = null
    private var exportLauncher: ActivityResultLauncher<Intent>? = null
    private var importLauncher: ActivityResultLauncher<Intent>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 启用沉浸式全屏（系统栏透明 + 内容延伸到状态栏/导航栏）
        enableEdgeToEdge()

        pendingNavigation = intent?.let { parseNavigationIntent(it) }

        exportLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = result.data?.data ?: return@registerForActivityResult
                val format = BackupFileHandler.getAndClearPendingExportFormat()
                    ?: return@registerForActivityResult
                handleExport(format, uri)
            }
        }

        importLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = result.data?.data ?: return@registerForActivityResult
                handleImport(uri)
            }
        }

        initDatabase()

        setContent {
            val themeMode by ThemeManager.themeMode.collectAsState()
            val themeColor by ThemeManager.themeColor.collectAsState()

            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            CorgiMemoTheme(
                darkTheme = darkTheme,
                themeColor = themeColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OnboardingRouter(
                        corgiPreferences = corgiPreferences,
                        pendingNavigation = pendingNavigation,
                        onExportClick = { format ->
                            BackupFileHandler.setPendingExportFormat(format)
                            val intent = BackupFileHandler.getExportIntent(format)
                            exportLauncher?.launch(intent)
                        },
                        onImportClick = {
                            val intent = BackupFileHandler.getImportIntent()
                            importLauncher?.launch(intent)
                        }
                    )
                }
            }
        }
    }

    /**
     * 处理导出操作
     */
    private fun handleExport(format: BackupManager.ExportFormat, uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = BackupManager.exportData(
                context = this@MainActivity,
                uri = uri,
                format = format
            )
            withContext(Dispatchers.Main) {
                when (result) {
                    is BackupManager.ExportResult.Success -> {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "导出成功！",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    is BackupManager.ExportResult.Error -> {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "导出失败：${result.message}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    /**
     * 处理导入操作
     */
    private fun handleImport(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = BackupManager.restoreData(
                context = this@MainActivity,
                uri = uri
            )
            withContext(Dispatchers.Main) {
                when (result) {
                    is BackupManager.RestoreResult.Success -> {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "恢复成功！已恢复 ${result.todoCount} 个待办",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    is BackupManager.RestoreResult.Error -> {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "恢复失败：${result.message}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    BackupManager.RestoreResult.WrongPassword -> {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "密码错误",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    BackupManager.RestoreResult.VersionIncompatible -> {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "备份文件版本过高，请升级应用",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        pendingNavigation = intent?.let { parseNavigationIntent(it) }
    }

    private fun initDatabase() {
    }

    private fun parseNavigationIntent(intent: Intent): NavigationTarget? {
        val navigateTo = intent.getStringExtra(NAVIGATE_TO) ?: return null
        return when (navigateTo) {
            VALUE_CREATE_TODO -> NavigationTarget.CreateTodo
            VALUE_HOME -> NavigationTarget.Home
            VALUE_EDIT_TODO -> {
                val todoId = intent.getLongExtra(EXTRA_TODO_ID, -1)
                if (todoId > 0) {
                    NavigationTarget.EditTodo(todoId)
                } else {
                    null
                }
            }
            VALUE_BACKUP_HISTORY -> NavigationTarget.BackupHistory
            else -> null
        }
    }
}

sealed class NavigationTarget {
    object Home : NavigationTarget()
    object CreateTodo : NavigationTarget()
    data class EditTodo(val todoId: Long) : NavigationTarget()
    object BackupHistory : NavigationTarget()
}

/**
 * 引导路由组件
 *
 * 读取 isOnboardingCompleted 标志，决定起始页面
 *
 * @param corgiPreferences 偏好设置管理器
 * @param pendingNavigation 待执行的导航目标（来自小部件点击）
 * @param onExportClick 导出点击回调
 * @param onImportClick 导入点击回调
 */
@Composable
private fun OnboardingRouter(
    corgiPreferences: CorgiPreferences,
    pendingNavigation: NavigationTarget?,
    onExportClick: (BackupManager.ExportFormat) -> Unit = {},
    onImportClick: () -> Unit = {}
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
            startDestination = destination,
            onExportClick = onExportClick,
            onImportClick = onImportClick
        )

        LaunchedEffect(pendingNavigation) {
            when (pendingNavigation) {
                NavigationTarget.Home -> {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
                NavigationTarget.CreateTodo -> {
                    navController.navigate(Screen.TodoEdit.route)
                }
                is NavigationTarget.EditTodo -> {
                    navController.navigate(Screen.TodoEditWithId.withArgs(pendingNavigation.todoId.toString()))
                }
                NavigationTarget.BackupHistory -> {
                    navController.navigate(Screen.BackupHistory.route)
                }
                null -> { }
            }
        }
    }
}
