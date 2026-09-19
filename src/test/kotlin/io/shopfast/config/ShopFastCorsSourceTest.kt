package io.shopfast.config

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CORS por lista branca. O `PermissiveCorsSource` refletia a origem recebida
 * com `allowCredentials = true` — qualquer site lia resposta autenticada.
 */
class ShopFastCorsSourceTest {

    private val request = MockHttpServletRequest("GET", "/api/orders/mine")

    @Test
    fun `lista vazia nao libera nenhuma origem`() {
        assertNull(ShopFastCorsSource(emptyList()).getCorsConfiguration(request))
    }

    @Test
    fun `apenas as origens declaradas entram na configuracao`() {
        val origens = listOf("https://loja.shopfast.io", "https://admin.shopfast.io")

        val configuration = assertNotNull(ShopFastCorsSource(origens).getCorsConfiguration(request))

        assertEquals(origens, configuration.allowedOrigins)
        assertEquals(true, configuration.allowCredentials)
        assertEquals(3600L, configuration.maxAge)
    }

    @Test
    fun `nao ha curinga em origem, metodo ou cabecalho`() {
        val configuration = assertNotNull(
            ShopFastCorsSource(listOf("https://loja.shopfast.io")).getCorsConfiguration(request),
        )

        assertTrue(configuration.allowedOrigins?.none { it == "*" } == true)
        assertTrue(configuration.allowedOriginPatterns.isNullOrEmpty())
        assertEquals(listOf("GET", "POST", "PUT", "DELETE", "OPTIONS"), configuration.allowedMethods)
        assertEquals(listOf("Authorization", "Content-Type", "X-XSRF-TOKEN"), configuration.allowedHeaders)
    }
}
