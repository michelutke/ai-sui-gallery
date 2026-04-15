package com.appswithlove.ai.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute : NavKey

@Serializable
data class ModelListRoute(val taskId: String) : NavKey

@Serializable
data class ModelRoute(val taskId: String, val modelName: String) : NavKey

@Serializable
data class BenchmarkRoute(val modelName: String) : NavKey

@Serializable
data object ModelManagerRoute : NavKey

@Serializable
data class ArticleDetailRoute(val articleId: Int) : NavKey
