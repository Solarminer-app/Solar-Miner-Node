CREATE TABLE agent_miner_entity
(
    id   UUID         NOT NULL,
    host VARCHAR(255) NULL,
    port INT          NOT NULL,
    CONSTRAINT pk_agentminerentity PRIMARY KEY (id)
);

ALTER TABLE agent_miner_entity
    ADD CONSTRAINT FK_AGENTMINERENTITY_ON_ID FOREIGN KEY (id) REFERENCES miners (id);