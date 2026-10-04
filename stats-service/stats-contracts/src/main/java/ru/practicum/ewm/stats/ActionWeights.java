package ru.practicum.ewm.stats;

import ru.practicum.ewm.stats.avro.ActionTypeAvro;

public final class ActionWeights {
    private ActionWeights() {
    }

    public static double weight(ActionTypeAvro action) {
        return switch (action) {
            case VIEW -> 0.4;
            case REGISTER -> 0.8;
            case LIKE -> 1.0;
        };
    }
}
