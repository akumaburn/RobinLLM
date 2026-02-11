package com.robinllm.router;

import com.robinllm.dto.OpenAIChatStreamResponse;
import com.robinllm.model.LLMModel;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Holds the result of a racing stream request including the stream itself,
 * the winning model, and cancellation control for the race.
 */
public class StreamRaceResult {
    private final Stream<OpenAIChatStreamResponse> stream;
    private final LLMModel model;
    private final CompletableFuture<Void> completionFuture;
    private final AtomicBoolean cancelled;
    private final Runnable onCancel;

    public StreamRaceResult(Stream<OpenAIChatStreamResponse> stream, LLMModel model, 
                           CompletableFuture<Void> completionFuture, Runnable onCancel) {
        this.stream = stream;
        this.model = model;
        this.completionFuture = completionFuture;
        this.cancelled = new AtomicBoolean(false);
        this.onCancel = onCancel;
    }

    public Stream<OpenAIChatStreamResponse> getStream() {
        return stream;
    }

    public LLMModel getModel() {
        return model;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            completionFuture.cancel(true);
            if (onCancel != null) {
                onCancel.run();
            }
        }
    }

    public CompletableFuture<Void> getCompletionFuture() {
        return completionFuture;
    }
}
