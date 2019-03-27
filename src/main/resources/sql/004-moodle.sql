CREATE TABLE moodle.duplication (
  id BIGSERIAL NOT NULL,
  id_course BIGINT NOT NULL,
  id_folder BIGINT NOT NULL,
  id_users VARCHAR NOT NULL,
  status VARCHAR NOT NULL,
  CONSTRAINT duplication_pkey PRIMARY KEY (id)
);
