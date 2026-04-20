package io.ente.entegram.features.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ente.entegram.core.logging.AppLogger
import io.ente.entegram.core.services.AuthClient
import io.ente.entegram.core.services.AuthOttPurpose
import io.ente.entegram.core.services.LoginFlowDecision
import io.ente.entegram.core.services.WallClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val email: String = "",
    val signupOttCode: String = "",
    val loginOttCode: String = "",
    val recoveryOttCode: String = "",
    val signupPassword: String = "",
    val signupConfirmPassword: String = "",
    val loginPassword: String = "",
    val wallName: String = "",
    val generatedRecoveryKey: String = "",
    val enteredRecoveryKey: String = "",
    val resetPassword: String = "",
    val resetConfirmPassword: String = "",
    val loginFlow: LoginFlowDecision = LoginFlowDecision.PasswordOnly,
    val isLoading: Boolean = false,
    val error: String? = null,
    val resendCooldown: Int = 0,
    val wallNameAvailability: WallNameAvailability = WallNameAvailability.Idle,
)

enum class WallNameAvailability {
    Idle,
    Checking,
    Available,
    Taken,
    TooShort,
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authClient: AuthClient,
    private val wallClient: WallClient,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private var resendJob: Job? = null
    private var slugCheckJob: Job? = null

    fun updateEmail(email: String) {
        _state.update { it.copy(email = email, error = null) }
    }

    fun isEmailValid(): Boolean {
        val e = _state.value.email.trim()
        return e.contains("@") && e.contains(".")
    }

    fun clearLoginState() {
        _state.update {
            it.copy(
                loginOttCode = "",
                recoveryOttCode = "",
                loginPassword = "",
                enteredRecoveryKey = "",
                resetPassword = "",
                resetConfirmPassword = "",
                error = null,
                loginFlow = LoginFlowDecision.PasswordOnly,
            )
        }
    }

    fun clearSignupState() {
        _state.update {
            it.copy(
                signupOttCode = "",
                signupPassword = "",
                signupConfirmPassword = "",
                wallName = "",
                generatedRecoveryKey = "",
                wallNameAvailability = WallNameAvailability.Idle,
                error = null,
            )
        }
    }

    fun sendSignupOtt(onSuccess: () -> Unit) {
        sendOtt(AuthOttPurpose.Signup, onSuccess)
    }

