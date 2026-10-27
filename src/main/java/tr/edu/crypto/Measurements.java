package tr.edu.crypto;

import java.time.Instant;
import java.util.*;
import java.util.function.ToDoubleFunction;

// Yalnizca tamamlanan on tekrarlik deneyleri saklar; eksik deneyi ortalamaya katmaz.
public final class Measurements {
    private record Batch(String endpoint, String time, List<KeyExchange.Measurement> samples) {}
    private final Map<String, Batch> batches = new LinkedHashMap<>();
    public synchronized void save(String method, String endpoint, List<KeyExchange.Measurement> samples) {
        if (samples.size() != 10 || samples.stream().anyMatch(sample -> !sample.method().equals(method)))
            throw new IllegalArgumentException("Deney 10 aynı yöntem ölçümü içermeli.");
        batches.put(method, new Batch(endpoint, Instant.now().toString(), List.copyOf(samples)));
    }
    public synchronized List<Map<String, Object>> snapshot() {
        return Crypto.METHODS.stream().map(method -> {
            Batch batch = batches.get(method);
            Map<String, Object> row = new LinkedHashMap<>(); row.put("method", method);
            row.put("count", batch == null ? 0 : batch.samples().size());
            if (batch != null) {
                row.put("endpoint", batch.endpoint()); row.put("time", batch.time());
                row.put("serverKeyMs", average(batch, KeyExchange.Measurement::serverKeyMs));
                row.put("clientKeyMs", average(batch, KeyExchange.Measurement::clientKeyMs));
                row.put("exchangeMs", average(batch, KeyExchange.Measurement::exchangeMs));
                row.put("keyPayloadBytes", average(batch, KeyExchange.Measurement::keyPayloadBytes));
                row.put("totalBytes", average(batch, sample -> sample.sentBytes() + sample.receivedBytes()));
            }
            return row;
        }).toList();
    }
    private static double average(Batch batch, ToDoubleFunction<KeyExchange.Measurement> field) {
        return batch.samples().stream().mapToDouble(field).average().orElseThrow();
    }
    public synchronized String csv() {
        StringBuilder text = new StringBuilder("method,endpoint,time,repeat,server_key_ms,client_key_ms,exchange_ms,key_payload_bytes,client_sent_bytes,client_received_bytes,total_bytes\n");
        batches.forEach((method, batch) -> {
            int index = 0;
            for (KeyExchange.Measurement sample : batch.samples()) {
                text.append(method).append(',').append(csvCell(batch.endpoint())).append(',').append(batch.time()).append(',')
                        .append(++index).append(',').append(sample.serverKeyMs()).append(',').append(sample.clientKeyMs()).append(',')
                        .append(sample.exchangeMs()).append(',').append(sample.keyPayloadBytes()).append(',').append(sample.sentBytes())
                        .append(',').append(sample.receivedBytes()).append(',').append(sample.sentBytes() + sample.receivedBytes()).append('\n');
            }
        });
        return text.toString();
    }
    private static String csvCell(String text) {
        // Elektronik tablo formulu olarak yorumlanabilecek kullanici degerlerini metin yapariz.
        if (text.matches("^[=+@\\-].*")) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
