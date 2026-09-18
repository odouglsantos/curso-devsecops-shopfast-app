package io.shopfast.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter

/**
 * Configuracao de seguranca do ShopFast.
 *
 * Esta classe era o centro dos achados de DAST: CSRF desligado, CORS aberto,
 * `frameOptions` e HSTS desabilitados e `permitAll` em tudo, inclusive
 * `/admin`, `/actuator` e o console do H2. Agora:
 *
 * - CSRF ligado, com token em cookie (padrao double-submit). Login e cadastro
 *   ficam de fora porque ainda nao ha sessao a proteger nesses dois;
 * - CORS restrito a lista branca de origens ([ShopFastCorsSource]);
 * - `frameOptions` em DENY (clickjacking), HSTS ligado, `Referrer-Policy` e
 *   `Content-Security-Policy` declarados;
 * - autorizacao real por rota, apoiada no [SessionTokenAuthenticationFilter]:
 *   `/admin` e `/api/legacy` exigem ADMIN, pedidos exigem sessao, e o
 *   que sobra cai em `authenticated()` por padrao — nao em `permitAll()`;
 * - sessao stateless: nada de `JSESSIONID`, o estado vive no token assinado.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(private val properties: AppProperties) {

    @Bean
    fun filterChain(
        http: HttpSecurity,
        sessionTokenFilter: SessionTokenAuthenticationFilter,
    ): SecurityFilterChain {
        http
            .csrf { csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                // Carga ansiosa do token: sem isto o cookie XSRF-TOKEN so seria
                // emitido quando algo ja tivesse lido o token, e o cliente nunca
                // teria como manda-lo de volta.
                csrf.csrfTokenRequestHandler(
                    CsrfTokenRequestAttributeHandler().apply { setCsrfRequestAttributeName(null) },
                )
                // Sem sessao estabelecida ainda, nao ha o que um CSRF sequestrar
                // nestas tres: as duas de entrada e o pedido de reset, que
                // responde sempre a mesma coisa e nao muda estado visivel.
                csrf.ignoringRequestMatchers(
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/auth/password-reset",
                )
            }
            .cors { cors -> cors.configurationSource(ShopFastCorsSource(properties.allowedOrigins)) }
            .headers { headers ->
                headers.frameOptions { frame -> frame.deny() }
                headers.httpStrictTransportSecurity { hsts ->
                    hsts.includeSubDomains(true).maxAgeInSeconds(HSTS_MAX_AGE)
                }
                headers.referrerPolicy { referrer ->
                    referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)
                }
                headers.xssProtection { xss ->
                    xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK)
                }
                headers.contentSecurityPolicy { csp ->
                    csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")
                }
            }
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .authorizeHttpRequests { auth ->
                // Vitrine publica e entrada de sessao.
                auth.requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                auth.requestMatchers("/api/auth/password-reset").permitAll()
                auth.requestMatchers("/api/products/**").permitAll()
                // Sem isto, erro em rota publica vira 401 em vez do status real.
                auth.requestMatchers("/error").permitAll()
                // Health para o orquestrador; o resto do Actuator e administrativo.
                auth.requestMatchers("/actuator/health").permitAll()
                auth.requestMatchers("/actuator/**").hasRole(ADMIN)
                // Contrato OpenAPI, que o ZAP importa no pipeline de DAST.
                auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                // Area administrativa e camada legada: so ADMIN.
                auth.requestMatchers("/admin/**", "/api/legacy/**").hasRole(ADMIN)
                auth.anyRequest().authenticated()
            }
            .exceptionHandling { exceptions ->
                // API REST: 401/403 secos, sem redirect para pagina de login.
                exceptions.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
            .httpBasic { basic -> basic.disable() }
            .formLogin { form -> form.disable() }
            .addFilterBefore(sessionTokenFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    /**
     * BCrypt com fator de trabalho 12: sal por senha e custo alto o bastante para
     * inviabilizar rainbow table e ataque de forca bruta em base vazada.
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(BCRYPT_STRENGTH)

    private companion object {
        private const val BCRYPT_STRENGTH = 12
        private const val HSTS_MAX_AGE = 31_536_000L
        private const val ADMIN = "ADMIN"
    }
}
