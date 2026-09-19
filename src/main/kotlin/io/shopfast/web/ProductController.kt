package io.shopfast.web

import io.shopfast.domain.Product
import io.shopfast.service.ProductSearchService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Vitrine publica do ShopFast.
 *
 * O endpoint `/debug-search`, que devolvia o stacktrace inteiro ao cliente —
 * versoes de biblioteca, caminhos internos e estrutura do banco —, foi
 * removido. A busca continua publica, agora sobre consultas parametrizadas no
 * `ProductSearchService`.
 */
@RestController
@RequestMapping("/api/products")
class ProductController(private val productSearchService: ProductSearchService) {

    @GetMapping("/search")
    fun search(@RequestParam("q") term: String): List<Product> =
        productSearchService.searchByName(term)

    @GetMapping("/by-category")
    fun byCategory(
        @RequestParam("category") category: String,
        @RequestParam("sort", defaultValue = "name") sort: String,
    ): List<Product> = productSearchService.searchByCategory(category, sort)

    @GetMapping("/by-price")
    fun byPrice(
        @RequestParam("min") min: String,
        @RequestParam("max") max: String,
    ): List<Product> = productSearchService.searchByPriceRange(min, max)
}
