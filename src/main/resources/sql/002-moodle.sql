BEGIN;
INSERT INTO moodle.folder(
	id, parent_id, user_id, structure_id, name)
	VALUES (0, 0, 'admin', 'structure_null', 'root');

INSERT INTO moodle.choices(
	view, choice, user_id)
	VALUES ('lastCreation', true, '7a0eff20-1dc3-4cf4-9174-ece177e6b7f5');

END;