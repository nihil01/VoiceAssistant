package com.nihil.voice.summary;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CallSummaryTest {
    @Test void rejectsMissingSummary() {
        assertThatThrownBy(() -> new CallSummary(" ", null, null, null, null, null, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsInvalidRequestedItemQuantity() {
        assertThatThrownBy(() -> new CallSummary.RequestedItem("Shock absorber", 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
