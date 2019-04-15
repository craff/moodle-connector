CREATE SCHEMA moodle;

CREATE TABLE moodle.scripts (
  filename character varying(255) NOT NULL,
  passed timestamp without time zone NOT NULL DEFAULT now(),
  CONSTRAINT scripts_pkey PRIMARY KEY (filename)
);

CREATE TABLE moodle.folder (
  id bigserial NOT NULL,
  parent_id bigint,
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
  folder_id bigint,
  user_id character varying(36) NOT NULL,
  CONSTRAINT course_pkey PRIMARY KEY (moodle_id)
);

CREATE TABLE moodle.choices (
  user_id character varying(36) NOT NULL,
  lastCreation boolean,
  toDo boolean,
  toCome boolean,
  coursestodosort character varying(36),
  coursestocomesort character varying(36),
  CONSTRAINT choices_pkey PRIMARY KEY (user_id)
);

CREATE TABLE moodle.preferences (
   id bigserial NOT NULL,
   moodle_id bigserial NOT NULL,
   user_id character varying(36) NOT NULL,
   masked boolean NOT NULL,
   favorites boolean NOT NULL,
   CONSTRAINT preferences_pkey PRIMARY KEY (id),
   unique (moodle_id, user_id)
);
