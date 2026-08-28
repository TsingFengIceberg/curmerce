package cn.iocoder.yudao.curmerce.search;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchIndexClientTest {

    @Test
    void buildSearchQuery_supportsFuzzyAndSubstringClauses() {
        Map<String, Object> request = ElasticsearchIndexClient.buildSearchQuery("te*st", 1, 12);
        Map<String, Object> query = map(request.get("query"));
        Map<String, Object> bool = map(query.get("bool"));
        List<?> should = (List<?>) bool.get("should");

        assertThat(bool).containsEntry("minimum_should_match", 1);
        assertThat(should).hasSize(5);
        assertThat(should.toString()).contains("fuzziness=AUTO", "prefix_length=0", "name.keyword");
        assertThat(should.toString()).contains("value=*te\\*st*");
    }

    @Test
    void buildSearchQuery_withoutKeywordUsesMatchAll() {
        Map<String, Object> request = ElasticsearchIndexClient.buildSearchQuery(" ", 0, 500);
        Map<String, Object> query = map(request.get("query"));
        Map<String, Object> bool = map(query.get("bool"));

        assertThat(bool).containsKey("must");
        assertThat(bool).doesNotContainKey("should");
        assertThat(request).containsEntry("from", 0).containsEntry("size", 100);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
