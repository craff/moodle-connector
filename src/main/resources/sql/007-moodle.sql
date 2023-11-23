CREATE TABLE moodleNati.publication (
  id BIGSERIAL NOT NULL,
  discipline_label VARCHAR ARRAY,
  level_label VARCHAR ARRAY,
  key_words VARCHAR ARRAY,
  fullName VARCHAR NOT NULL,
  imageUrl VARCHAR NOT NULL,
  summary VARCHAR NOT NULL,
  author VARCHAR NOT NULL,
  author_id VARCHAR NOT NULL,
  user_id VARCHAR NOT NULL,
  username VARCHAR NOT NULL,
  license BOOLEAN NOT NULL,
  course_id BIGINT,
  CONSTRAINT publicCourse_pk PRIMARY KEY (id),
  CONSTRAINT course_fk FOREIGN KEY (course_id) REFERENCES moodleNati.course(moodle_id)
);

ALTER TABLE moodleNati.duplication
    ADD category_id BIGINT,
    ADD auditeur VARCHAR,
    ADD publishFK BIGINT,
    ADD CONSTRAINT publication_fk FOREIGN KEY (publishFK) REFERENCES moodleNati.publication(id);
