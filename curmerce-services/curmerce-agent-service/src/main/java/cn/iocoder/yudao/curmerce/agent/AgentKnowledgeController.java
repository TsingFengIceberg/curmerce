package cn.iocoder.yudao.curmerce.agent;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RequestHeader;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/app-api/agent")
public class AgentKnowledgeController {
    private final AgentKnowledgeStore store;
    private final AgentToolRegistry registry;
    private final AgentServiceProperties properties;
    private final AgentKnowledgeIngestionQueue ingestionQueue;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AgentKnowledgeKafkaDltService kafkaDlt;
    public AgentKnowledgeController(AgentKnowledgeStore store, AgentToolRegistry registry, AgentServiceProperties properties,
                                    AgentKnowledgeIngestionQueue ingestionQueue) {
        this.store = store; this.registry = registry; this.properties = properties; this.ingestionQueue = ingestionQueue;
    }

    @GetMapping("/knowledge/search")
    public CommonResult<List<AgentKnowledgeStore.Document>> search(@RequestParam String query,
                                                                    @RequestParam(defaultValue = "5") int limit,
                                                                    @RequestParam(required = false) String source) {
        return success(store.search(query, limit, source));
    }

    @GetMapping("/tools")
    public CommonResult<Map<String, Object>> tools() {
        return success(Map.of("tools", registry.list(), "readOnlyByDefault", true,
                "sensitiveActionsRequireConfirmation", true));
    }

    @GetMapping("/knowledge/status")
    public CommonResult<Map<String, Object>> status() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("documents", store.size());
        result.put("sources", store.sourceCounts());
        result.put("sourceVersions", store.sourceVersions());
        result.put("backend", store.backendName());
        result.put("backendAvailable", store.backendAvailable());
        result.put("backendHealth", store.backendHealth());
        result.put("persistent", store.persistent());
        result.put("embeddingEnabled", store.embeddingEnabled());
        result.put("embeddingDimensions", store.embeddingDimensions());
        result.put("pendingExternalUpserts", store.pendingExternalUpserts());
        result.put("pendingExternalDeletes", store.pendingExternalDeletes());
        result.put("ingestionDurable", ingestionQueue.durable());
        result.put("queueDepth", ingestionQueue.queueDepth());
        result.put("pendingDepth", ingestionQueue.pendingDepth());
        result.put("retryWaiting", ingestionQueue.retryWaiting());
        result.put("deadLetterDepth", ingestionQueue.deadLetterDepth());
        return success(Map.copyOf(result));
    }

    @PostMapping("/knowledge/documents")
    public CommonResult<Boolean> upsert(@Valid @RequestBody KnowledgeDocumentRequest request,
                                        @RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token) {
        requireInternal(token);
        store.upsertChunked(request.id(), request.source(), request.text(), request.metadata());
        return success(true);
    }

    @DeleteMapping("/knowledge/documents/{id}")
    public CommonResult<Boolean> remove(@PathVariable String id,
                                        @RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token) {
        requireInternal(token);
        store.remove(id);
        return success(true);
    }

    @PostMapping("/knowledge/reconcile")
    public CommonResult<Integer> reconcile(
            @RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token) {
        requireInternal(token);
        return success(store.reconcileExternalOperations());
    }

    @PostMapping("/knowledge/reindex")
    public CommonResult<Integer> reindex(@Valid @RequestBody ReindexRequest request,
                                         @RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token) {
        requireInternal(token);
        List<AgentKnowledgeStore.SourceDocument> documents = request.documents() == null ? List.of()
                : request.documents().stream().map(item -> new AgentKnowledgeStore.SourceDocument(item.id(), item.text(), item.metadata())).toList();
        return success(store.replaceSource(request.source(), documents));
    }

    @PostMapping("/knowledge/reindex/async")
    public CommonResult<AgentKnowledgeIngestionQueue.Job> reindexAsync(@Valid @RequestBody ReindexRequest request,
                                                                        @RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token) {
        requireInternal(token);
        List<AgentKnowledgeStore.SourceDocument> documents = request.documents() == null ? List.of()
                : request.documents().stream().map(item -> new AgentKnowledgeStore.SourceDocument(item.id(), item.text(), item.metadata())).toList();
        return success(ingestionQueue.submit(request.source(), documents));
    }

    @GetMapping("/knowledge/reindex/async/status")
    public CommonResult<AgentKnowledgeIngestionQueue.Job> reindexStatus(@RequestParam String jobId,
                                                                        @RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token) {
        requireInternal(token);
        AgentKnowledgeIngestionQueue.Job job = ingestionQueue.status(jobId);
        if (job == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "索引任务不存在");
        return success(job);
    }

    @PostMapping("/knowledge/reindex/async/retry")
    public CommonResult<Boolean> retry(@RequestParam String jobId,
                                       @RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token) {
        requireInternal(token);
        return success(ingestionQueue.retry(jobId));
    }

    @GetMapping("/knowledge/reindex/async/dead-letters")
    public CommonResult<List<AgentKnowledgeIngestionQueue.DeadLetter>> deadLetters(
            @RequestParam(defaultValue = "50") int limit,
            @RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token) {
        requireInternal(token);
        return success(ingestionQueue.deadLetters(limit));
    }

    @GetMapping("/knowledge/kafka/dlt")
    public CommonResult<List<AgentKnowledgeKafkaDltService.DeadLetter>> kafkaDeadLetters(
            @RequestParam(defaultValue = "0") int partition,
            @RequestParam(defaultValue = "0") long offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token) {
        requireInternal(token);
        requireKafkaDlt();
        return success(kafkaDlt.list(partition, offset, limit));
    }

    @PostMapping("/knowledge/kafka/dlt/replay")
    public CommonResult<AgentKnowledgeKafkaDltService.ReplayResult> replayKafkaDeadLetter(
            @Valid @RequestBody KafkaDltReplayRequest request,
            @RequestHeader(value = "X-Curmerce-Internal-Token", required = false) String token) {
        requireInternal(token);
        requireKafkaDlt();
        return success(kafkaDlt.replay(request.partition(), request.offset()));
    }

    public record KnowledgeDocumentRequest(@NotBlank String id, @NotBlank String source, @NotBlank String text, com.fasterxml.jackson.databind.JsonNode metadata) { }
    public record ReindexRequest(@NotBlank String source, List<KnowledgeDocumentRequest> documents) { }
    public record KafkaDltReplayRequest(int partition, long offset) { }

    private void requireInternal(String token) {
        if (!AgentInternalAuthorizer.matches(properties.internalToken(), token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅允许内部知识索引调用");
        }
    }

    private void requireKafkaDlt() {
        if (kafkaDlt == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Kafka 知识投影未启用");
    }
}
