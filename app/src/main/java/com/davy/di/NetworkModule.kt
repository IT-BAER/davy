package com.davy.di

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.Interceptor
import android.net.TrafficStats
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module providing network-related dependencies.
 * 
 * Provides:
 * - OkHttpClient with logging interceptor
 * - Retrofit instance
 * - Moshi JSON converter
 * 
 * HTTP logging respects user's debug logging preference:
 * - When debug logging is enabled: Full request/response body logging
 * - When debug logging is disabled: No HTTP logging (reduces overhead)
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }
    
    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(@ApplicationContext context: Context): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message ->
            Timber.tag("OkHttp").d(message)
        }.apply {
            // Check user's debug logging preference
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val debugEnabled = if (prefs.contains("debug_logging")) {
                prefs.getBoolean("debug_logging", false)
            } else {
                // Default to BuildConfig.DEBUG if user hasn't set preference
                com.davy.BuildConfig.DEBUG
            }
            
            level = if (debugEnabled) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        Timber.d("Creating OkHttpClient")
        // Interceptor to tag sockets to avoid StrictMode's UntaggedSocketViolation noise
        val trafficTaggingInterceptor = Interceptor { chain ->
            // Use a stable tag for DAVy network traffic; could be refined per account later
            val previousTag = try { TrafficStats.getThreadStatsTag() } catch (_: Throwable) { 0 }
            try {
                TrafficStats.setThreadStatsTag(0x44415659) // 'DAVY' in hex
            } catch (_: Throwable) { /* no-op on platforms without TrafficStats */ }
            try {
                chain.proceed(chain.request())
            } finally {
                try { TrafficStats.setThreadStatsTag(previousTag) } catch (_: Throwable) { /* ignore */ }
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(trafficTaggingInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        Timber.d("Creating Retrofit")
        // Base URL will be dynamic per account
        // This is a placeholder - actual API services will set their own base URLs
        return Retrofit.Builder()
            .baseUrl("https://placeholder.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
}
