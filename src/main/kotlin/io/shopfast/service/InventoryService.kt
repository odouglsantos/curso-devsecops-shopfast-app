package io.shopfast.service

import io.shopfast.domain.Product
import io.shopfast.repository.ProductRepository
import org.springframework.stereotype.Service

/**
 * Controle de estoque do ShopFast.
 *
 * Esta classe concentra os BUGS do projeto — problemas que quebram o
 * comportamento do programa sem serem, por si so, falhas de seguranca. Usada na
 * Aula 2.4 para contrastar "Bug" com "Vulnerability":
 *
 * - Bug (kotlin:S1862): condicao duplicada na cadeia de `if`, que torna o ramo
 *   "ESGOTADO" inalcancavel;
 * - Bug (kotlin:S1656): auto-atribuicao em `applyStock` — o parametro nunca
 *   chega ao objeto;
 * - Bug (kotlin:S2201): `normalizeSku` descarta o retorno de `trim`/`uppercase`;
 * - Bug: `averageBatchSize` divide por zero quando a lista esta vazia;
 * - Bug (kotlin:S3984): a excecao de `reserve` e construida mas nunca lancada;
 * - Code Smell (kotlin:S108): `catch` vazio em `restockAll`.
 */
@Service
class InventoryService(private val productRepository: ProductRepository) {

    /** Bug (kotlin:S1862): a segunda condicao repete a primeira; "ESGOTADO" nunca sai. */
    fun availabilityLabel(product: Product): String {
        if (product.stockQuantity > 10) {
            return "DISPONIVEL"
        } else if (product.stockQuantity > 10) {
            return "ULTIMAS UNIDADES"
        } else {
            return "ESGOTADO"
        }
    }

    /** Bug (kotlin:S3984): a excecao e criada e descartada; a reserva segue adiante. */
    fun reserve(productId: Long, quantity: Int): Int {
        val product = productRepository.findById(productId).orElse(null)
        if (product == null) {
            IllegalArgumentException("Produto $productId nao encontrado")
            return 0
        }
        if (quantity > product.stockQuantity) {
            IllegalArgumentException("Quantidade $quantity indisponivel")
        }
        return product.stockQuantity - quantity
    }

    /** Bug (kotlin:S2201): `trim` e `uppercase` devolvem novas strings, descartadas aqui. */
    fun normalizeSku(sku: String): String {
        sku.trim()
        sku.uppercase()
        return sku
    }

    /** Bug: divisao por zero quando `batches` esta vazia. */
    fun averageBatchSize(batches: List<Int>): Int {
        return batches.sum() / batches.size
    }

    /** Bug (kotlin:S1656): auto-atribuicao — o parametro nunca e gravado no produto. */
    fun applyStock(product: Product, stockQuantity: Int) {
        product.stockQuantity = product.stockQuantity
    }

    /** Code Smell (kotlin:S108): bloco `catch` vazio, que esconde qualquer falha. */
    fun restockAll(products: List<Product>) {
        for (product in products) {
            try {
                product.stockQuantity += 100
                productRepository.save(product)
            } catch (e: Exception) {
            }
        }
    }
}