    fun beginLogin(
        onPasswordOnly: () -> Unit,
        onEmailOtt: () -> Unit,
        onSignup: () -> Unit,
    ) {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val flow = authClient.loginPreflight(_state.value.email.trim())
                _state.update { it.copy(loginFlow = flow) }
                AppLogger.i("Onboarding", "login preflight completed with $flow")
                when (flow) {
                    LoginFlowDecision.PasswordOnly -> {
                        _state.update { it.copy(isLoading = false) }
                        onPasswordOnly()
                    }
                    LoginFlowDecision.EmailOttAndPassword -> {
                        authClient.sendOtt(_state.value.email.trim(), AuthOttPurpose.Login)
                        _state.update { it.copy(isLoading = false) }
                        startResendCooldown()
                        onEmailOtt()
                    }
                    LoginFlowDecision.Signup -> {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = "No account found for this email. Create a new account instead.",
                            )
                        }
                        onSignup()
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("Onboarding", "login preflight failed", e)
                _state.update {
                    it.copy(isLoading = false, error = e.message ?: "Couldn't continue sign in")
                }
            }
        }
    }

    fun sendRecoveryOtt(onSuccess: () -> Unit) {
        sendOtt(AuthOttPurpose.Recovery, onSuccess)
    }

    private fun sendOtt(
        purpose: AuthOttPurpose,
        onSuccess: () -> Unit,
    ) {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                authClient.sendOtt(_state.value.email.trim(), purpose)
                _state.update { it.copy(isLoading = false) }
                AppLogger.i("Onboarding", "sent ${purpose.name.lowercase()} verification code")
                startResendCooldown()
                onSuccess()
            } catch (e: Exception) {
                AppLogger.w("Onboarding", "failed to send ${purpose.name.lowercase()} code", e)
                _state.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to send code")
                }
            }
        }
    }

    fun resendSignupOtt() {
        resendOtt(AuthOttPurpose.Signup)
    }

    fun resendLoginOtt() {
        resendOtt(AuthOttPurpose.Login)
    }

    fun resendRecoveryOtt() {
        resendOtt(AuthOttPurpose.Recovery)
    }

    private fun resendOtt(purpose: AuthOttPurpose) {
        if (_state.value.resendCooldown > 0 || _state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                authClient.sendOtt(_state.value.email.trim(), purpose)
                _state.update { it.copy(isLoading = false) }
                AppLogger.i("Onboarding", "resent ${purpose.name.lowercase()} verification code")
                startResendCooldown()
            } catch (e: Exception) {
                AppLogger.w("Onboarding", "failed to resend ${purpose.name.lowercase()} code", e)
                _state.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to resend code")
                }
            }
        }
    }

    private fun startResendCooldown() {
        resendJob?.cancel()
        _state.update { it.copy(resendCooldown = 30) }
        resendJob = viewModelScope.launch {
            for (i in 29 downTo 0) {
                delay(1000)
                _state.update { it.copy(resendCooldown = i) }
            }
        }
    }

    fun updateSignupOttCode(code: String) {
        _state.update { it.copy(signupOttCode = sanitizeOtp(code), error = null) }
    }

    fun updateLoginOttCode(code: String) {
        _state.update { it.copy(loginOttCode = sanitizeOtp(code), error = null) }
    }

    fun updateRecoveryOttCode(code: String) {
        _state.update { it.copy(recoveryOttCode = sanitizeOtp(code), error = null) }
    }

    fun verifySignupOtt(onSuccess: () -> Unit) {
        if (_state.value.signupOttCode.length == 6) {
            _state.update { it.copy(error = null) }
            onSuccess()
        }
    }

    fun verifyLoginOtt(onSuccess: () -> Unit) {
        if (_state.value.loginOttCode.length == 6) {
            _state.update { it.copy(error = null) }
            onSuccess()
        }
    }

    fun verifyRecoveryOtt(onSuccess: () -> Unit) {
        if (_state.value.recoveryOttCode.length == 6) {
            _state.update { it.copy(error = null) }
            onSuccess()
        }
    }

    fun updateSignupPassword(password: String) {
        _state.update { it.copy(signupPassword = password, error = null) }
    }

    fun updateSignupConfirmPassword(password: String) {
        _state.update { it.copy(signupConfirmPassword = password, error = null) }
    }

    fun signupPasswordStrength(): Int = strengthFor(_state.value.signupPassword)

    fun isSignupPasswordValid(): Boolean {
        val s = _state.value
        return s.signupPassword.isNotEmpty() &&
            s.signupPassword == s.signupConfirmPassword
    }

    fun signupPasswordsMatch(): Boolean {
        val s = _state.value
        return s.signupConfirmPassword.isEmpty() || s.signupPassword == s.signupConfirmPassword
    }

    fun updateLoginPassword(password: String) {
        _state.update { it.copy(loginPassword = password, error = null) }
    }

    fun canSubmitLoginPassword(): Boolean = _state.value.loginPassword.isNotBlank()

    fun login(onSuccess: () -> Unit) {
        if (_state.value.isLoading || !canSubmitLoginPassword()) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                authClient.login(
                    email = _state.value.email.trim(),
                    password = _state.value.loginPassword,
                    code = when (_state.value.loginFlow) {
                        LoginFlowDecision.EmailOttAndPassword -> _state.value.loginOttCode
                        else -> null
                    },
                )
                _state.update { it.copy(isLoading = false) }
                AppLogger.i("Onboarding", "login completed")
                onSuccess()
            } catch (e: Exception) {
                AppLogger.w("Onboarding", "login failed", e)
                _state.update {
                    it.copy(isLoading = false, error = e.message ?: "Incorrect password")
                }
            }
        }
    }

    fun updateWallName(name: String) {
        val sanitized = name.lowercase()
            .filter { it.isLetterOrDigit() || it == '-' }
            .take(30)
        _state.update {
            it.copy(
                wallName = sanitized,
                error = null,
                wallNameAvailability = WallNameAvailability.Idle,
            )
        }
        checkWallNameAvailability(sanitized)
    }

    private fun checkWallNameAvailability(slug: String) {
        slugCheckJob?.cancel()
        if (slug.length < 3) {
            _state.update { it.copy(wallNameAvailability = WallNameAvailability.TooShort) }
            return
        }
        _state.update { it.copy(wallNameAvailability = WallNameAvailability.Checking) }
        slugCheckJob = viewModelScope.launch {
            delay(500)
            val availability = try {
                if (wallClient.wall(bySlug = slug) == null) {
                    WallNameAvailability.Available
                } else {
                    WallNameAvailability.Taken
                }
            } catch (_: Exception) {
                WallNameAvailability.Idle
            }
            _state.update { state ->
                if (state.wallName == slug) {
                    state.copy(wallNameAvailability = availability)
                } else {
                    state
                }
            }
        }
    }

    fun isWallNameValid(): Boolean {
        return _state.value.wallName.length >= 3 &&
            _state.value.wallNameAvailability == WallNameAvailability.Available
    }

    fun completeSignupAuth(onSuccess: () -> Unit) {
        if (_state.value.isLoading || !isSignupPasswordValid()) return
        if (_state.value.signupOttCode.length != 6) {
            _state.update { it.copy(error = "Enter the 6-digit verification code.") }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                authClient.signup(
                    email = _state.value.email.trim(),
                    password = _state.value.signupPassword,
                    code = _state.value.signupOttCode,
                ).also { result ->
                    val recoveryKey = result.recoveryKey
                        ?: throw IllegalStateException("Signup completed without a recovery key.")
                    _state.update {
                        it.copy(
                            isLoading = false,
                            generatedRecoveryKey = recoveryKey,
                        )
                    }
                }
                AppLogger.i("Onboarding", "signup completed")
                onSuccess()
            } catch (e: Exception) {
                AppLogger.w("Onboarding", "signup failed", e)
                _state.update {
                    it.copy(isLoading = false, error = e.message ?: "Signup failed")
                }
            }
        }
    }

    fun createFirstWall(onSuccess: () -> Unit) {
        if (_state.value.isLoading || !isWallNameValid()) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                wallClient.createWall(
                    slug = _state.value.wallName,
                    displayName = _state.value.wallName,
                    bio = null,
                )
                _state.update { it.copy(isLoading = false) }
                AppLogger.i("Onboarding", "first wall created")
                onSuccess()
            } catch (e: Exception) {
                AppLogger.w("Onboarding", "failed to create first wall", e)
                _state.update {
                    it.copy(isLoading = false, error = e.message ?: "Couldn't create wall")
                }
            }
        }
    }

    fun updateEnteredRecoveryKey(value: String) {
        _state.update { it.copy(enteredRecoveryKey = value, error = null) }
    }

    fun canContinueRecoveryKey(): Boolean = _state.value.enteredRecoveryKey.trim().isNotEmpty()

    fun updateResetPassword(password: String) {
        _state.update { it.copy(resetPassword = password, error = null) }
    }

    fun updateResetConfirmPassword(password: String) {
        _state.update { it.copy(resetConfirmPassword = password, error = null) }
    }

    fun resetPasswordStrength(): Int = strengthFor(_state.value.resetPassword)

    fun isResetPasswordValid(): Boolean {
        val s = _state.value
        return s.resetPassword.isNotEmpty() &&
            s.resetPassword == s.resetConfirmPassword
    }

    fun resetPasswordsMatch(): Boolean {
        val s = _state.value
        return s.resetConfirmPassword.isEmpty() || s.resetPassword == s.resetConfirmPassword
    }

    fun recoverAccount(onSuccess: () -> Unit) {
        if (_state.value.isLoading || !isResetPasswordValid()) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                authClient.recoverAccount(
                    email = _state.value.email.trim(),
                    code = _state.value.recoveryOttCode,
                    recoveryKey = _state.value.enteredRecoveryKey.trim(),
                    newPassword = _state.value.resetPassword,
                )
                _state.update { it.copy(isLoading = false) }
                AppLogger.i("Onboarding", "account recovery completed")
                onSuccess()
            } catch (e: Exception) {
                AppLogger.w("Onboarding", "account recovery failed", e)
                _state.update {
                    it.copy(isLoading = false, error = e.message ?: "Couldn't reset password")
                }
            }
        }
    }

    private fun sanitizeOtp(code: String): String {
        return code.filter { it.isDigit() }.take(6)
    }

    private fun strengthFor(password: String): Int {
        var score = 0
        if (password.length >= 8) score++
        if (password.length >= 12) score++
        if (password.any { it.isLetter() } && password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        return score
    }
}
