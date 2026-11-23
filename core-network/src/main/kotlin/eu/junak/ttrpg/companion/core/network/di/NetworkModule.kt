package eu.junak.ttrpg.companion.core.network.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.junak.ttrpg.companion.core.network.maps.MapStore
import eu.junak.ttrpg.companion.core.network.session.CachedSessionVault
import eu.junak.ttrpg.companion.core.network.session.EncryptedSessionVault
import eu.junak.ttrpg.companion.core.network.session.SessionVault
import eu.junak.ttrpg.companion.core.network.session.SiteCookieJar
import eu.junak.ttrpg.companion.core.network.site.SiteStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Qualifier
import javax.inject.Singleton

/** The scope that lives as long as the app, for work a screen being left must not cancel, such as writes. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * One OkHttp client for every site: REST, images and (with a longer read
 * timeout) the event stream all share its connection pool and cookie jar.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(cookies: SiteCookieJar): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookies)
        // Request and response lines only: never headers, so cookies stay out of logs.
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** The host sends a heartbeat every 20 seconds; a silent minute means the stream is gone. */
    @Provides
    @Singleton
    @Named("events")
    fun provideEventClient(client: OkHttpClient): OkHttpClient =
        client.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {
    /** The Keystore-encrypted cookies, decrypted once per origin and process. */
    @Provides
    @Singleton
    fun provideSessionVault(vault: EncryptedSessionVault): SessionVault = CachedSessionVault(vault)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MapModule {
    @Binds
    abstract fun bindMapStore(store: SiteStore): MapStore
}
