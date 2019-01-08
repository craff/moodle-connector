CREATE SCHEMA moodle;

CREATE TABLE moodle.folder (
  id bigserial NOT NULL,
  parent_id bigserial,
  user_id character varying(36) NOT NULL,
  structure_id character varying(36) NOT NULL,
  name character varying (255) NOT NULL,
  CONSTRAINT folder_pkey PRIMARY KEY (id),
  CONSTRAINT folders_parent_id_fkey FOREIGN KEY (parent_id)
  REFERENCES moodle.folder (id) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE CASCADE
);

CREATE TABLE moodle.course (
  moodle_id bigserial NOT NULL,
  folder_id bigserial,
  user_id character varying(36) NOT NULL,
  CONSTRAINT course_pkey PRIMARY KEY (moodle_id)--,
--   CONSTRAINT fk_folder_id FOREIGN KEY (folder_id)
--     REFERENCES moodle.folder (id) MATCH SIMPLE
--     ON UPDATE NO ACTION
--     ON DELETE CASCADE
);
