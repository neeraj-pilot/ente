package io.ente.entegram.features.onboarding

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.ente.entegram.ui.design.Motion
import kotlinx.serialization.Serializable

@Serializable
internal object WelcomeRoute

@Serializable
internal object SignupEmailRoute

@Serializable
internal object SignupOttRoute

@Serializable
internal object SignupPasswordRoute

@Serializable
internal object WallNameRoute

@Serializable
internal object RecoveryKeyRoute

@Serializable
internal object SignInEmailRoute

@Serializable
internal object SignInOttRoute

@Serializable
internal object SignInPasswordRoute

@Serializable
internal object RecoveryOttRoute

@Serializable
internal object RecoveryKeyEntryRoute

@Serializable
internal object ResetPasswordRoute

@Composable
fun OnboardingFlow(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = WelcomeRoute,
        modifier = modifier,
    ) {
        composable<WelcomeRoute> {
            WelcomeScreen(
                onCreateAccount = {
                    viewModel.clearSignupState()
                    navController.navigate(SignupEmailRoute)
                },
                onSignIn = {
                    viewModel.clearLoginState()
                    navController.navigate(SignInEmailRoute)
                },
            )
        }

        composable<SignupEmailRoute>(
            exitTransition = forwardExit(),
            popEnterTransition = backwardEnter(),
        ) {
            EmailEntryScreen(
                email = state.email,
                isLoading = state.isLoading,
                error = state.error,
                onEmailChange = viewModel::updateEmail,
                onContinue = {
                    navController.navigate(SignupPasswordRoute)
                },
                isEmailValid = viewModel.isEmailValid(),
            )
        }

        composable<SignupOttRoute>(
            enterTransition = forwardEnter(),
            exitTransition = forwardExit(),
            popExitTransition = backwardExit(),
            popEnterTransition = backwardEnter(),
        ) {
            OTTVerificationScreen(
                title = "Check your email",
                subtitle = "We sent a 6-digit code to ${state.email}",
                verifyLabel = "Verify",
                currentStep = 1,
                totalSteps = 5,
                code = state.signupOttCode,
                email = state.email,
                isLoading = state.isLoading,
                error = state.error,
                resendCooldown = state.resendCooldown,
                onCodeChange = viewModel::updateSignupOttCode,
                onVerify = {
                    viewModel.completeSignupAuth {
                        navController.navigate(WallNameRoute)
                    }
                },
                onResend = viewModel::resendSignupOtt,
                onBack = { navController.popBackStack() },
            )
        }

        composable<SignupPasswordRoute>(
            enterTransition = forwardEnter(),
            exitTransition = forwardExit(),
            popExitTransition = backwardExit(),
            popEnterTransition = backwardEnter(),
        ) {
            PasswordSetupScreen(
                password = state.signupPassword,
                confirmPassword = state.signupConfirmPassword,
                strength = viewModel.signupPasswordStrength(),
                passwordsMatch = viewModel.signupPasswordsMatch(),
                isValid = viewModel.isSignupPasswordValid(),
                error = state.error,
                onPasswordChange = viewModel::updateSignupPassword,
                onConfirmPasswordChange = viewModel::updateSignupConfirmPassword,
                onContinue = {
                    viewModel.sendSignupOtt {
                        navController.navigate(SignupOttRoute)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<WallNameRoute>(
            enterTransition = forwardEnter(),
            exitTransition = forwardExit(),
            popExitTransition = backwardExit(),
            popEnterTransition = backwardEnter(),
        ) {
            WallNameScreen(
                wallName = state.wallName,
                availability = state.wallNameAvailability,
                isLoading = state.isLoading,
                error = state.error,
                isValid = viewModel.isWallNameValid(),
                onWallNameChange = viewModel::updateWallName,
                onContinue = {
                    viewModel.createFirstWall {
                        navController.navigate(RecoveryKeyRoute) {
                            popUpTo(SignupEmailRoute) { inclusive = true }
                        }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<RecoveryKeyRoute>(
            enterTransition = forwardEnter(),
        ) {
            RecoveryKeyScreen(
                recoveryKey = state.generatedRecoveryKey,
                onDone = onComplete,
            )
        }

        composable<SignInEmailRoute>(
            exitTransition = forwardExit(),
            popEnterTransition = backwardEnter(),
        ) {
            SignInEmailScreen(
                email = state.email,
                isLoading = state.isLoading,
                error = state.error,
                onEmailChange = viewModel::updateEmail,
                onContinue = {
                    viewModel.beginLogin(
                        onPasswordOnly = { navController.navigate(SignInPasswordRoute) },
                        onEmailOtt = { navController.navigate(SignInOttRoute) },
                        onSignup = { },
                    )
                },
                onCreateAccount = {
                    viewModel.clearSignupState()
                    navController.navigate(SignupEmailRoute)
                },
                onBack = { navController.popBackStack() },
                isEmailValid = viewModel.isEmailValid(),
            )
        }

        composable<SignInOttRoute>(
            enterTransition = forwardEnter(),
            exitTransition = forwardExit(),
            popExitTransition = backwardExit(),
            popEnterTransition = backwardEnter(),
        ) {
            OTTVerificationScreen(
                title = "Verify your email",
                subtitle = "We sent a 6-digit code to ${state.email}",
                verifyLabel = "Continue",
                currentStep = 0,
                totalSteps = 1,
                showProgress = false,
                code = state.loginOttCode,
                email = state.email,
                isLoading = state.isLoading,
                error = state.error,
                resendCooldown = state.resendCooldown,
                onCodeChange = viewModel::updateLoginOttCode,
                onVerify = {
                    viewModel.verifyLoginOtt {
                        navController.navigate(SignInPasswordRoute)
                    }
                },
                onResend = viewModel::resendLoginOtt,
                onBack = { navController.popBackStack() },
            )
        }

        composable<SignInPasswordRoute>(
            enterTransition = forwardEnter(),
            popExitTransition = backwardExit(),
            popEnterTransition = backwardEnter(),
        ) {
            AuthPasswordScreen(
                title = "Enter your password",
                subtitle = state.email,
                password = state.loginPassword,
                error = state.error,
                isLoading = state.isLoading,
                actionLabel = "Sign in",
                secondaryActionLabel = "Forgot password? Use your recovery key",
                onPasswordChange = viewModel::updateLoginPassword,
                isValid = viewModel.canSubmitLoginPassword(),
                onSubmit = {
                    viewModel.login(onSuccess = onComplete)
                },
                onSecondaryAction = {
                    viewModel.sendRecoveryOtt {
                        navController.navigate(RecoveryOttRoute)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<RecoveryOttRoute>(
            enterTransition = forwardEnter(),
            exitTransition = forwardExit(),
            popExitTransition = backwardExit(),
            popEnterTransition = backwardEnter(),
        ) {
            OTTVerificationScreen(
                title = "Verify to reset",
                subtitle = "We sent a 6-digit code to ${state.email}",
                verifyLabel = "Continue",
                currentStep = 0,
                totalSteps = 1,
                showProgress = false,
                code = state.recoveryOttCode,
                email = state.email,
                isLoading = state.isLoading,
                error = state.error,
                resendCooldown = state.resendCooldown,
                onCodeChange = viewModel::updateRecoveryOttCode,
                onVerify = {
                    viewModel.verifyRecoveryOtt {
                        navController.navigate(RecoveryKeyEntryRoute)
                    }
                },
                onResend = viewModel::resendRecoveryOtt,
                onBack = { navController.popBackStack() },
            )
        }

        composable<RecoveryKeyEntryRoute>(
            enterTransition = forwardEnter(),
            exitTransition = forwardExit(),
            popExitTransition = backwardExit(),
            popEnterTransition = backwardEnter(),
        ) {
            RecoveryKeyEntryScreen(
                email = state.email,
                recoveryKey = state.enteredRecoveryKey,
                error = state.error,
                isLoading = state.isLoading,
                onRecoveryKeyChange = viewModel::updateEnteredRecoveryKey,
                onContinue = {
                    navController.navigate(ResetPasswordRoute)
                },
                onBack = { navController.popBackStack() },
                canContinue = viewModel.canContinueRecoveryKey(),
            )
        }

        composable<ResetPasswordRoute>(
            enterTransition = forwardEnter(),
            popExitTransition = backwardExit(),
            popEnterTransition = backwardEnter(),
        ) {
            AuthPasswordScreen(
                title = "Set a new password",
                subtitle = "Choose a strong password for ${state.email}",
                password = state.resetPassword,
                confirmPassword = state.resetConfirmPassword,
                error = state.error,
                isLoading = state.isLoading,
                actionLabel = "Reset password",
                onPasswordChange = viewModel::updateResetPassword,
                onConfirmPasswordChange = viewModel::updateResetConfirmPassword,
                strength = viewModel.resetPasswordStrength(),
                passwordsMatch = viewModel.resetPasswordsMatch(),
                isValid = viewModel.isResetPasswordValid(),
                onSubmit = {
                    viewModel.recoverAccount(onSuccess = onComplete)
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun forwardEnter():
    AnimatedContentTransitionScope<NavBackStackEntry>.() -> androidx.compose.animation.EnterTransition? = {
    slideInHorizontally(
        animationSpec = Motion.soft(),
        initialOffsetX = { fullWidth -> (fullWidth * 0.25f).toInt() },
    ) + fadeIn(Motion.quickFade())
}

private fun forwardExit():
    AnimatedContentTransitionScope<NavBackStackEntry>.() -> androidx.compose.animation.ExitTransition? = {
    slideOutHorizontally(
        animationSpec = Motion.soft(),
        targetOffsetX = { fullWidth -> -(fullWidth * 0.25f).toInt() },
    ) + fadeOut(Motion.quickFade())
}

private fun backwardEnter():
    AnimatedContentTransitionScope<NavBackStackEntry>.() -> androidx.compose.animation.EnterTransition? = {
    slideInHorizontally(
        animationSpec = Motion.soft(),
        initialOffsetX = { fullWidth -> -(fullWidth * 0.25f).toInt() },
    ) + fadeIn(Motion.quickFade())
}

private fun backwardExit():
    AnimatedContentTransitionScope<NavBackStackEntry>.() -> androidx.compose.animation.ExitTransition? = {
    slideOutHorizontally(
        animationSpec = Motion.soft(),
        targetOffsetX = { fullWidth -> (fullWidth * 0.25f).toInt() },
    ) + fadeOut(Motion.quickFade())
}
