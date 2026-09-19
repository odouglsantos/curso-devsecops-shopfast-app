package io.shopfast.service

import io.shopfast.config.AppProperties
import io.shopfast.domain.User
import io.shopfast.repository.UserRepository
import io.shopfast.util.CryptoUtils
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Login, emissao e validacao do token de sessao e o fluxo de reset de senha.
 *
 * O ponto central e a assinatura: trocar o papel dentro do Base64 invalida o
 * token, que era exatamente como se virava ADMIN antes da correcao.
 */
class AuthServiceTest {

    private val userRepository = mock(UserRepository::class.java)
    private val properties = AppProperties(jwtSecret = "segredo-de-assinatura", encryptionKey = "chave")
    private val passwordEncoder = BCryptPasswordEncoder(4)
    private val crypto = CryptoUtils(properties)
    private val service = AuthService(userRepository, properties, passwordEncoder, crypto)

    private fun hash(senha: String): String = checkNotNull(passwordEncoder.encode(senha))

    private fun storedUser(id: Long = 2L, role: String = "CUSTOMER", senha: String = "Joana@2026") =
        User(
            id = id,
            username = "joana",
            passwordHash = hash(senha),
            email = "joana@example.com",
            role = role,
        )

    private fun decode(token: String) = String(Base64.getUrlDecoder().decode(token))

    private fun encode(conteudo: String) =
        Base64.getUrlEncoder().withoutPadding().encodeToString(conteudo.toByteArray())

    @Test
    fun `login valido emite um token que resolve na propria identidade`() {
        `when`(userRepository.findByUsername("joana")).thenReturn(storedUser())

        val token = assertNotNull(service.login("joana", "Joana@2026"))
        val principal = assertNotNull(service.resolveSession(token))

        assertEquals(2L, principal.userId)
        assertEquals("CUSTOMER", principal.role)
    }

    @Test
    fun `senha errada e usuario inexistente respondem igual`() {
        `when`(userRepository.findByUsername("joana")).thenReturn(storedUser())
        `when`(userRepository.findByUsername("ninguem")).thenReturn(null)

        assertNull(service.login("joana", "senha-errada"))
        assertNull(service.login("ninguem", "qualquer"))
    }

    @Test
    fun `usuario sem id gera token com identificador zero`() {
        `when`(userRepository.findByUsername("novo")).thenReturn(
            User(id = null, username = "novo", passwordHash = hash("x"), email = "n@e.io"),
        )

        val principal = assertNotNull(service.resolveSession(assertNotNull(service.login("novo", "x"))))

        assertEquals(0L, principal.userId)
    }

    @Test
    fun `dois tokens do mesmo usuario sao diferentes por causa do nonce`() {
        assertNotEquals(service.issueSessionToken(1L, "ADMIN"), service.issueSessionToken(1L, "ADMIN"))
    }

    @Test
    fun `trocar o papel dentro do token invalida a assinatura`() {
        val token = service.issueSessionToken(2L, "CUSTOMER")
        val adulterado = encode(decode(token).replace("CUSTOMER", "ADMIN00"))

        assertNull(service.resolveSession(adulterado))
    }

    @Test
    fun `token com numero de partes errado e recusado`() {
        val payload = "2:CUSTOMER"
        val assinatura = crypto.hmacSha256(payload, properties.jwtSecret)

        assertNull(service.resolveSession(encode("$payload:$assinatura")))
    }

    @Test
    fun `token com identificador nao numerico e recusado`() {
        val payload = "joana:CUSTOMER:nonce"
        val assinatura = crypto.hmacSha256(payload, properties.jwtSecret)

        assertNull(service.resolveSession(encode("$payload:$assinatura")))
    }

    @Test
    fun `token malformado e recusado sem estourar excecao`() {
        assertNull(service.resolveSession("nao-e-base64-valido!!"))
        assertNull(service.resolveSession(""))
    }

    @Test
    fun `cadastro grava a senha em hash e nunca em claro`() {
        `when`(userRepository.save(any(User::class.java))).thenAnswer { it.arguments[0] }

        val user = service.register("carlos", "Carlos@2026", "carlos@example.com")

        assertEquals("carlos", user.username)
        assertEquals("CUSTOMER", user.role)
        assertNotEquals("Carlos@2026", user.passwordHash)
        assertTrue(passwordEncoder.matches("Carlos@2026", user.passwordHash))
    }

    @Test
    fun `codificador de senha que devolve nulo interrompe o cadastro`() {
        val codificadorQuebrado = mock(org.springframework.security.crypto.password.PasswordEncoder::class.java)
        val comCodificadorQuebrado =
            AuthService(userRepository, properties, codificadorQuebrado, crypto)

        assertFailsWith<IllegalStateException> {
            comCodificadorQuebrado.register("carlos", "Carlos@2026", "carlos@example.com")
        }
    }

    @Test
    fun `reset de conta inexistente nao emite token`() {
        `when`(userRepository.findByUsername("ninguem")).thenReturn(null)

        service.requestPasswordReset("ninguem")

        assertTrue(resetTokens()["ninguem"] == null)
        assertFalse(service.consumePasswordResetToken("ninguem", "qualquer"))
    }

    @Test
    fun `reset de conta existente deixa um token pendente`() {
        `when`(userRepository.findByUsername("joana")).thenReturn(storedUser())

        service.requestPasswordReset("joana")

        assertNotNull(resetTokens()["joana"])
    }

    @Test
    fun `token de reset correto e aceito uma unica vez`() {
        plantarTokenDeReset("joana", "token-de-laboratorio")

        assertTrue(service.consumePasswordResetToken("joana", "token-de-laboratorio"))
        assertFalse(service.consumePasswordResetToken("joana", "token-de-laboratorio"))
    }

    @Test
    fun `token de reset errado e recusado e nao consome o pendente`() {
        plantarTokenDeReset("joana", "token-de-laboratorio")

        assertFalse(service.consumePasswordResetToken("joana", "token-inventado"))
        assertTrue(service.consumePasswordResetToken("joana", "token-de-laboratorio"))
    }

    @Test
    fun `token de reset vencido e descartado`() {
        plantarTokenDeReset("joana", "token-de-laboratorio", validoPor = -1L)

        assertFalse(service.consumePasswordResetToken("joana", "token-de-laboratorio"))
        assertFalse(service.consumePasswordResetToken("joana", "token-de-laboratorio"))
    }

    /**
     * O token de reset nao volta pela API — e esse justamente o ponto da
     * correcao —, entao o teste planta um pendente conhecido direto no mapa
     * interno, no mesmo formato que [AuthService.requestPasswordReset] grava:
     * o HMAC do token e a hora de expiracao.
     */
    private fun plantarTokenDeReset(username: String, token: String, validoPor: Long = 900L) {
        resetTokens()[username] =
            crypto.hmacSha256(token, properties.jwtSecret) to Instant.now().plusSeconds(validoPor)
    }

    @Suppress("UNCHECKED_CAST")
    private fun resetTokens(): ConcurrentHashMap<String, Pair<String, Instant>> {
        val field = AuthService::class.java.getDeclaredField("resetTokens")
        field.isAccessible = true
        return field.get(service) as ConcurrentHashMap<String, Pair<String, Instant>>
    }
}
