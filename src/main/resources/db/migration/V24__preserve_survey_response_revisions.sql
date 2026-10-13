CREATE TABLE survey_response_revisions (
    revision_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    survey_response_id BIGINT NOT NULL,
    revision_number INT NOT NULL,
    submitted_at DATETIME NOT NULL,
    consent_status_at_submission VARCHAR(20) NOT NULL,
    answers_snapshot JSON NOT NULL,
    archived_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_survey_response_revision UNIQUE (survey_response_id, revision_number),
    CONSTRAINT fk_survey_revision_response FOREIGN KEY (survey_response_id)
        REFERENCES survey_responses (survey_response_id)
);
