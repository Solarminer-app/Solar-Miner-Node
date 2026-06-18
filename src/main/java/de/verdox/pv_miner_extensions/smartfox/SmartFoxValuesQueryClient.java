package de.verdox.pv_miner_extensions.smartfox;

import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Deprecated
public class SmartFoxValuesQueryClient {

    public Map<SmartFoxValuesDataType, String> readSmartFoxValues(String ipv4AddressSmartFox) throws Exception {
        String response = sendGetRequest("http://"+ipv4AddressSmartFox+"/values.xml");
        return parseSmartFoxValues(response);
    }

    private static String sendGetRequest(String url) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        }
    }

    private static Map<SmartFoxValuesDataType, String> parseSmartFoxValues(String wholeString) {
        Map<SmartFoxValuesDataType, String> map = new HashMap<>();
        String[] keyValuePairs = wholeString.split("</value>");

        Arrays.stream(keyValuePairs)
                .filter(s -> s.contains("<value id=\""))
                .map(s -> s.replace("<value id=\"", ""))
                .map(s -> s.split("\">")).forEach(strings -> {
                    if(strings.length == 2){
                        String key = strings[0];
                        String value = strings[1];
                        map.put(SmartFoxValuesDataType.byVariableName(key), value);
                    }
                });
        return map;
    }

    public static void main(String[] args) throws Exception {
        new SmartFoxValuesQueryClient().readSmartFoxValues("192.168.178.56");
    }

}
