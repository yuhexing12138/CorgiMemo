package com.corgimemo.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.corgimemo.app.ui.screens.home.HomeScreen
import com.corgimemo.app.ui.screens.profile.ProfileScreen
import com.corgimemo.app.ui.screens.todo.TodoScreen

/**
 * 导航路由定义
 * 定义应用中所有页面的路由路径
 */
object Routes {
    const val HOME = "home"
    const val TODO = "todo"
    const val PROFILE = "profile"
    const val TODO_EDIT = "todo/{id}"
}

/**
 * 应用主导航组件
 * 
 * 负责管理应用的页面导航和路由
 */
@Composable
fun CorgiMemoApp() {
    // 创建导航控制器
    val navController = rememberNavController()
    
    // 导航宿主 - 管理所有页面的切换
    NavHost(
        navController = navController,
        startDestination = Routes.HOME  // 默认显示首页
    ) {
        // 首页路由
        composable(Routes.HOME) {
            HomeScreen(
                onAddTodo = { navController.navigate(Routes.TODO) },
                onEditTodo = { id -> navController.navigate("${Routes.TODO}/$id") },
                onProfile = { navController.navigate(Routes.PROFILE) }
            )
        }
        
        // 待办创建/编辑页面路由
        composable(Routes.TODO) {
            TodoScreen(
                onBack = { navController.popBackStack() },
                onSave = { navController.popBackStack() }
            )
        }
        
        // 待办编辑页面路由（带参数）
        composable(Routes.TODO_EDIT) { backStackEntry ->
            val todoId = backStackEntry.arguments?.getString("id")
            TodoScreen(
                todoId = todoId,
                onBack = { navController.popBackStack() },
                onSave = { navController.popBackStack() }
            )
        }
        
        // 个人页面路由
        composable(Routes.PROFILE) {
            ProfileScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}