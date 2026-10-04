CREATE TABLE IF NOT EXISTS user_interactions (
    user_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    weight DOUBLE PRECISION NOT NULL,
    interacted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, event_id)
);
CREATE INDEX IF NOT EXISTS idx_interactions_event ON user_interactions (event_id);
CREATE INDEX IF NOT EXISTS idx_interactions_recent ON user_interactions (user_id, interacted_at DESC);
CREATE TABLE IF NOT EXISTS event_similarities (
    event_a BIGINT NOT NULL,
    event_b BIGINT NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (event_a, event_b),
    CHECK (event_a < event_b)
);
CREATE INDEX IF NOT EXISTS idx_similarities_event_b ON event_similarities (event_b);
