package io.shopfast.legacy;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Base64;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

/**
 * Importador legado de relatorios de parceiros, ainda em Java.
 *
 * <p>Achados do analisador Java do SonarQube:
 *
 * <ul>
 *   <li>VULN (java:S2755) — XXE: o parser aceita DTD e entidades externas, entao um
 *       {@code <!ENTITY x SYSTEM "file:///etc/passwd">} le arquivos do servidor;
 *   <li>VULN (java:S1313) — endereco IP de infraestrutura interna fixo no codigo;
 *   <li>Code Smell (java:S112) — {@code throws Exception} generico;
 *   <li>Code Smell (java:S1075) — caminho absoluto e separador de diretorio escritos
 *       na mao.
 * </ul>
 *
 * <p>VULN (didatica): path traversal em {@link #readReport(String)}, command injection
 * em {@link #exportToPdf(String)} e desserializacao insegura em
 * {@link #restoreSnapshot(String)} — o SonarQube Community nao os detecta, e eles sao
 * o material do Modulo 3 (DAST).
 */
@Component
public class LegacyReportImporter {

    /** Code Smell (java:S1075): caminho absoluto hardcoded. */
    private static final String REPORT_DIR = "/var/shopfast/reports";

    /** VULN (java:S1313): IP de infraestrutura interna no codigo. */
    private static final String BILLING_HOST = "10.42.13.7";

    /**
     * VULN (java:S2755 / XXE): o parser fica com a configuracao padrao, que expande
     * entidades externas.
     *
     * <p>Code Smell (java:S112): declara {@code throws Exception}.
     */
    public int importPartnerReport(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setExpandEntityReferences(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new ByteArrayInputStream(xml.getBytes()));
        return document.getElementsByTagName("item").getLength();
    }

    /** VULN (didatica): objeto Java reconstruido a partir de conteudo do cliente. */
    public Object restoreSnapshot(String base64Payload) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(base64Payload);
        ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes));
        return input.readObject();
    }

    /** VULN (didatica / java:S1075): path traversal — o nome vem do request e e concatenado. */
    public String readReport(String fileName) throws Exception {
        File file = new File(REPORT_DIR + "/" + fileName);
        return new String(Files.readAllBytes(file.toPath()));
    }

    /** VULN (didatica): o parametro do request e interpretado pelo shell. */
    public void exportToPdf(String reportName) throws Exception {
        String command = "wkhtmltopdf " + REPORT_DIR + "/" + reportName + ".html";
        Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", command});
    }

    /** VULN (java:S1313 e java:S5332): HTTP puro para um IP interno fixo no codigo. */
    public int notifyBilling(long orderId) throws Exception {
        URL url = new URL("http://" + BILLING_HOST + "/billing/orders/" + orderId);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        return connection.getResponseCode();
    }
}
