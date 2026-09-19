package io.shopfast.web

import com.fasterxml.jackson.core.JsonProcessingException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import org.xml.sax.SAXException

/**
 * Traduz excecao em resposta HTTP sem vazar detalhe interno.
 *
 * O projeto devolvia stacktrace completo ao cliente — versao de biblioteca,
 * caminho de arquivo e estrutura do banco — e o ZAP levantava isso como
 * Application Error Disclosure. Agora o detalhe fica no log do servidor e o
 * cliente recebe so o status e uma mensagem generica.
 *
 * Herdar de [ResponseEntityExceptionHandler] importa: as excecoes do proprio
 * Spring MVC (rota inexistente, verbo errado, corpo malformado) continuam com
 * o status correto, em vez de cairem no `catch` generico e virarem 500.
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(ApiExceptionHandler::class.java)

    /** Entrada invalida: nome de arquivo, faixa de preco, numero de parcelas. */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleInvalidInput(e: IllegalArgumentException): ResponseEntity<Map<String, String>> {
        log.warn("Requisicao rejeitada: {}", e.message)
        return ResponseEntity.badRequest().body(mapOf("error" to "requisicao invalida"))
    }

    /**
     * Corpo XML ou JSON que o parser recusa — inclusive o XML com DOCTYPE do
     * ataque de XXE e o payload Java serializado que antes ia para o
     * `ObjectInputStream`. E entrada invalida, logo 400, nao 500.
     */
    @ExceptionHandler(SAXException::class, JsonProcessingException::class)
    fun handleMalformedPayload(e: Exception): ResponseEntity<Map<String, String>> {
        log.warn("Corpo da requisicao recusado pelo parser: {}", e.javaClass.simpleName)
        return ResponseEntity.badRequest().body(mapOf("error" to "conteudo invalido"))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<Map<String, String>> {
        log.error("Falha inesperada ao processar a requisicao", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to "erro interno"))
    }
}
