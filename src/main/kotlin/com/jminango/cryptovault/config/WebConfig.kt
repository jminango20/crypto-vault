package com.jminango.cryptovault.config

import com.jminango.cryptovault.interceptor.RateLimitInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val rateLimitInterceptor: RateLimitInterceptor
) : WebMvcConfigurer {

    /**
     * Registra interceptor de rate limiting
     * Aplica rate limit em todos endpoints /api/v1/
     * exceto health check e autenticação
     */
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(rateLimitInterceptor)
            .addPathPatterns("/api/v1/**")        // Aplicar em todos endpoints /api/v1
            .excludePathPatterns(
                "/api/v1/health",
                "/api/v1/auth/**"
            )
    }
}