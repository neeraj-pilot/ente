package io.ente.entegram.app

import io.ente.entegram.core.services.AuthClient
import io.ente.entegram.core.services.AuthSessionStore
import io.ente.entegram.core.services.PersistingAuthClient
import io.ente.entegram.core.services.RustAccountAuthClient
import io.ente.entegram.core.services.RustWallClient
import io.ente.entegram.core.services.WallClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Application-wide state holder.
 * Both auth and wall data now flow through the shared Rust crates.
 */
class AppState(
    val wallClient: WallClient,
)

@Module
@InstallIn(SingletonComponent::class)
object AppStateModule {

    @Provides
    @Singleton
    fun provideWallClient(rustWallClient: RustWallClient): WallClient = rustWallClient

    @Provides
    @Singleton
    fun provideAuthClient(
        rustAccountAuthClient: RustAccountAuthClient,
        authSessionStore: AuthSessionStore,
    ): AuthClient = PersistingAuthClient(
        delegate = rustAccountAuthClient,
        authSessionStore = authSessionStore,
    )

    @Provides
    @Singleton
    fun provideAppState(wallClient: WallClient): AppState = AppState(wallClient = wallClient)
}
