CREATE TABLE moodle.rel_course_folder
             (
                          folder_id BIGINT  ,
                          course_id BIGINT,
                          CONSTRAINT folder_fk FOREIGN KEY (folder_id) REFERENCES moodle.folder(id) ON DELETE CASCADE,
                          CONSTRAINT course_fk FOREIGN KEY (course_id) REFERENCES moodle.course(moodle_id)  ON DELETE CASCADE
             );


DELETE FROM moodle.course
WHERE folder_id NOT IN (SELECT id from moodle.folder);

INSERT INTO moodle.rel_course_folder ( folder_id, course_id )
SELECT folder_id , moodle_id
FROM   moodle.course
where folder_id != 0;

UPDATE moodle.folder
SET parent_id = NULL
WHERE  parent_id = 0;

DELETE from moodle.folder
WHERE id = 0 ;

ALTER TABLE moodle.course
DROP COLUMN folder_id ;

ALTER TABLE moodle.folder
DROP COLUMN structure_id;