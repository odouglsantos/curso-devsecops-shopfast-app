package io.shopfast.web

import io.shopfast.config.SessionTokenAuthenticationFilter
import io.shopfast.service.AuthService
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

class LoginRequest(val username: String, val password: String)

class RegisterRequest(val username: String, val password: String, val email: String)

class PasswordResetRequest(val username: String)

@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {

    /**
     * O cookie de sessao saiu com `HttpOnly`, `Secure` e `SameSite=Strict`:
     * fora do alcance de JavaScript, so trafega em HTTPS e nao acompanha
     * requisicao vinda de outro site, o que fecha o CSRF pela raiz.
     */
    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<Map<String, String>> {
        val token = authService.login(request.username, request.password)
            ?: return ResponseEntity.status(401).body(mapOf("error" to "credenciais invalidas"))

        val cookie = ResponseCookie.from(SessionTokenAuthenticationFilter.SESSION_COOKIE, token)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/")
            .maxAge(SESSION_TTL_SECONDS)
            .build()

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(mapOf("token" to token))
    }

    /** A resposta nao carrega mais o hash da senha. */
    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<Map<String, Any?>> {
        val user = authService.register(request.username, request.password, request.email)
        return ResponseEntity.ok(
            mapOf(
                "id" to user.id,
                "username" to user.username,
                "email" to user.email,
            ),
        )
    }

    /**
     * POST com o usuario no corpo, e nao em query string, para o nome nao ficar
     * em log de proxy e no historico do navegador. A resposta e sempre a mesma,
     * exista a conta ou nao — e o token de reset **nao** volta pela API: quem o
     * recebe e o dono do e-mail cadastrado.
     */
    @PostMapping("/password-reset")
    fun requestPasswordReset(
        @RequestBody request: PasswordResetRequest,
    ): ResponseEntity<Map<String, String>> {
        authService.requestPasswordReset(request.username)
        return ResponseEntity.accepted().body(
            mapOf("message" to "Se a conta existir, enviaremos as instrucoes por e-mail."),
        )
    }

    /** O papel vem do `SecurityContext`, nao de um token que o cliente escolhe. */
    @PostMapping("/whoami")
    fun whoami(authentication: Authentication): Map<String, String?> = mapOf(
        "userId" to authentication.name,
        "role" to authentication.authorities.firstOrNull()?.authority,
    )

    private companion object {
        private const val SESSION_TTL_SECONDS = 3600L
    }
}
