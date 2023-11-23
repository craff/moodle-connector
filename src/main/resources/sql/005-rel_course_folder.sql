CREATE TABLE moodleNati.rel_course_folder
             (
                          folder_id BIGINT  ,
                          course_id BIGINT,
                          CONSTRAINT folder_fk FOREIGN KEY (folder_id) REFERENCES moodleNati.folder(id) ON DELETE CASCADE,
                          CONSTRAINT course_fk FOREIGN KEY (course_id) REFERENCES moodleNati.course(moodle_id)  ON DELETE CASCADE
             );


DELETE FROM moodleNati.course
WHERE folder_id NOT IN (SELECT id from moodleNati.folder);

INSERT INTO moodleNati.rel_course_folder ( folder_id, course_id )
SELECT folder_id , moodle_id
FROM   moodleNati.course
where folder_id != 0;

UPDATE moodleNati.folder
SET parent_id = NULL
WHERE  parent_id = 0;

DELETE from moodleNati.folder
WHERE id = 0 ;

ALTER TABLE moodleNati.course
DROP COLUMN folder_id ;

ALTER TABLE moodleNati.folder
DROP COLUMN structure_id;
