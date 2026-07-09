package com.rivals.skillsim.data.api

import com.google.gson.Gson
import com.rivals.skillsim.BuildConfig
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitProvider {
    /**
     * Base URL is provided per build type via [BuildConfig.API_BASE_URL]:
     * debug -> local dev server (http://10.0.2.2:8080/),
     * release -> 자체 호스팅 백엔드(ngrok 고정 터널 → 로컬 PC Docker).
     */
    val DefaultBaseUrl: String = BuildConfig.API_BASE_URL

    // 로컬 PC + 터널 구성은 콜드스타트가 없지만, 네트워크 지연을 감안해 넉넉히 둔다.
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
            .addInterceptor(ngrokSkipWarningInterceptor)
            .build()

    // ngrok 무료 터널은 브라우저성 요청에 경고 페이지(HTML)를 반환할 수 있다.
    // 이 헤더가 있으면 항상 실제 백엔드 응답을 받는다. 다른 백엔드에서는 무시되는 무해한 헤더다.
    private val ngrokSkipWarningInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("ngrok-skip-browser-warning", "true")
            .build()
        chain.proceed(request)
    }
}
