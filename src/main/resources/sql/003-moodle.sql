CREATE TABLE moodle.users (
	id VARCHAR(36) NOT NULL PRIMARY KEY,
	username VARCHAR(255)
);

CREATE TABLE moodle.groups (
	id VARCHAR(36) NOT NULL PRIMARY KEY,
	name VARCHAR(255)
);

CREATE TABLE moodle.members (
	id VARCHAR(36) NOT NULL PRIMARY KEY,
	user_id VARCHAR(36),
	group_id VARCHAR(36),
	CONSTRAINT user_fk FOREIGN KEY(user_id) REFERENCES moodle.users(id) ON UPDATE CASCADE ON DELETE CASCADE,
	CONSTRAINT group_fk FOREIGN KEY(group_id) REFERENCES moodle.groups(id) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE moodle.course_shares (
	member_id VARCHAR(36) NOT NULL,
	resource_id BIGINT NOT NULL,
	action VARCHAR(255) NOT NULL,
	CONSTRAINT course_shares_pk PRIMARY KEY (member_id, resource_id, action),
	CONSTRAINT course_shares_resource_fk FOREIGN KEY(resource_id) REFERENCES moodle.course(moodle_id) ON UPDATE CASCADE ON DELETE NO ACTION,
	CONSTRAINT course_shares_member_fk FOREIGN KEY(member_id) REFERENCES moodle.members(id) ON UPDATE CASCADE ON DELETE CASCADE
);


CREATE FUNCTION moodle.merge_users(key VARCHAR, data VARCHAR) RETURNS VOID AS
$$
BEGIN
    LOOP
        UPDATE moodle.users SET username = data WHERE id = key;
        IF found THEN
            RETURN;
        END IF;
        BEGIN
            INSERT INTO moodle.users(id,username) VALUES (key, data);
            RETURN;
        EXCEPTION WHEN unique_violation THEN
        END;
    END LOOP;
END;
$$
LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION moodle.insert_users_members() RETURNS TRIGGER AS $$
    BEGIN
		IF (TG_OP = 'INSERT') THEN
            INSERT INTO moodle.members (id, user_id) VALUES (NEW.id, NEW.id);
            RETURN NEW;
        END IF;
        RETURN NULL;
    END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION moodle.insert_groups_members() RETURNS TRIGGER AS $$
    BEGIN
		IF (TG_OP = 'INSERT') THEN
            INSERT INTO moodle.members (id, group_id) VALUES (NEW.id, NEW.id);
            RETURN NEW;
        END IF;
        RETURN NULL;
    END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_trigger
AFTER INSERT ON moodle.users
    FOR EACH ROW EXECUTE PROCEDURE moodle.insert_users_members();

CREATE TRIGGER groups_trigger
AFTER INSERT ON moodle.groups
    FOR EACH ROW EXECUTE PROCEDURE moodle.insert_groups_members();

CREATE TYPE moodle.share_tuple as (member_id VARCHAR(36), action VARCHAR(255));