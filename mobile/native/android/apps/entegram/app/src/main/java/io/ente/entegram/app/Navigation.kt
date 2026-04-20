package io.ente.entegram.app

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.ente.entegram.features.community.CommunityScreen
import io.ente.entegram.features.compose.PostComposerScreen
import io.ente.entegram.features.feed.FeedScreen
import io.ente.entegram.features.follow.FollowManagementScreen
import io.ente.entegram.features.onboarding.OnboardingFlow
import io.ente.entegram.features.postdetail.PostDetailScreen
import io.ente.entegram.features.settings.SettingsScreen
import io.ente.entegram.features.wall.WallScreen
import io.ente.entegram.ui.components.LoadingStateView
import io.ente.entegram.ui.design.Motion
import kotlinx.serialization.Serializable

@Serializable
object OnboardingRoute

@Serializable
object FeedRoute

@Serializable
data class PostDetailRoute(val postId: Long)

@Serializable
object FollowManagementRoute

@Serializable
object CommunityRoute

@Serializable
data class WallRoute(val slug: String)

@Serializable
object PostComposerRoute

@Serializable
object SettingsRoute

@Composable
fun AppNavigation(
    sessionState: AppSessionUiState,
    onAuthComplete: () -> Unit,
    onSignOut: () -> Unit,
) {
    when (sessionState) {
        AppSessionUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                LoadingStateView(modifier = Modifier.fillMaxSize())
            }
        }

        AppSessionUiState.SignedOut -> {
            OnboardingNavigation(onComplete = onAuthComplete)
        }

        is AppSessionUiState.Authenticated -> {
            SignedInNavigation(onSignOut = onSignOut)
        }
    }
}

@Composable
private fun OnboardingNavigation(
    onComplete: () -> Unit,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = OnboardingRoute,
    ) {
        composable<OnboardingRoute>(
            exitTransition = {
                fadeOut(Motion.quickFade())
            },
        ) {
            OnboardingFlow(onComplete = onComplete)
        }
    }
}

@Composable
private fun SignedInNavigation(
    onSignOut: () -> Unit,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = FeedRoute,
    ) {
        composable<FeedRoute>(
            exitTransition = {
                fadeOut(Motion.quickFade())
            },
            popEnterTransition = {
                fadeIn(Motion.quickFade())
            },
        ) {
            FeedScreen(
                onPostTap = { postId ->
                    navController.navigate(PostDetailRoute(postId))
                },
                onCommunityTap = {
                    navController.navigate(CommunityRoute)
                },
                onWallTap = { slug ->
                    navController.navigate(WallRoute(slug))
                },
                onComposeTap = {
                    navController.navigate(PostComposerRoute)
                },
                onProfileTap = {
                    navController.navigate(SettingsRoute)
                },
                onConnectionsTap = {
                    navController.navigate(FollowManagementRoute)
                },
                onSignOut = onSignOut,
            )
        }
        composable<PostDetailRoute>(
            enterTransition = {
                slideInHorizontally(
                    animationSpec = Motion.soft(),
                    initialOffsetX = { fullWidth -> (fullWidth * 0.25f).toInt() },
                ) + fadeIn(Motion.quickFade())
            },
            popExitTransition = {
                slideOutHorizontally(
                    animationSpec = Motion.soft(),
                    targetOffsetX = { fullWidth -> (fullWidth * 0.25f).toInt() },
                ) + fadeOut(Motion.quickFade())
            },
        ) {
            PostDetailScreen(
                onBack = { navController.popBackStack() },
                onWallTap = { slug ->
                    navController.navigate(WallRoute(slug))
                },
            )
        }
        composable<FollowManagementRoute>(
            enterTransition = {
                slideInHorizontally(
                    animationSpec = Motion.soft(),
                    initialOffsetX = { fullWidth -> (fullWidth * 0.25f).toInt() },
                ) + fadeIn(Motion.quickFade())
            },
            popExitTransition = {
                slideOutHorizontally(
                    animationSpec = Motion.soft(),
                    targetOffsetX = { fullWidth -> (fullWidth * 0.25f).toInt() },
                ) + fadeOut(Motion.quickFade())
            },
        ) {
            FollowManagementScreen(
                onBack = { navController.popBackStack() },
                onWallTap = { slug ->
                    navController.navigate(WallRoute(slug))
                },
            )
        }
        composable<CommunityRoute>(
            enterTransition = {
                slideInHorizontally(
                    animationSpec = Motion.soft(),
                    initialOffsetX = { fullWidth -> (fullWidth * 0.25f).toInt() },
                ) + fadeIn(Motion.quickFade())
            },
            popExitTransition = {
                slideOutHorizontally(
                    animationSpec = Motion.soft(),
                    targetOffsetX = { fullWidth -> (fullWidth * 0.25f).toInt() },
                ) + fadeOut(Motion.quickFade())
            },
        ) {
            CommunityScreen(
                onBack = { navController.popBackStack() },
                onWallTap = { slug ->
                    navController.navigate(WallRoute(slug))
                },
            )
        }
        composable<WallRoute>(
            enterTransition = {
                slideInHorizontally(
                    animationSpec = Motion.soft(),
                    initialOffsetX = { fullWidth -> (fullWidth * 0.25f).toInt() },
                ) + fadeIn(Motion.quickFade())
            },
            popExitTransition = {
                slideOutHorizontally(
                    animationSpec = Motion.soft(),
                    targetOffsetX = { fullWidth -> (fullWidth * 0.25f).toInt() },
                ) + fadeOut(Motion.quickFade())
            },
        ) {
            WallScreen(
                onBack = { navController.popBackStack() },
                onPostTap = { postId ->
                    navController.navigate(PostDetailRoute(postId))
                },
            )
        }
        composable<PostComposerRoute>(
            enterTransition = {
                slideInVertically(
                    animationSpec = Motion.soft(),
                    initialOffsetY = { fullHeight -> (fullHeight * 0.15f).toInt() },
                ) + fadeIn(Motion.quickFade())
            },
            popExitTransition = {
                slideOutVertically(
                    animationSpec = Motion.soft(),
                    targetOffsetY = { fullHeight -> (fullHeight * 0.15f).toInt() },
                ) + fadeOut(Motion.quickFade())
            },
        ) {
            PostComposerScreen(
                onBack = { navController.popBackStack() },
                onPostCreated = {
                    navController.popBackStack(FeedRoute, inclusive = false)
                },
            )
        }
        composable<SettingsRoute>(
            enterTransition = {
                slideInHorizontally(
                    animationSpec = Motion.soft(),
                    initialOffsetX = { fullWidth -> (fullWidth * 0.25f).toInt() },
                ) + fadeIn(Motion.quickFade())
            },
            popExitTransition = {
                slideOutHorizontally(
                    animationSpec = Motion.soft(),
                    targetOffsetX = { fullWidth -> (fullWidth * 0.25f).toInt() },
                ) + fadeOut(Motion.quickFade())
            },
        ) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onPostTap = { postId ->
                    navController.navigate(PostDetailRoute(postId))
                },
            )
        }
    }
}
