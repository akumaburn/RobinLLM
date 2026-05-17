package com.robinllm.client;

import com.robinllm.dto.OpenAIChatStreamResponse;
import okhttp3.Call;
import okhttp3.Response;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A wrapper around a streaming response that allows cancellation of the underlying HTTP request.
 * This class maintains the iterator separately so it can be used for racing and then
 * converted to a stream for the response.
 */
public class CancellableStream implements Closeable {
    private final Iterator<OpenAIChatStreamResponse> iterator;
    private final Call call;
    private final Response response;
    private final BufferedReader reader;
    private final AtomicBoolean cancelled;
    private final AtomicBoolean firstChunkReceived;
    private final String modelId;
    private Stream<OpenAIChatStreamResponse> stream;

    public CancellableStream(Call call, Response response, BufferedReader reader, 
                              Iterator<OpenAIChatStreamResponse> iterator, String modelId) {
        this.call = call;
        this.response = response;
        this.reader = reader;
        this.iterator = iterator;
        this.cancelled = new AtomicBoolean(false);
        this.firstChunkReceived = new AtomicBoolean(false);
        this.modelId = modelId;
        this.stream = null;
    }

    /**
     * Get the raw iterator for racing. The iterator should be used to consume
     * the stream, and then getStream() can be called to get a Stream wrapper
     * for the remaining elements.
     */
    public Iterator<OpenAIChatStreamResponse> getIterator() {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                if (cancelled.get()) return false;
                return iterator.hasNext();
            }

            @Override
            public OpenAIChatStreamResponse next() {
                if (cancelled.get()) {
                    throw new java.util.NoSuchElementException("Stream has been cancelled");
                }
                OpenAIChatStreamResponse chunk = iterator.next();
                firstChunkReceived.set(true);
                chunk.setModel(modelId);
                return chunk;
            }
        };
    }

    /**
     * Get a Stream view of the remaining elements.
     * Can only be called once - subsequent calls will return the same stream.
     */
    public Stream<OpenAIChatStreamResponse> getStream() {
        if (stream == null) {
            stream = StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(getIterator(), Spliterator.ORDERED),
                false
            ).onClose(this::close);
        }
        return stream;
    }

    /**
     * Check if this stream has received its first chunk (i.e., won the race)
     */
    public boolean hasReceivedFirstChunk() {
        return firstChunkReceived.get();
    }

    /**
     * Cancel this stream and close all resources
     */
    @Override
    public void close() {
        if (cancelled.compareAndSet(false, true)) {
            try {
                // Cancel the HTTP call
                if (call != null) {
                    call.cancel();
                }
            } catch (Exception e) {
                // Ignore cancellation errors
            }
            
            try {
                // Close the reader
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                // Ignore close errors
            }
            
            try {
                // Close the response
                if (response != null) {
                    response.close();
                }
            } catch (Exception e) {
                // Ignore close errors
            }
        }
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public String getModelId() {
        return modelId;
    }

    /**
     * The underlying okhttp Call. Exposed so a race coordinator can identify
     * the winner's Call and skip it while cancelling losers.
     */
    public Call getCall() {
        return call;
    }
}
