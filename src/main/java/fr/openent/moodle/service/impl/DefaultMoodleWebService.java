package fr.openent.moodle.service.impl;


import fr.openent.moodle.Moodle;
import fr.openent.moodle.service.MoodleWebService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.neo4j.Neo4jResult;
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;

import static fr.openent.moodle.Moodle.*;

public class DefaultMoodleWebService extends SqlCrudService implements MoodleWebService {

    public DefaultMoodleWebService(String schema, String table) {
        super(schema, table);
    }

    @Override
    public void createFolder(final JsonObject folder, final Handler<Either<String, JsonObject>> handler){
        JsonArray values = new JsonArray();
        values.add(folder.getValue("userId"));
        values.add(folder.getValue("structureId"));
        values.add(folder.getValue("name"));
        values.add(folder.getValue("parentId"));

        String createFolder = "INSERT INTO " + Moodle.moodleSchema + ".folder(user_id, structure_id, name, parent_id)" +
                    " VALUES (?, ?, ?, ?)";
            sql.prepared(createFolder,values , SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void deleteFolders(final JsonObject folder, final Handler<Either<String, JsonObject>> handler) {
        JsonArray values = new JsonArray();
        JsonArray folders = folder.getJsonArray("foldersId");

        for (int i = 0; i < folders.size(); i++) {
            values.add(folders.getValue(i));
        }

        String deleteFolders = "DELETE FROM " + Moodle.moodleSchema + ".folder" + " WHERE id IN " + Sql.listPrepared(folders.getList());

        sql.prepared(deleteFolders, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void moveFolder(final JsonObject folder, final Handler<Either<String, JsonObject>> handler){
        JsonArray values = new JsonArray();
        JsonArray folders = folder.getJsonArray("foldersId");
        values.add(folder.getValue("parentId"));

        for (int i = 0;i<folders.size();i++){
            values.add(folders.getValue(i));
        }

        String moveFolder = "UPDATE " + Moodle.moodleSchema + ".folder SET parent_id = ? WHERE id IN " + Sql.listPrepared(folders.getList());

        sql.prepared(moveFolder,values , SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void createCourse(final JsonObject course, final Handler<Either<String, JsonObject>> handler){
        String createCourse = "INSERT INTO " + Moodle.moodleSchema + ".course(moodle_id, folder_id, user_id)" +
                " VALUES (?, ?, ?) RETURNING moodle_id as id";

        JsonArray values = new JsonArray();
        values.add(course.getValue("moodleid"));

        Integer folderId = course.getInteger("folderid");
        if (folderId == null) {
            values.addNull();
        } else {
            values.add(folderId);
        }
        values.add(course.getString("userid"));

        sql.prepared(createCourse, values, SqlResult.validUniqueResultHandler(handler));
    }


    @Override
    public void getPreferences( String id_user, final Handler<Either<String, JsonArray>> handler) {
        String getCoursespreferences = "SELECT moodle_id, masked, favorites FROM " + Moodle.moodleSchema + ".preferences" + " WHERE user_id = ?;";
        JsonArray value = new JsonArray();
        value.add(id_user);
        sql.prepared(getCoursespreferences, value, SqlResult.validResultHandler(handler));
    }

    @Override
    public void setPreferences(final JsonObject course, final Handler<Either<String, JsonObject>> handler) {
        String query = "INSERT INTO " + Moodle.moodleSchema + ".preferences (moodle_id, user_id, masked, favorites)" +
                " VALUES (?, ?, ?, ?) ON CONFLICT (moodle_id, user_id) DO UPDATE SET masked =" + course.getBoolean("masked")+
                ", favorites ="+course.getBoolean("favorites")+" ; "+
                "DELETE FROM " + Moodle.moodleSchema + ".preferences WHERE masked=false AND favorites=false;";

        JsonArray values = new JsonArray();
        values.add(course.getInteger("courseid"));
        values.add(course.getString("userId"));
        values.add(course.getBoolean("masked"));
        values.add(course.getBoolean("favorites"));


        sql.prepared(query, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void moveCourse(final JsonObject course, final Handler<Either<String, JsonObject>> handler){
        JsonArray values = new JsonArray();
        JsonArray courses = course.getJsonArray("coursesId");
        values.add(course.getValue("folderId"));

        for (int i = 0;i<courses.size();i++){
            values.add(courses.getValue(i));
        }

        String moveCourse = "UPDATE " + Moodle.moodleSchema + ".course SET folder_id = ? WHERE moodle_id IN " + Sql.listPrepared(courses.getList());

        sql.prepared(moveCourse,values , SqlResult.validUniqueResultHandler(handler));
    }


    @Override
    public void deleteCourse(final JsonObject course, final Handler<Either<String, JsonObject>> handler) {
        JsonArray values = new JsonArray();
        JsonArray courses = course.getJsonArray("coursesId");

        for (int i = 0; i < courses.size(); i++) {
            values.add(courses.getValue(i));
        }

        String deleteCourse = "DELETE FROM " + Moodle.moodleSchema + ".course" + " WHERE moodle_id IN " + Sql.listPrepared(courses.getList());

        sql.prepared(deleteCourse, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void getFoldersInEnt(String id_user, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT * " +
                "FROM " + Moodle.moodleSchema + ".folder " +
                "WHERE user_id = ?;";
        JsonArray values = new JsonArray();
        values.add(id_user);
        sql.prepared(query, values, SqlResult.validResultHandler(handler));
    }

    @Override
    public void countItemInfolder(long id_folder, String userId, Handler<Either<String, JsonObject>> defaultResponseHandler) {
        String query = "SELECT  count(*) " +
                "FROM " + Moodle.moodleSchema + ".folder " +
                "WHERE user_id = ? AND parent_id = ?;";
        JsonArray values = new JsonArray();
        values.add(userId).add(id_folder);
        sql.prepared(query, values, SqlResult.validUniqueResultHandler(defaultResponseHandler));
    }

    @Override
    public void countCoursesItemInfolder(long id_folder, String userId, Handler<Either<String, JsonObject>> defaultResponseHandler) {
        String query = "SELECT  count(*) " +
                "FROM " + Moodle.moodleSchema + ".course " +
                "WHERE user_id = ? AND folder_id = ?;";
        JsonArray values = new JsonArray();
        values.add(userId).add(id_folder);
        sql.prepared(query, values, SqlResult.validUniqueResultHandler(defaultResponseHandler));
    }

    @Override
    public void getCoursesByUserInEnt(String userId, Handler<Either<String, JsonArray>> eitherHandler) {
        String query = "SELECT moodle_id, folder_id  " +
                "FROM " + Moodle.moodleSchema + ".course " +
                "WHERE user_id = ?;";

        JsonArray values = new JsonArray();
        values.add(userId);
        sql.prepared(query, values, SqlResult.validResultHandler(eitherHandler));
    }

    @Override
    public void getChoices(String userId, final Handler<Either<String, JsonArray>> eitherHandler) {
        String query = "SELECT lastcreation, todo, tocome " +
                "FROM " + Moodle.moodleSchema + ".choices " +
                "WHERE user_id = ?;";

        JsonArray values = new JsonArray();
        values.add(userId);
        sql.prepared(query, values, SqlResult.validResultHandler(eitherHandler));
    }

    @Override
    public void setChoice(final JsonObject courses, String view, Handler<Either<String, JsonObject>> handler) {
        String query = "INSERT INTO " + Moodle.moodleSchema + ".choices (user_id, lastcreation, todo, tocome)" +
                " VALUES (?, ?, ?, ?) ON CONFLICT (user_id) DO UPDATE SET " + view + "=" + courses.getBoolean("printcourses" + view) + "; " +
                "DELETE FROM " + Moodle.moodleSchema + ".choices WHERE lastcreation=true AND todo=true AND tocome=true;";

        JsonArray values = new JsonArray();
        values.add(courses.getString("userId"));
        values.add(courses.getBoolean("printcourseslastcreation"));
        values.add(courses.getBoolean("printcoursestodo"));
        values.add(courses.getBoolean("printcoursestocome"));

        sql.prepared(query, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void getUsers(final JsonArray usersIds, Handler<Either<String, JsonArray>> handler) {
        JsonObject params = new JsonObject()
                .put("usersIds", usersIds);

        String queryUsersNeo4j =
                "MATCH (u:User) WHERE  u.id IN {usersIds} " +
                        "RETURN u.id AS id, u.login as username, u.email AS email, u.firstName AS firstname, u.lastName AS lastname";

        Neo4j.getInstance().execute(queryUsersNeo4j, params, Neo4jResult.validResultHandler(handler));
    }

    @Override
    public void getGroups(final JsonArray groupsIds, Handler<Either<String, JsonArray>> handler) {
        JsonObject params = new JsonObject()
                .put("groupsIds", groupsIds);


        String queryGroupsNeo4j =
                "MATCH(g:Group)-[:IN]-(ug:User) WHERE g.id  IN {groupsIds} " +
                        "WITH g, collect({id: ug.id, username: ug.login, email: ug.email, firstname: ug.firstName, lastname: ug.lastName}) AS users " +
                        "return \"GR_\"+g.id AS id, g.name AS name, users";

        Neo4j.getInstance().execute(queryGroupsNeo4j, params, Neo4jResult.validResultHandler(handler));
    }

    @Override
    public void getSharedBookMark(String sharedBookMarkId, Handler<Either<String, JsonArray>> handler) {
        JsonObject params = new JsonObject()
                .put("sharedBookMarkId", sharedBookMarkId);

        String queryNeo4j = "MATCH (u:User)-[:HAS_SB]->(sb:ShareBookmark) " +
                "UNWIND TAIL(sb.{sharedBookMarkId}) as vid " +
                "MATCH (v:Visible {id : vid}) " +
                "WHERE not(has(v.deleteDate)) " +
                "RETURN {group: {id: \"SB\" + \" {sharedBookMarkId} \", name: HEAD(sb.{sharedBookMarkId}), " +
                "users: COLLECT(DISTINCT " +
                "{id: v.id, email: v.email, lastname: v.lastName, firstname: v.firstName, username: v.login})}} " +
                "as sharedBookMark;";

        Neo4j.getInstance().execute(queryNeo4j, params, Neo4jResult.validResultHandler(handler));
    }

    @Override
    public void insertDuplicateTable (JsonObject courseToDuplicate, Handler<Either<String, JsonObject>> handler) {
        String query = "INSERT INTO " + Moodle.moodleSchema + ".duplication (id_course, id_folder, id_users, status)" +
                " VALUES (?, ?, ?, ?);";

        JsonArray values = new JsonArray();
        values.add(courseToDuplicate.getInteger("courseid"));
        values.add(courseToDuplicate.getInteger("folderid"));
        values.add(courseToDuplicate.getString("userId"));
        values.add(courseToDuplicate.getString("status"));

        sql.prepared(query, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void getCourseIdToDuplicate (String status, Handler<Either<String, JsonArray>> handler){
        String query = "SELECT id, id_course, id_users, id_folder, nombre_tentatives FROM " + Moodle.moodleSchema + ".duplication WHERE status = ?";

        if(status == WAITING)
            query+= "LIMIT 1";

        query += ";";

        JsonArray values = new JsonArray();
        values.add(status);

        sql.prepared(query, values, SqlResult.validResultHandler(handler));
    }

    @Override
    public void getCourseToDuplicate (String userId, final Handler<Either<String, JsonArray>> handler){
        String query = "SELECT * FROM " + Moodle.moodleSchema + ".duplication WHERE id_users = ? ;";

        JsonArray values = new JsonArray();
        values.add(userId);

        sql.prepared(query, values, SqlResult.validResultHandler(handler));
    }

    @Override
    public void updateStatusCourseToDuplicate (String status, Integer id, Integer numberOfTentatives, Handler<Either<String, JsonObject>> handler){
        JsonArray values = new JsonArray();
        String query = "UPDATE " + Moodle.moodleSchema + ".duplication SET ";
        if(status == WAITING){
            if(numberOfTentatives == moodleConfig.getInteger("numberOfMaxDuplicationTentatives"))
                status=ERROR;
            query += "nombre_tentatives = ?, ";

            values.add(numberOfTentatives + 1);
        }

        query += "status = ? WHERE id = ?";

        values.add(status);
        values.add(id);


        sql.prepared(query, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void deleteFinishedCoursesDuplicate ( Handler<Either<String, JsonObject>> handler){
        String query = "DELETE FROM " + Moodle.moodleSchema + ".duplication WHERE status = '"+FINISHED+"' ;"+
                "DELETE FROM " + Moodle.moodleSchema + ".duplication WHERE status != '"+WAITING+"' AND status != '"+ERROR+"' AND now()-date > interval '"+
                moodleConfig.getString("timeDuplicationBeforeDelete")+"' ;";

        sql.raw(query, SqlResult.validUniqueResultHandler(handler));
    }
}
