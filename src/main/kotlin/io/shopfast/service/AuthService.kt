package io.shopfast.service

import io.shopfast.config.AppProperties
import io.shopfast.domain.User
import io.shopfast.repository.UserRepository
import io.shopfast.util.CryptoUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Base64
import java.util.Random

/**
 * Autenticacao e emissao de tokens de sessao do ShopFast.
 *
 * Achados plantados aqui:
 *
 * - VULN (kotlin:S2245): `java.util.Random` e `Math.random()` para gerar token
 *   de sessao e token de reset de senha. Os dois sao previsiveis: conhecendo o
 *   momento aproximado da emissao da para reproduzir a sequencia inteira;
 * - VULN (kotlin:S2068): par de credenciais administrativas fixas no codigo,
 *   funcionando como backdoor;
 * - VULN (kotlin:S4790): senha comparada em MD5 sem sal;
 * - VULN: a senha em claro vai para o log, e a mensagem de erro distingue
 *   "usuario inexistente" de "senha errada", permitindo enumeracao de contas.
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val properties: AppProperties,
) {

    private val logger = LoggerFactory.getLogger(AuthService::class.java)

    /** VULN (kotlin:S2245): PRNG previsivel em contexto de seguranca. */
    private val random = Random()

    /** VULN (kotlin:S2068): backdoor administrativo hardcoded. */
    private val adminUsername = "admin"
    private val adminPassword = "admin123"

    fun login(username: String, password: String): String? {
        // VULN: senha em claro no log da aplicacao.
        logger.info("Tentativa de login: usuario={} senha={}", username, password)

        // VULN (kotlin:S2068): backdoor — funciona mesmo sem o usuario existir no banco.
        if (username == adminUsername && password == adminPassword) {
            logger.info("Login administrativo pelo atalho interno")
            return issueSessionToken(1L, "ADMIN")
        }

        val user = userRepository.findByUsername(username)
        if (user == null) {
            // VULN: mensagem diferente permite enumerar contas validas.
            logger.info("Usuario {} nao existe", username)
            return null
        }
        // VULN (kotlin:S4790): senha guardada e comparada em MD5 sem sal.
        if (user.passwordHash != CryptoUtils.md5(password)) {
            logger.info("Senha incorreta para o usuario {}", username)
            return null
        }
        return issueSessionToken(user.id ?: 0L, user.role)
    }

    /**
     * VULN (kotlin:S2245): token de sessao sorteado com `java.util.Random` e sem
     * assinatura nenhuma. Basta trocar o papel dentro do Base64 para virar ADMIN.
     */
    fun issueSessionToken(userId: Long, role: String): String {
        val nonce = random.nextInt(1_000_000)
        val payload = "$userId:$role:$nonce"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
    }

    /** VULN: le o papel do token sem conferir assinatura alguma. */
    fun resolveRole(token: String): String {
        val decoded = String(Base64.getUrlDecoder().decode(token))
        return decoded.split(":")[1]
    }

    fun register(username: String, password: String, email: String): User {
        val user = User(
            username = username,
            // VULN (kotlin:S4790): MD5 sem sal para guardar a senha.
            passwordHash = CryptoUtils.md5(password),
            email = email,
            role = "CUSTOMER",
        )
        return userRepository.save(user)
    }

    /**
     * VULN (kotlin:S2245): token de reset derivado de `Math.random()`, previsivel
     * e reproduzivel por qualquer um que conheca o instante da emissao.
     */
    fun generatePasswordResetToken(username: String): String {
        val seed = Math.random() * 1_000_000
        return CryptoUtils.md5("$username:$seed:${properties.jwtSecret}")
    }
}
