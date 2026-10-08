package ru.practicum.ewm.stats.aggregator;

import org.junit.jupiter.api.Test;
import ru.practicum.ewm.stats.ActionWeights;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SimilarityCalculatorTest {
    private final SimilarityCalculator calculator = new SimilarityCalculator();

    @Test
    void firstEventHasNoPairs() {
        assertThat(calculator.update(action(1, 10, ActionTypeAvro.VIEW))).isEmpty();
    }

    @Test
    void ordersPairsAndUsesMinimumWeight() {
        calculator.update(action(1, 20, ActionTypeAvro.LIKE));
        List<EventSimilarityAvro> result = calculator.update(action(1, 10, ActionTypeAvro.REGISTER));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getEventA()).isEqualTo(10);
        assertThat(result.getFirst().getEventB()).isEqualTo(20);
        assertThat(result.getFirst().getScore()).isCloseTo(0.8 / Math.sqrt(0.8), within(1e-12));
    }

    @Test
    void repeatedAndWeakerActionsDoNotChangeWeights() {
        calculator.update(action(1, 10, ActionTypeAvro.REGISTER));
        assertThat(calculator.update(action(1, 10, ActionTypeAvro.VIEW))).isEmpty();
        assertThat(calculator.update(action(1, 10, ActionTypeAvro.REGISTER))).isEmpty();
    }

    @Test
    void updatesDenominatorEvenWithoutCommonUser() {
        calculator.update(action(1, 10, ActionTypeAvro.LIKE));
        calculator.update(action(1, 20, ActionTypeAvro.LIKE));
        var result = calculator.update(action(2, 10, ActionTypeAvro.LIKE));
        assertThat(result.getFirst().getScore()).isCloseTo(1 / Math.sqrt(2), within(1e-12));
    }

    @Test
    void updatesDenominatorWhenMinimumDoesNotChange() {
        calculator.update(action(1, 10, ActionTypeAvro.REGISTER));
        calculator.update(action(1, 20, ActionTypeAvro.VIEW));
        var result = calculator.update(action(1, 10, ActionTypeAvro.LIKE));
        assertThat(result.getFirst().getScore()).isCloseTo(0.4 / Math.sqrt(0.4), within(1e-12));
    }

    @Test
    void clearRebuildsTheSameState() {
        calculator.update(action(1, 10, ActionTypeAvro.LIKE));
        double score = calculator.update(action(1, 20, ActionTypeAvro.VIEW)).getFirst().getScore();
        calculator.clear();
        calculator.update(action(1, 10, ActionTypeAvro.LIKE));
        assertThat(calculator.update(action(1, 20, ActionTypeAvro.VIEW)).getFirst().getScore()).isEqualTo(score);
    }

    @Test
    void incrementalResultsMatchFullFormulaForRandomStream() {
        Random random = new Random(12345);
        Map<Long, Map<Long, Double>> matrix = new HashMap<>();
        for (int index = 0; index < 1000; index++) {
            long user = random.nextInt(15) + 1;
            long event = random.nextInt(8) + 1;
            ActionTypeAvro type = ActionTypeAvro.values()[random.nextInt(3)];
            matrix.computeIfAbsent(event, key -> new HashMap<>()).merge(user, ActionWeights.weight(type), Math::max);
            for (EventSimilarityAvro pair : calculator.update(action(user, event, type))) {
                var first = matrix.get(pair.getEventA());
                var second = matrix.get(pair.getEventB());
                double minimum = first.entrySet().stream()
                        .mapToDouble(entry -> Math.min(entry.getValue(), second.getOrDefault(entry.getKey(), 0.0))).sum();
                double totalA = first.values().stream().mapToDouble(Double::doubleValue).sum();
                double totalB = second.values().stream().mapToDouble(Double::doubleValue).sum();
                assertThat(pair.getScore()).isCloseTo(minimum / Math.sqrt(totalA * totalB), within(1e-12));
                assertThat(pair.getEventA()).isLessThan(pair.getEventB());
            }
        }
    }

    private UserActionAvro action(long user, long event, ActionTypeAvro type) {
        return new UserActionAvro(user, event, type, Instant.ofEpochSecond(100));
    }
}
