package com.corgimemo.app.ui.screens.inspiration

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.data.model.InspirationRelation
import com.corgimemo.app.ui.screens.inspiration.components.ImagePicker
import com.corgimemo.app.ui.screens.inspiration.components.RelationSelector
import com.corgimemo.app.ui.screens.inspiration.components.TagInputField
import com.corgimemo.app.viewmodel.InspirationViewModel

/**
 * 灵感记录编辑页面（Phase 4）
 * 用于新建或编辑灵感记录，包含标题、内容、标签、图片和关联功能
 *
 * 功能说明：
 * - 顶部导航栏：返回按钮 + 保存按钮（标题根据模式显示）
 * - 表单区域：可滚动的表单，包含多个输入组件
 * - 支持新建模式（无ID）和编辑模式（传入灵感ID）
 *
 * @param navController 导航控制器，用于页面跳转和返回
 * @param inspirationId 灵感ID（可选，null表示新建模式）
 * @param viewModel 灵感视图模型（通过 Hilt 自动注入）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspirationEditScreen(
    navController: NavController,
    inspirationId: Long? = null,
    viewModel: InspirationViewModel = hiltViewModel()
) {
    /** 标题输入状态 */
    var title by remember { mutableStateOf("") }
    /** 内容输入状态 */
    var content by remember { mutableStateOf("") }
    /** 标签列表状态 */
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    /** 图片路径列表状态 */
    var imagePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    /** 关联列表状态 */
    var relations by remember { mutableStateOf<List<InspirationRelation>>(emptyList()) }

    /** 判断是否为编辑模式 */
    val isEditMode = inspirationId != null && inspirationId > 0

    /**
     * 如果是编辑模式，加载现有灵感数据到表单中
     * 使用 LaunchedEffect 在首次组合时执行一次性加载
     */
    if (isEditMode) {
        LaunchedEffect(inspirationId) {
            viewModel.setEditingInspiration(null)
            inspirationId?.let { id ->
                val existing = viewModel.getInspirationById(id)
                if (existing != null) {
                    viewModel.setEditingInspiration(existing)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            /**
             * 顶部应用栏
             * 左侧：返回按钮
             * 右侧：保存按钮
             * 标题：根据模式动态显示"新建灵感"或"编辑灵感"
             */
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "编辑灵感" else "新建灵感",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    /**
                     * 返回按钮：点击后返回上一级页面
                     */
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    /**
                     * 保存按钮：验证并保存灵感数据
                     */
                    IconButton(
                        onClick = {
                            /** 验证标题是否为空 */
                            if (title.isBlank()) return@IconButton

                            if (isEditMode && inspirationId != null) {
                                /** 编辑模式：调用更新方法 */
                                val updatedInspiration = Inspiration(
                                    id = inspirationId,
                                    title = title,
                                    content = content,
                                    tags = viewModel.encodeTags(tags),
                                    imagePaths = viewModel.encodePaths(imagePaths),
                                    createdAt = System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis()
                                )
                                viewModel.updateInspiration(updatedInspiration)
                            } else {
                                /** 新建模式：调用创建方法 */
                                viewModel.createInspiration(
                                    title = title,
                                    content = content,
                                    tags = tags,
                                    imagePaths = imagePaths
                                )
                            }

                            /** 保存成功后自动返回上一级页面 */
                            navController.popBackStack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "保存"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        /**
         * 可滚动表单区域
         * 使用 verticalScroll 实现垂直滚动，防止内容过长时溢出屏幕
         */
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            /**
             * 标题输入框
             * 单行文本输入，用于输入灵感标题（必填项）
             */
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题") },
                placeholder = { Text("请输入灵感标题...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            /**
             * 内容输入区
             * 多行文本输入，最小高度200dp，用于输入富文本内容
             */
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("内容") },
                placeholder = { Text("记录你的灵感想法...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                minLines = 5,
                maxLines = 10,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            /**
             * 标签输入组件
             * 用于添加和管理灵感标签
             */
            TagInputField(
                tags = tags,
                onTagsChange = { newTags -> tags = newTags },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            /**
             * 图片选择器组件
             * 用于选择和管理灵感关联的图片
             */
            ImagePicker(
                imagePaths = imagePaths,
                onImagesChange = { newPaths -> imagePaths = newPaths },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            /**
             * 关联选择器组件
             * 用于管理灵感与其他卡片（待办/日期/其他灵感）的关联关系
             */
            RelationSelector(
                relations = relations,
                onRelationAdd = { targetType, targetId ->
                    /** 添加新关联关系 */
                    if (inspirationId != null) {
                        viewModel.addRelation(inspirationId, targetType, targetId)
                    }
                },
                onRelationDelete = { relationId ->
                    /** 删除关联关系 */
                    viewModel.deleteRelation(relationId)
                },
                modifier = Modifier.fillMaxWidth()
            )

            /** 底部留白，防止内容被遮挡 */
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
