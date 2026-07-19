package de.verdox.solarminer.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class RestPVClient implements AutoCloseable {
    private final String baseUrl;
    private final String apiToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RestPVClient(String baseUrl, String apiToken) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiToken = apiToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public double read(RestPVConfig.Entry<?> entry) throws Exception {
        String fullUrl = this.baseUrl + entry.urlExtension();

        String acceptHeader = switch (entry.responseType()) {
            case JSON -> "application/json";
            case XML -> "application/xml";
            case PLAIN_TEXT -> "text/plain";
        };

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header("Accept", acceptHeader)
                .timeout(Duration.ofSeconds(5));

        if(apiToken != null && !apiToken.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + apiToken);
        }

        if (entry.httpMethod() == RestHttpMethod.GET) {
            requestBuilder.GET();
        } else if (entry.httpMethod() == RestHttpMethod.POST) {
            requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP request failed with status code: " + response.statusCode() + " for URL: " + fullUrl);
        }

        String rawValueStr;

        if (entry.responseType() == RestResponseType.JSON) {
            rawValueStr = extractFromJson(response.body(), entry.dataPath(), fullUrl);
        } else if (entry.responseType() == RestResponseType.XML) {
            rawValueStr = extractFromXml(response.body(), entry.dataPath(), fullUrl);
        } else {
            rawValueStr = response.body().trim();
        }

        Number parsedNumber = entry.restParameterType().parser().apply(rawValueStr);
        double finalValue = parsedNumber.doubleValue();

        finalValue = finalValue * entry.scaleFactor();

        return finalValue;
    }

    public void ping() throws IOException, InterruptedException {
        System.out.println(baseUrl);
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .GET()
                .timeout(Duration.ofSeconds(3));
        if(apiToken != null && !apiToken.isBlank()) {
            builder.header("Authorization", "Bearer " + apiToken);
        }

        HttpRequest request = builder.build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Ping failed. API returned status: " + response.statusCode());
        }
    }

    private double evaluateFormula(double value, String formula) {
        return value;
    }

    @Override
    public void close() {
    }

    private String extractFromJson(String body, String path, String url) throws IOException {
        JsonNode currentNode = objectMapper.readTree(body);
        String cleanPath = path.startsWith("$.") ? path.substring(2) : (path.startsWith("$") ? path.substring(1) : path);

        if (!cleanPath.isBlank()) {
            String[] keys = cleanPath.split("\\.");
            for (String key : keys) {
                if (currentNode == null) break;
                currentNode = currentNode.get(key);
            }
        }

        if (currentNode == null || currentNode.isMissingNode() || currentNode.isNull()) {
            throw new IOException("Could not find json path '" + path + "' in response from " + url);
        }
        return currentNode.asText();
    }

    private String extractFromXml(String body, String xpathStr, String url) throws Exception {
        int startIndex = body.indexOf("<?xml");

        if (startIndex == -1) {
            startIndex = body.indexOf('<');
        }

        int endIndex = body.lastIndexOf('>');

        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            body = body.substring(startIndex, endIndex + 1);
        }



        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();

        InputSource is = new InputSource(new StringReader(body));
        Document doc = builder.parse(is);

        XPathFactory xPathfactory = XPathFactory.newInstance();
        XPath xpath = xPathfactory.newXPath();
        XPathExpression expr = xpath.compile(xpathStr);

        String result = expr.evaluate(doc);
        if (result == null || result.isBlank()) {
            return "";
        }
        return result.trim();
    }
}
