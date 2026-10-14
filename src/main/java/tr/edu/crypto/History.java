package tr.edu.crypto;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.List;

public final class History {
    private final ArrayDeque<Map<String, Object>> entries = new ArrayDeque<>();
    private long nextId;

    // Bellek kullanimini sinirlamak icin son 300 kaydi saklariz.
    public synchronized void add(String kind, String sender, String text) {
        if (entries.size() == 300) entries.removeFirst();
        entries.addLast(Map.of("id", ++nextId, "time", Instant.now().toString(),
                "kind", kind, "sender", sender, "text", text));
    }

    public synchronized List<Map<String, Object>> snapshot() {
        return List.copyOf(entries);
    }
}
