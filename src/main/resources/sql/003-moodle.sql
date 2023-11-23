CREATE TABLE moodleNati.users (
	id VARCHAR(36) NOT NULL PRIMARY KEY,
	username VARCHAR(255)
);

CREATE TABLE moodleNati.groups (
	id VARCHAR(36) NOT NULL PRIMARY KEY,
	name VARCHAR(255)
);

CREATE TABLE moodleNati.members (
	id VARCHAR(36) NOT NULL PRIMARY KEY,
	user_id VARCHAR(36),
	group_id VARCHAR(36),
	CONSTRAINT user_fk FOREIGN KEY(user_id) REFERENCES moodleNati.users(id) ON UPDATE CASCADE ON DELETE CASCADE,
	CONSTRAINT group_fk FOREIGN KEY(group_id) REFERENCES moodleNati.groups(id) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE moodleNati.course_shares (
	member_id VARCHAR(36) NOT NULL,
	resource_id BIGINT NOT NULL,
	action VARCHAR(255) NOT NULL,
	CONSTRAINT course_shares_pk PRIMARY KEY (member_id, resource_id, action),
	CONSTRAINT course_shares_resource_fk FOREIGN KEY(resource_id) REFERENCES moodleNati.course(moodle_id) ON UPDATE CASCADE ON DELETE NO ACTION,
	CONSTRAINT course_shares_member_fk FOREIGN KEY(member_id) REFERENCES moodleNati.members(id) ON UPDATE CASCADE ON DELETE CASCADE
);


CREATE FUNCTION moodleNati.merge_users(key VARCHAR, data VARCHAR) RETURNS VOID AS
$$
BEGIN
    LOOP
        UPDATE moodleNati.users SET username = data WHERE id = key;
        IF found THEN
            RETURN;
        END IF;
        BEGIN
            INSERT INTO moodleNati.users(id,username) VALUES (key, data);
            RETURN;
        EXCEPTION WHEN unique_violation THEN
        END;
    END LOOP;
END;
$$
LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION moodleNati.insert_users_members() RETURNS TRIGGER AS $$
    BEGIN
		IF (TG_OP = 'INSERT') THEN
            INSERT INTO moodleNati.members (id, user_id) VALUES (NEW.id, NEW.id);
            RETURN NEW;
        END IF;
        RETURN NULL;
    END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION moodleNati.insert_groups_members() RETURNS TRIGGER AS $$
    BEGIN
		IF (TG_OP = 'INSERT') THEN
            INSERT INTO moodleNati.members (id, group_id) VALUES (NEW.id, NEW.id);
            RETURN NEW;
        END IF;
        RETURN NULL;
    END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_trigger
AFTER INSERT ON moodleNati.users
    FOR EACH ROW EXECUTE PROCEDURE moodleNati.insert_users_members();

CREATE TRIGGER groups_trigger
AFTER INSERT ON moodleNati.groups
    FOR EACH ROW EXECUTE PROCEDURE moodleNati.insert_groups_members();

CREATE TYPE moodleNati.share_tuple as (member_id VARCHAR(36), action VARCHAR(255));
