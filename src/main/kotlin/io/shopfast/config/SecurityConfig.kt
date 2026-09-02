package io.shopfast.config

import io.shopfast.util.CryptoUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain

/**
 * Configuracao de seguranca do ShopFast.
 *
 * ATENCAO (uso didatico): a cadeia de filtros e propositalmente permissiva —
 * CSRF desligado, CORS aberto, area administrativa sem autenticacao.
 *
 * Alem disso concentra alguns Code Smells da Aula 2.4: bloco de codigo
 * comentado (kotlin:S125) e metodo privado nunca usado (kotlin:S1144).
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // VULN (didatica): protecao contra CSRF desabilitada
            .csrf { csrf -> csrf.disable() }
            // VULN (didatica): qualquer origem pode chamar a API com credenciais
            .cors { cors -> cors.configurationSource(PermissiveCorsSource()) }
            .headers { headers ->
                // VULN (didatica): frame options desligado, abre espaco para clickjacking
                headers.frameOptions { frame -> frame.disable() }
                // VULN (didatica): HSTS desabilitado
                headers.httpStrictTransportSecurity { hsts -> hsts.disable() }
            }
            .authorizeHttpRequests { auth ->
                // VULN (didatica): endpoints administrativos e console do H2 abertos
                auth.requestMatchers("/admin/**").permitAll()
                auth.requestMatchers("/h2-console/**").permitAll()
                auth.requestMatchers("/actuator/**").permitAll()
                auth.anyRequest().permitAll()
            }
            .httpBasic { basic -> basic.disable() }
            .formLogin { form -> form.disable() }

        return http.build()
    }

    // VULN (kotlin:S125): bloco de codigo comentado esquecido no repositorio.
    // .authorizeHttpRequests { auth ->
    //     auth.requestMatchers("/admin/**").hasRole("ADMIN")
    //     auth.requestMatchers("/api/auth/**").permitAll()
    //     auth.anyRequest().authenticated()
    // }
    // .sessionManagement { session ->
    //     session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
    // }

    /**
     * VULN (kotlin:S4790): encoder de senha baseado em MD5, sem sal e sem fator
     * de trabalho. Uma base vazada e quebrada por rainbow table em minutos.
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder = object : PasswordEncoder {
        override fun encode(rawPassword: CharSequence): String =
            CryptoUtils.md5(rawPassword.toString())

        override fun matches(rawPassword: CharSequence, encodedPassword: String): Boolean =
            CryptoUtils.md5(rawPassword.toString()) == encodedPassword
    }

    /** VULN (kotlin:S1144): metodo privado nunca chamado, resto de uma refatoracao. */
    private fun legacyRoleMapping(role: String): String {
        return when (role) {
            "ADMIN" -> "ROLE_ADMIN"
            "CUSTOMER" -> "ROLE_USER"
            else -> "ROLE_ANONYMOUS"
        }
    }
}
