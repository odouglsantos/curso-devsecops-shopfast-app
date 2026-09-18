package io.shopfast.legacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import io.shopfast.config.AppProperties;
import io.shopfast.util.ReportPaths;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Importador legado de relatorios de parceiros, ainda em Java.
 *
 * <p>Fechadas aqui as mesmas falhas da camada Kotlin:
 *
 * <ul>
 *   <li><b>XXE</b> (java:S2755): o parser recusa DOCTYPE e nao busca recurso externo;
 *   <li><b>Path traversal</b>: nome por lista branca e caminho conferido contra o
 *       diretorio base, em {@link ReportPaths};
 *   <li><b>Command injection</b>: sem shell — {@link ProcessBuilder} com lista de
 *       argumentos e nome de relatorio ja validado;
 *   <li><b>Desserializacao insegura</b>: {@code ObjectInputStream} saiu; o snapshot e
 *       JSON lido como mapa, sem tipagem polimorfica;
 *   <li>java:S1313 / java:S5332: o endereco de cobranca vem de configuracao, por HTTPS,
 *       em vez de um IP interno fixo no codigo em HTTP puro;
 *   <li>java:S112 / java:S1075: excecoes especificas e caminho vindo de configuracao.
 * </ul>
 */
@Component
public class LegacyReportImporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyReportImporter.class);

    private static final String DISALLOW_DOCTYPE =
            "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String WKHTMLTOPDF = "/usr/bin/wkhtmltopdf";
    private static final long PROCESS_TIMEOUT_SECONDS = 30L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String reportDirectory;
    private final String billingBaseUrl;

    public LegacyReportImporter(AppProperties properties) {
        this.reportDirectory = properties.getReportDirectory();
        this.billingBaseUrl = properties.getBillingBaseUrl();
    }

    /** Parser sem DTD e sem entidades externas. */
    public int importPartnerReport(String xml)
            throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(DISALLOW_DOCTYPE, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new ByteArrayInputStream(xml.getBytes()));
        return document.getElementsByTagName("item").getLength();
    }

    /** Snapshot em JSON: sem instanciacao de classe arbitraria, sem gadget chain. */
    public Map<String, Object> restoreSnapshot(String payload) throws IOException {
        MapType type = objectMapper
                .getTypeFactory()
                .constructMapType(java.util.LinkedHashMap.class, String.class, Object.class);
        return objectMapper.readValue(payload, type);
    }

    /** Caminho conferido contra o diretorio base antes de qualquer leitura. */
    public String readReport(String fileName) throws IOException {
        return Files.readString(ReportPaths.resolveInside(reportDirectory, fileName));
    }

    /** Sem shell: o nome do relatorio nunca chega a ser interpretado como comando. */
    public void exportToPdf(String reportName) throws IOException {
        String safeName = ReportPaths.requireSafeName(reportName);
        Path source = ReportPaths.resolveInside(reportDirectory, safeName + ".html");
        Path target = ReportPaths.resolveInside(reportDirectory, safeName + ".pdf");

        Process process = new ProcessBuilder(
                        List.of(WKHTMLTOPDF, source.toString(), target.toString()))
                .start();
        try {
            if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                LOGGER.error("Exportacao de relatorio excedeu o tempo limite");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Exportacao de relatorio interrompida", e);
        }
    }

    /** Cobranca por HTTPS, com o endereco vindo de configuracao. */
    public int notifyBilling(long orderId) throws IOException {
        URI uri = URI.create(billingBaseUrl + "/billing/orders/" + orderId);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod("POST");
        return connection.getResponseCode();
    }
}
