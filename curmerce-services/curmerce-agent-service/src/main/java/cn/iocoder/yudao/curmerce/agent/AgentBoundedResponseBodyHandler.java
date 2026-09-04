package cn.iocoder.yudao.curmerce.agent;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/**
 * Bounds provider response buffering before the HTTP client materializes a
 * String.  {@code BodyHandlers.ofString()} buffers the complete response and
 * checking its size afterwards is too late for an untrusted model endpoint.
 */
final class AgentBoundedResponseBodyHandler {
    private AgentBoundedResponseBodyHandler() { }

    static HttpResponse.BodyHandler<String> utf8(int maxBytes) {
        int limit = Math.max(1, maxBytes);
        return ignored -> new Subscriber(limit);
    }

    private static final class Subscriber implements HttpResponse.BodySubscriber<String> {
        private final int maxBytes;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<String> body = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private int received;

        private Subscriber(int maxBytes) { this.maxBytes = maxBytes; }

        @Override
        public CompletionStage<String> getBody() { return body; }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) {
                subscription.cancel();
                return;
            }
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            if (body.isDone() || items == null) return;
            for (ByteBuffer item : items) {
                if (item == null) continue;
                int size = item.remaining();
                if (size > maxBytes - received) {
                    if (subscription != null) subscription.cancel();
                    body.completeExceptionally(new ResponseTooLargeException(maxBytes));
                    return;
                }
                byte[] chunk = new byte[size];
                item.get(chunk);
                bytes.write(chunk, 0, chunk.length);
                received += size;
            }
        }

        @Override
        public void onError(Throwable throwable) { body.completeExceptionally(throwable); }

        @Override
        public void onComplete() {
            body.complete(bytes.toString(StandardCharsets.UTF_8));
        }
    }

    static final class ResponseTooLargeException extends RuntimeException {
        ResponseTooLargeException(int maxBytes) { super("provider response exceeds " + maxBytes + " bytes"); }
    }
}
