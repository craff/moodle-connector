BEGIN;
INSERT INTO moodle.folder(
	id, parent_id, user_id, structure_id, name)
	VALUES (0, 0, 'admin', 'structure_null', 'root');
END;
