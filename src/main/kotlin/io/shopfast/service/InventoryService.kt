package io.shopfast.service

import io.shopfast.domain.Product
import io.shopfast.repository.ProductRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Controle de estoque do ShopFast.
 *
 * Esta classe concentrava os BUGS do projeto. Todos corrigidos:
 *
 * - a condicao duplicada de [availabilityLabel] virou uma faixa de verdade, e o
 *   ramo "ESGOTADO" voltou a ser alcancavel;
 * - a auto-atribuicao de [applyStock] passou a gravar o parametro no produto;
 * - [normalizeSku] devolve o resultado de `trim`/`uppercase`, que antes era
 *   descartado;
 * - [averageBatchSize] nao divide mais por zero com a lista vazia;
 * - as excecoes de [reserve] sao efetivamente lancadas, e a reserva desconta o
 *   estoque em vez de so calcular o saldo;
 * - o `catch` vazio de [restockAll] passou a registrar a falha.
 */
@Service
class InventoryService(private val productRepository: ProductRepository) {

    private val logger = LoggerFactory.getLogger(InventoryService::class.java)

    fun availabilityLabel(product: Product): String = when {
        product.stockQuantity > LOW_STOCK_THRESHOLD -> "DISPONIVEL"
        product.stockQuantity > 0 -> "ULTIMAS UNIDADES"
        else -> "ESGOTADO"
    }

    /** Reserva [quantity] unidades e devolve o estoque restante. */
    fun reserve(productId: Long, quantity: Int): Int {
        val product = productRepository.findById(productId).orElseThrow {
            IllegalArgumentException("Produto $productId nao encontrado")
        }
        require(quantity > 0) { "Quantidade $quantity invalida" }
        require(quantity <= product.stockQuantity) { "Quantidade $quantity indisponivel" }

        product.stockQuantity -= quantity
        productRepository.save(product)
        return product.stockQuantity
    }

    fun normalizeSku(sku: String): String = sku.trim().uppercase()

    /** Devolve zero para a lista vazia, em vez de estourar divisao por zero. */
    fun averageBatchSize(batches: List<Int>): Int =
        if (batches.isEmpty()) 0 else batches.sum() / batches.size

    fun applyStock(product: Product, stockQuantity: Int) {
        product.stockQuantity = stockQuantity
    }

    fun restockAll(products: List<Product>) {
        for (product in products) {
            try {
                product.stockQuantity += RESTOCK_QUANTITY
                productRepository.save(product)
            } catch (e: Exception) {
                logger.error("Falha ao repor o estoque do produto {}", product.id, e)
            }
        }
    }

    private companion object {
        private const val LOW_STOCK_THRESHOLD = 10
        private const val RESTOCK_QUANTITY = 100
    }
}
