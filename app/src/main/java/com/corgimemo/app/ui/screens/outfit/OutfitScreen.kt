package com.corgimemo.app.ui.screens.outfit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

/**
 * 装扮详情页（占位版本）
 *
 * 此文件为 Phase 1 的最小可编译占位，让 AppNavHost 引用能够通过编译。
 * Phase 3（Task 8）会用完整的装扮模块实现替换本文件内容：
 * - 装扮推荐横幅
 * - 柯基动画预览区（含预览模式提示）
 * - 装扮横滑列表
 * - 预览模式操作栏（取消/应用）
 *
 * @param navController 导航控制器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitScreen(
    navController: NavController
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "🎩 装扮") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // TODO: Phase 3 Task 8 用完整装扮模块替换此占位
            Text(text = "装扮详情页（开发中）")
        }
    }
}
