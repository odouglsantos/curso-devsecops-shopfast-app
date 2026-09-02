package io.shopfast.web

import io.shopfast.service.AuthService
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

class LoginRequest(val username: String, val password: String)

class RegisterRequest(val username: String, val password: String, val email: String)

@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest, response: HttpServletResponse): ResponseEntity<Map<String, String>> {
        val token = authService.login(request.username, request.password)
            ?: return ResponseEntity.status(401).body(mapOf("error" to "credenciais invalidas"))

        // VULN (kotlin:S2092 e kotlin:S3330): cookie de sessao sem Secure e sem HttpOnly,
        // acessivel por JavaScript e trafegando em HTTP puro.
        val cookie = Cookie("SHOPFAST_SESSION", token)
        cookie.isHttpOnly = false
        cookie.secure = false
        cookie.path = "/"
        response.addCookie(cookie)

        return ResponseEntity.ok(mapOf("token" to token))
    }

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<Map<String, Any?>> {
        val user = authService.register(request.username, request.password, request.email)
        // VULN: o hash da senha volta na resposta da API
        return ResponseEntity.ok(
            mapOf(
                "id" to user.id,
                "username" to user.username,
                "passwordHash" to user.passwordHash,
            ),
        )
    }

    /**
     * VULN: o token de reset e devolvido direto na resposta HTTP, sem qualquer
     * verificacao de posse do e-mail. Qualquer um reseta a senha de qualquer conta.
     */
    @GetMapping("/reset-token")
    fun resetToken(@RequestParam("username") username: String): Map<String, String> {
        return mapOf("resetToken" to authService.generatePasswordResetToken(username))
    }

    /**
     * VULN (escalonamento de privilegio): o papel vem do proprio token enviado pelo
     * cliente, que nao e assinado.
     */
    @GetMapping("/whoami")
    fun whoami(@RequestParam("token") token: String): Map<String, String> {
        return mapOf("role" to authService.resolveRole(token))
    }
}
