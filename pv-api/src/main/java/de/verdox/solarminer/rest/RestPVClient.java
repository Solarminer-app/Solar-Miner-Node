package de.verdox.solarminer.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.solarminer.formula.FormulaEngine;
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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RestPVClient implements AutoCloseable {
    private final String baseUrl;
    private final String apiToken;
    private final RestAuthenticationType authenticationType;
    private final HttpClient httpClient;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern JSON_EXPRESSION_PATH = Pattern.compile(
            "\\$(?:(?:\\.[A-Za-z_][A-Za-z0-9_-]*)|(?:\\[(?:\\d+|\"[^\"]+\"|'[^']+')]))+");

    public RestPVClient(String baseUrl, String apiToken) {
        this(baseUrl, apiToken, RestAuthenticationType.BEARER);
    }

    public RestPVClient(String baseUrl, String apiToken, RestAuthenticationType authenticationType) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiToken = apiToken;
        this.authenticationType = authenticationType == null ? RestAuthenticationType.BEARER : authenticationType;
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

        applyAuthentication(requestBuilder);

        if (entry.httpMethod() == RestHttpMethod.GET) {
            requestBuilder.GET();
        } else if (entry.httpMethod() == RestHttpMethod.POST) {
            requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP request failed with status code: " + response.statusCode() + " for URL: " + fullUrl);
        }

        return parsePayload(response.body(), entry, fullUrl);
    }

    /** Parses a JSON, XML or plain-text device message without performing an HTTP request. */
    public static double parsePayload(String body, RestPVConfig.Entry<?> entry, String source) throws Exception {
        String rawValueStr;
        if (entry.responseType() == RestResponseType.JSON) {
            rawValueStr = extractFromJson(body, entry.dataPath(), source);
        } else if (entry.responseType() == RestResponseType.XML) {
            rawValueStr = extractFromXml(body, entry.dataPath(), source);
        } else {
            rawValueStr = body.trim();
            if (entry.dataPath() != null && entry.dataPath().startsWith("regex:")) {
                rawValueStr = extractWithRegex(rawValueStr, entry.dataPath().substring("regex:".length()), source);
            }
        }
        String normalizedValue = RestValueParser.normalizeNumber(rawValueStr);
        Number parsedNumber = entry.restParameterType().parser().apply(normalizedValue);
        return parsedNumber.doubleValue() * entry.scaleFactor();
    }

    public void ping() throws IOException, InterruptedException {
        System.out.println(baseUrl);
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .GET()
                .timeout(Duration.ofSeconds(3));
        applyAuthentication(builder);

        HttpRequest request = builder.build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Ping failed. API returned status: " + response.statusCode());
        }
    }

    private double evaluateFormula(double value, String formula) {
        return value;
    }

    private void applyAuthentication(HttpRequest.Builder builder) {
        if (apiToken == null || apiToken.isBlank() || authenticationType == RestAuthenticationType.NONE) return;
        if (authenticationType == RestAuthenticationType.BASIC) {
            String encoded = Base64.getEncoder().encodeToString(apiToken.getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + encoded);
        } else {
            builder.header("Authorization", "Bearer " + apiToken);
        }
    }

    @Override
    public void close() {
    }

    static String extractFromJson(String body, String path, String url) throws IOException {
        JsonNode currentNode = OBJECT_MAPPER.readTree(body);
        if (path.startsWith("expr:")) {
            return evaluateJsonExpression(currentNode, path.substring("expr:".length()), url);
        }
        currentNode = resolveJsonPath(currentNode, path);
        if (currentNode == null || currentNode.isMissingNode() || currentNode.isNull()) {
            throw new IOException("Could not find json path '" + path + "' in response from " + url);
        }
        return currentNode.asText();
    }

    private static JsonNode resolveJsonPath(JsonNode currentNode, String path) {
        String cleanPath = path.startsWith("$.") ? path.substring(2) : (path.startsWith("$") ? path.substring(1) : path);

        if (!cleanPath.isBlank()) {
            for (String segment : cleanPath.split("\\.")) {
                Matcher tokenMatcher = Pattern.compile("([^\\[\\]]+)|\\[(\\d+)]|\\[[\"']([^\"']+)[\"']]").matcher(segment);
                int consumed = 0;
                while (tokenMatcher.find()) {
                    if (tokenMatcher.start() != consumed || currentNode == null) {
                        currentNode = null;
                        break;
                    }
                    if (tokenMatcher.group(1) != null) {
                        currentNode = currentNode.get(tokenMatcher.group(1));
                    } else if (tokenMatcher.group(2) != null) {
                        currentNode = currentNode.isArray() ? currentNode.get(Integer.parseInt(tokenMatcher.group(2))) : null;
                    } else {
                        currentNode = currentNode.get(tokenMatcher.group(3));
                    }
                    consumed = tokenMatcher.end();
                }
                if (consumed != segment.length()) currentNode = null;
                if (currentNode == null) break;
            }
        }

        return currentNode;
    }

    static String evaluateJsonExpression(JsonNode root, String expression, String url) throws IOException {
        Matcher matcher = JSON_EXPRESSION_PATH.matcher(expression);
        StringBuilder formula = new StringBuilder();
        java.util.Map<String, Double> values = new java.util.HashMap<>();
        int index = 0;
        while (matcher.find()) {
            JsonNode node = resolveJsonPath(root, matcher.group());
            if (node == null || !node.isNumber()) {
                throw new IOException("JSON expression path '" + matcher.group() + "' is not numeric in response from " + url);
            }
            String variable = "v" + index++;
            values.put(variable, node.doubleValue());
            matcher.appendReplacement(formula, Matcher.quoteReplacement("$" + variable));
        }
        matcher.appendTail(formula);
        if (values.isEmpty() || !formula.toString().matches("[0-9v$+\\-*/%().\\s]+")) {
            throw new IOException("Unsupported JSON expression for " + url);
        }
        double result = FormulaEngine.evaluate(0, formula.toString(), variable -> {
            Double value = values.get(variable);
            if (value == null) throw new IllegalArgumentException("Unknown JSON expression variable " + variable);
            return value;
        });
        if (!Double.isFinite(result)) throw new IOException("JSON expression returned a non-finite value for " + url);
        return Double.toString(result);
    }

    static String extractWithRegex(String body, String regex, String url) throws IOException {
        Pattern pattern;
        try {
            pattern = Pattern.compile(regex);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid response regex for " + url, exception);
        }
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) {
            throw new IOException("Response regex did not match response from " + url);
        }
        return matcher.groupCount() > 0 ? matcher.group(1) : matcher.group();
    }

    static String extractFromXml(String body, String xpathStr, String url) throws Exception {
        int startIndex = body.indexOf("<?xml");

        if (startIndex == -1) {
            startIndex = body.indexOf('<');
        }

        int endIndex = body.lastIndexOf('>');

        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            body = body.substring(startIndex, endIndex + 1);
        }



        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
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
        String result = "";
        try {
            XPathExpression expr = xpath.compile(xpathStr);
            result = expr.evaluate(doc);
        } catch (javax.xml.xpath.XPathExpressionException ignored) {
            // Prefixes used by a device are not necessarily known to the profile. The fallback below uses local names.
        }
        if (result == null || result.isBlank()) {
            String namespaceAgnosticPath = toNamespaceAgnosticXPath(xpathStr);
            if (!namespaceAgnosticPath.equals(xpathStr)) {
                result = xpath.compile(namespaceAgnosticPath).evaluate(doc);
            }
        }
        if (result == null || result.isBlank()) {
            throw new IOException("Could not find XML path '" + xpathStr + "' in response from " + url);
        }
        return result.trim();
    }

    static String toNamespaceAgnosticXPath(String xpath) {
        Pattern element = Pattern.compile("(^|/)(?![/*.@])(?:[A-Za-z_][\\w.-]*:)?([A-Za-z_][\\w.-]*)(?=(?:/|\\[|$))");
        Matcher matcher = element.matcher(xpath);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(
                    matcher.group(1) + "*[local-name()='" + matcher.group(2) + "']"));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
