package org.example;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.nio.charset.StandardCharsets;

public class WebFetcher {
    public static String fetch(String url) throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(url);
            get.addHeader("User-Agent", "Mozilla/5.0");
            return client.execute(get, response ->
                    EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
        }
    }
}
