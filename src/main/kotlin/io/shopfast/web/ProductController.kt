package io.shopfast.web

import io.shopfast.domain.Product
import io.shopfast.service.ProductSearchService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Vitrine publica do ShopFast.
 */
@RestController
@RequestMapping("/api/products")
class ProductController(private val productSearchService: ProductSearchService) {

    /**
     * Endpoint usado na Aula 2.4 como exemplo de SQL Injection.
     *
     * Reproducao: /api/products/search?q=' OR '1'='1
     */
    @GetMapping("/search")
    fun search(@RequestParam("q") term: String): List<Product> {
        return productSearchService.searchByName(term)
    }

    @GetMapping("/by-category")
    fun byCategory(
        @RequestParam("category") category: String,
        @RequestParam("sort", defaultValue = "name") sort: String,
    ): List<Product> {
        return productSearchService.searchByCategory(category, sort)
    }

    @GetMapping("/by-price")
    fun byPrice(
        @RequestParam("min") min: String,
        @RequestParam("max") max: String,
    ): List<Product> {
        return productSearchService.searchByPriceRange(min, max)
    }

    /**
     * VULN (exposicao de informacao): o stacktrace completo volta para o cliente,
     * entregando versoes de biblioteca, caminhos internos e estrutura do banco.
     */
    @GetMapping("/debug-search")
    fun debugSearch(@RequestParam("q") term: String): ResponseEntity<String> {
        return try {
            ResponseEntity.ok(productSearchService.searchByName(term).size.toString())
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(e.stackTraceToString())
        }
    }
}
