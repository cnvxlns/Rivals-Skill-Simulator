package com.rivals.skillsim.data.api

import com.google.gson.Gson
import com.rivals.skillsim.BuildConfig
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitProvider {
    /**
     * Base URL is provided per build type via [BuildConfig.API_BASE_URL]:
     * debug -> local dev server (http://10.0.2.2:8080/), release -> Render deployment.
     */
    val DefaultBaseUrl: String = BuildConfig.API_BASE_URL

    // Render free tier can cold-start (~30-45s), so allow generous timeouts.
    private val defaultTimeoutSeconds = 60L

    fun createSkillApi(
        baseUrl: String = DefaultBaseUrl,
        gson: Gson = Gson(),
        client: OkHttpClient = defaultClient(),
    ): SkillApi {
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SkillApi::class.java)
    }

    private fun defaultClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(defaultTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(defaultTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(defaultTimeoutSeconds, TimeUnit.SECONDS)
            .build()
}
