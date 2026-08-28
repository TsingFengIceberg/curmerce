package cn.iocoder.yudao.curmerce.search;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Reads public source snapshots through service APIs for index rebuilds. */
@Component
public class SearchSourceClient {
    private final SearchProperties properties;
    private final HttpClient client;

    public SearchSourceClient(SearchProperties properties) {
        this.properties = properties;
        this.client = HttpClient.newBuilder().connectTimeout(properties.requestTimeout()).build();
    }

    public List<Map<String, Object>> fetchProducts() {
        return fetchPages(properties.coreBaseUrl() + "/app-api/commerce/catalog/product-page", this::productDocument);
    }

    public List<Map<String, Object>> fetchPosts() {
        return fetchPages(properties.communityBaseUrl() + "/app-api/community/post/page", this::postDocument);
    }

    private List<Map<String, Object>> fetchPages(String baseUrl,
                                                 Function<Map<String, Object>, Map<String, Object>> mapper) {
        List<Map<String, Object>> documents = new ArrayList<>();
        for (int page = 1; page <= 10_000; page++) {
            Map<String, Object> response = get(baseUrl + "?pageNo=" + page + "&pageSize=100");
            Map<String, Object> data = response != null && response.get("data") instanceof Map<?, ?> map
                    ? castMap(map) : Map.of();
            Object values = data.get("list");
            if (!(values instanceof List<?> list) || list.isEmpty()) break;
            for (Object value : list) {
                if (value instanceof Map<?, ?> map) documents.add(mapper.apply(castMap(map)));
            }
            long total = data.get("total") instanceof Number number ? number.longValue() : documents.size();
            if (documents.size() >= total || list.size() < 100) break;
        }
        return documents;
    }

    private Map<String, Object> productDocument(Map<String, Object> source) {
        Map<String, Object> doc = new HashMap<>(source);
        doc.put("id", source.get("id"));
        doc.put("visible", true);
        doc.put("sourceEventId", 0L);
        return doc;
    }

    private Map<String, Object> postDocument(Map<String, Object> source) {
        Map<String, Object> doc = new HashMap<>(source);
        doc.put("id", source.get("id"));
        doc.put("visible", true);
        doc.put("sourceEventId", 0L);
        return doc;
    }

    private Map<String, Object> get(String url) {
        try {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(properties.requestTimeout()).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Source API returned HTTP " + response.statusCode());
            }
            return JsonUtils.parseMap(response.body());
        } catch (Exception ex) {
            throw new IllegalStateException("Search source rebuild request failed", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> value) {
        return (Map<String, Object>) value;
    }
}
