CREATE TABLE moodleNati.duplication (
  id BIGSERIAL NOT NULL,
  id_course BIGINT NOT NULL,
  id_folder BIGINT NOT NULL,
  id_users VARCHAR NOT NULL,
  status VARCHAR NOT NULL,
  date TIMESTAMP NOT NULL DEFAULT NOW(),
  nombre_tentatives BIGINT NOT NULL DEFAULT 1,
  CONSTRAINT duplication_pkey PRIMARY KEY (id)
);
