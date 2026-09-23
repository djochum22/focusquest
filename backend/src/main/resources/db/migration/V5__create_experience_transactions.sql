CREATE TABLE experience_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    amount INT NOT NULL,
    type VARCHAR(40) NOT NULL,
    reference_type VARCHAR(30) NOT NULL,
    reference_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_experience_transactions_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_experience_transactions_user_created ON experience_transactions (user_id, created_at);

-- At most one transaction of a given type per referenced record, so a retried request can
-- never award or penalize the same session twice.
CREATE UNIQUE INDEX idx_experience_transactions_reference
    ON experience_transactions (user_id, type, reference_type, reference_id);
