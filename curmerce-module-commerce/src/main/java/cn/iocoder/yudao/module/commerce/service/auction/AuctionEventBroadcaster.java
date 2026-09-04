package cn.iocoder.yudao.module.commerce.service.auction;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-process SSE fan-out; extracted deployments can replace this with Kafka/WebSocket. */
@Component
public class AuctionEventBroadcaster {
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long sessionId) {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.computeIfAbsent(sessionId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(sessionId, emitter));
        emitter.onTimeout(() -> remove(sessionId, emitter));
        emitter.onError(error -> remove(sessionId, emitter));
        try { emitter.send(SseEmitter.event().name("connected").data(Map.of("sessionId", sessionId))); }
        catch (IOException ex) { remove(sessionId, emitter); }
        return emitter;
    }

    public void publish(Long sessionId, String event, Object payload) {
        var listeners = subscribers.get(sessionId);
        if (listeners == null) return;
        for (SseEmitter emitter : listeners) {
            try { emitter.send(SseEmitter.event().name(event).data(payload)); }
            catch (IOException ex) { remove(sessionId, emitter); }
        }
    }

    private void remove(Long sessionId, SseEmitter emitter) {
        var listeners = subscribers.get(sessionId);
        if (listeners != null) { listeners.remove(emitter); if (listeners.isEmpty()) subscribers.remove(sessionId, listeners); }
    }
}
