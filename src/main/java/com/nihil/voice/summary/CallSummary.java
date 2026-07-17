package com.nihil.voice.summary;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record CallSummary(String shortSummary, String fullSummary, String topic, String intent, String sentiment,
                          String leadQuality, String outcome, Contact contact, List<RequestedItem> requestedItems,
                          NextAction nextAction, Map<String, Object> extractedFacts) {
    public CallSummary {
        if (shortSummary == null || shortSummary.isBlank()) throw new IllegalArgumentException("shortSummary is required");
        requestedItems = requestedItems == null ? List.of() : List.copyOf(requestedItems);
        extractedFacts = extractedFacts == null ? Map.of() : Map.copyOf(extractedFacts);
    }

    public CallSummary(String shortSummary, String fullSummary, String intent, String sentiment,
                       String leadQuality, String outcome, Contact contact, List<RequestedItem> requestedItems,
                       NextAction nextAction, Map<String, Object> extractedFacts) {
        this(shortSummary, fullSummary, null, intent, sentiment, leadQuality, outcome, contact, requestedItems, nextAction, extractedFacts);
    }
    public record Contact(String name, String phone) {}
    public record RequestedItem(String name, int quantity) {
        public RequestedItem { if (name == null || name.isBlank() || quantity < 1) throw new IllegalArgumentException("Invalid requested item"); }
    }
    public record NextAction(String type, OffsetDateTime dueAt) {}
}
