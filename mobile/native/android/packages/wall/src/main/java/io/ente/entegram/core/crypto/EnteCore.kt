package io.ente.entegram.core.crypto

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import uniffi.ente_ffi.FfiAccountLoginFlow
import uniffi.ente_ffi.FfiAccountLoginPreflight
import uniffi.ente_ffi.FfiAccountLoginResult
import uniffi.ente_ffi.accountLogin
import uniffi.ente_ffi.accountLoginPreflight
import uniffi.ente_ffi.accountLoginWithOtt
import uniffi.ente_ffi.accountLogout
import uniffi.ente_ffi.accountRecover
import uniffi.ente_ffi.accountSendOtt
import uniffi.ente_ffi.accountSignup

interface EnteAuthCore {
    fun sendOtt(baseUrl: String, email: String, purpose: String)
    fun loginPreflight(baseUrl: String, email: String): FfiAccountLoginPreflight
    fun signup(
        baseUrl: String,
        email: String,
        ott: String,
        password: String,
    ): FfiAccountLoginResult
    fun login(baseUrl: String, email: String, password: String): FfiAccountLoginResult
    fun loginWithOtt(
        baseUrl: String,
        email: String,
        ott: String,
        password: String,
    ): FfiAccountLoginResult
    fun recover(
        baseUrl: String,
        email: String,
        ott: String,
        recoveryKey: String,
        newPassword: String,
    ): FfiAccountLoginResult
    fun logout(baseUrl: String, authToken: String)
}

@Singleton
class UniFfiEnteAuthCore @Inject constructor() : EnteAuthCore {
    override fun sendOtt(baseUrl: String, email: String, purpose: String) {
        accountSendOtt(baseUrl, email, purpose)
    }

    override fun loginPreflight(baseUrl: String, email: String): FfiAccountLoginPreflight {
        return accountLoginPreflight(baseUrl, email)
    }

    override fun signup(
        baseUrl: String,
        email: String,
        ott: String,
        password: String,
    ): FfiAccountLoginResult {
        return accountSignup(baseUrl, email, ott, password)
    }

    override fun login(baseUrl: String, email: String, password: String): FfiAccountLoginResult {
        return accountLogin(baseUrl, email, password)
    }

    override fun loginWithOtt(
        baseUrl: String,
        email: String,
        ott: String,
        password: String,
    ): FfiAccountLoginResult {
        return accountLoginWithOtt(baseUrl, email, ott, password)
    }

    override fun recover(
        baseUrl: String,
        email: String,
        ott: String,
        recoveryKey: String,
        newPassword: String,
    ): FfiAccountLoginResult {
        return accountRecover(baseUrl, email, ott, recoveryKey, newPassword)
    }

    override fun logout(baseUrl: String, authToken: String) {
        accountLogout(baseUrl, authToken)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EnteAuthCoreModule {
    @Binds
    @Singleton
    abstract fun bindEnteAuthCore(
        impl: UniFfiEnteAuthCore,
    ): EnteAuthCore
}
