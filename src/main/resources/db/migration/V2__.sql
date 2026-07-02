CREATE TABLE antminer_entity
(
    id       UUID         NOT NULL,
    host     VARCHAR(255) NULL,
    username VARCHAR(255) NULL,
    password VARCHAR(255) NULL,
    port     INT          NOT NULL,
    CONSTRAINT pk_antminerentity PRIMARY KEY (id)
);

ALTER TABLE antminer_entity
    ADD CONSTRAINT FK_ANTMINERENTITY_ON_ID FOREIGN KEY (id) REFERENCES miners (id);