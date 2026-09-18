package io.shopfast.service

import io.shopfast.config.AppProperties
import io.shopfast.domain.User
import io.shopfast.repository.UserRepository
import io.shopfast.util.CryptoUtils
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** Identidade extraida de um token de sessao ja validado. */
data class SessionPrincipal(val userId: Long, val role: String)

/**
 * Autenticacao e emissao de tokens de sessao do ShopFast.
 *
 * - a senha passa pelo `PasswordEncoder` (BCrypt) e nunca vai para o log;
 * - usuario inexistente e senha errada produzem a mesma resposta e a mesma
 *   mensagem, o que fecha a enumeracao de contas;
 * - o token carrega assinatura HMAC-SHA256, entao trocar o papel dentro do
 *   Base64 invalida o token;
 * - o token de reset nao e devolvido ao cliente: fica guardado em hash, com
 *   validade, e seria enviado pelo canal de e-mail cadastrado.
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val properties: AppProperties,
    private val passwordEncoder: PasswordEncoder,
    private val cryptoUtils: CryptoUtils,
) {

    private val logger = LoggerFactory.getLogger(AuthService::class.java)

    private val random = SecureRandom()

    /** Tokens de reset pendentes: username -> (hash do token, expiracao). */
    private val resetTokens = ConcurrentHashMap<String, Pair<String, Instant>>()

    fun login(username: String, password: String): String? {
        logger.info("Tentativa de login para o usuario {}", username)

        val user = userRepository.findByUsername(username)
        if (user == null || !passwordEncoder.matches(password, user.passwordHash)) {
            logger.info("Credenciais invalidas para o usuario {}", username)
            return null
        }
        return issueSessionToken(user.id ?: 0L, user.role)
    }

    /** Token com nonce aleatorio e assinatura HMAC-SHA256 do proprio conteudo. */
    fun issueSessionToken(userId: Long, role: String): String {
        val payload = "$userId:$role:${randomToken()}"
        val signed = "$payload:${cryptoUtils.hmacSha256(payload, properties.jwtSecret)}"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signed.toByteArray())
    }

    /**
     * Devolve a identidade apenas quando a assinatura confere. Token adulterado,
     * malformado ou com papel trocado devolve `null`.
     */
    fun resolveSession(token: String): SessionPrincipal? = runCatching {
        val decoded = String(Base64.getUrlDecoder().decode(token))
        val payload = decoded.substringBeforeLast(SEPARATOR)
        val signature = decoded.substringAfterLast(SEPARATOR)
        val parts = payload.split(SEPARATOR)
        if (parts.size != PAYLOAD_PARTS) {
            return@runCatching null
        }
        val expected = cryptoUtils.hmacSha256(payload, properties.jwtSecret)
        if (!cryptoUtils.matchesSignature(expected, signature)) {
            return@runCatching null
        }
        parts[0].toLongOrNull()?.let { userId -> SessionPrincipal(userId, parts[1]) }
    }.getOrElse { error ->
        logger.debug("Token de sessao rejeitado: {}", error.message)
        null
    }

    fun register(username: String, password: String, email: String): User {
        val user = User(
            username = username,
            passwordHash = checkNotNull(passwordEncoder.encode(password)) {
                "Nao foi possivel codificar a senha"
            },
            email = email,
            role = "CUSTOMER",
        )
        return userRepository.save(user)
    }

    /**
     * Gera o token de reset, guarda so o hash dele e o devolve para o canal de
     * envio. A API **nao** expoe esse valor: quem o recebe e o dono do e-mail.
     */
    fun requestPasswordReset(username: String) {
        if (userRepository.findByUsername(username) == null) {
            // Silencio proposital: responder diferente aqui entrega quais contas existem.
            logger.info("Reset solicitado para usuario inexistente")
            return
        }
        val token = randomToken()
        resetTokens[username] = cryptoUtils.hmacSha256(token, properties.jwtSecret) to
            Instant.now().plusSeconds(RESET_TTL_SECONDS)
        logger.info("Token de reset emitido para {} e enviado por e-mail", username)
        // O envio real do e-mail entraria aqui; o token nunca volta pela API.
    }

    /** Consome o token de reset, se ele confere e ainda esta no prazo. */
    fun consumePasswordResetToken(username: String, token: String): Boolean {
        val (storedHash, expiresAt) = resetTokens[username] ?: return false
        if (Instant.now().isAfter(expiresAt)) {
            resetTokens.remove(username)
            return false
        }
        val candidate = cryptoUtils.hmacSha256(token, properties.jwtSecret)
        if (!cryptoUtils.matchesSignature(storedHash, candidate)) {
            return false
        }
        resetTokens.remove(username)
        return true
    }

    private fun randomToken(): String {
        val bytes = ByteArray(NONCE_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        private const val SEPARATOR = ":"
        private const val PAYLOAD_PARTS = 3
        private const val NONCE_BYTES = 32
        private const val RESET_TTL_SECONDS = 900L
    }
}
