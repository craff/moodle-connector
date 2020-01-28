package fr.openent.moodle.service.impl;


import fr.openent.moodle.Moodle;
import fr.openent.moodle.service.MoodleService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.neo4j.Neo4jResult;
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fr.openent.moodle.Moodle.*;

public class DefaultMoodleService extends SqlCrudService implements MoodleService {

    private final Logger log = LoggerFactory.getLogger(DefaultMoodleService.class);

    public DefaultMoodleService(String schema, String table) {
        super(schema, table);
    }

    @Override
    public void createFolder(final JsonObject folder, final Handler<Either<String, JsonObject>> handler){
        JsonArray values = new JsonArray();
        Object parentId = folder.getValue("parentId");
        values.add(folder.getValue("userId"));
        values.add(folder.getValue("name"));
        String createFolder = "";
        if(!parentId.equals(0)) {
            values.add(folder.getValue("parentId"));
            createFolder = "INSERT INTO " + Moodle.moodleSchema + ".folder(user_id,  name, parent_id)" +
                    " VALUES (?, ?,  ?)";
        }else {
            createFolder = "INSERT INTO " + Moodle.moodleSchema + ".folder(user_id,  name)" +
                    " VALUES (?,  ?)";
        }
        sql.prepared(createFolder,values , SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void renameFolder(final JsonObject folder, final Handler<Either<String, JsonObject>> handler){

        String renameFolder = "UPDATE " + Moodle.moodleSchema + ".folder SET name='"+folder.getValue("name")+"' WHERE id="+folder.getValue("id");
        sql.raw(renameFolder , SqlResult.validUniqueResultHandler(handler));
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
    public void moveFolder(final JsonObject folder, final Handler<Either<String, JsonObject>> handler) {
        JsonArray values = new JsonArray();
        JsonArray folders = folder.getJsonArray("foldersId");
        if (folder.getInteger("parentId") == 0) {
            values.addNull();
        } else {
            values.add(folder.getValue("parentId"));
        }

        for (int i = 0; i < folders.size(); i++) {
            values.add(folders.getValue(i));
        }

        String moveFolder = "UPDATE " + Moodle.moodleSchema + ".folder SET parent_id = ? WHERE id IN " + Sql.listPrepared(folders.getList());

        sql.prepared(moveFolder, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void createCourse(final JsonObject course, String userId, final Handler<Either<String, JsonObject>> handler){
        String createCourse = "INSERT INTO " + Moodle.moodleSchema + ".course(moodle_id,  user_id)" +
                " VALUES (?,  ?) RETURNING moodle_id as id;";

        JsonArray values = new JsonArray();
        values.add(course.getValue("moodleid"));
        values.add(course.getString("userid"));

        sql.prepared(createCourse, values, SqlResult.validUniqueResultHandler(event -> {
            if(event.isRight()){
                if(!course.getValue("folderid").equals(0)){
                    createRelCourseFolder(course.getValue("moodleid"), course.getValue("folderid"), handler);
                } else {
                    handler.handle(new Either.Right<>(course));
                }
            }
            else{
                log.error("Error when inserting new courses before inserting rel_course_folders elems ");
            }

        }));

    }

    private void createRelCourseFolder(Object moodleid, Object folderid, Handler<Either<String, JsonObject>> handler) {
        String createCourse = "INSERT INTO " + Moodle.moodleSchema + ".rel_course_folder(course_id,  folder_id)" +
                " VALUES (?, ?) RETURNING course_id as id;";

        JsonArray values = new JsonArray();
        values.add(moodleid);
        values.add(folderid);

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
                " VALUES (?, ?, ?, ?) ON CONFLICT (moodle_id, user_id) DO UPDATE SET masked =" + course.getBoolean("masked") +
                ", favorites =" + course.getBoolean("favorites") + " ; " +
                "DELETE FROM " + Moodle.moodleSchema + ".preferences WHERE masked=false AND favorites=false;";

        JsonArray values = new JsonArray();
        values.add(course.getInteger("courseid"));
        values.add(course.getString("userId"));
        values.add(course.getBoolean("masked"));
        values.add(course.getBoolean("favorites"));


        sql.prepared(query, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void checkIfCoursesInRelationTable(final JsonObject course, final Handler<Either<String, Boolean>> handler) {
        JsonArray values = new JsonArray();
        JsonArray courses = course.getJsonArray("coursesId");
        for (int i = 0; i < courses.size(); i++) {
            values.add(courses.getValue(i));
        }

        String selectCourse = "SELECT COUNT(course_id) " +
                "FROM " + Moodle.moodleSchema + ".rel_course_folder " +
                "WHERE course_id IN " + Sql.listPrepared(courses.getList());

        sql.prepared(selectCourse, values, res -> {
            Long count = SqlResult.countResult(res);
            if (count > 0) handler.handle(new Either.Right<>(count == courses.size()));
            else handler.handle(new Either.Left<>("Courses present in relation table"));
        });
    }

    @Override
    public void insertCourseInRelationTable(final JsonObject course, final Handler<Either<String, JsonObject>> handler) {
        JsonArray values = new JsonArray();

        String insertMoveCourse = "INSERT INTO " + Moodle.moodleSchema + ".rel_course_folder (folder_id, course_id) VALUES ";
        JsonArray courses = course.getJsonArray("coursesId");

        for (int i = 0; i < courses.size() - 1; i++) {
            values.add(course.getValue("folderId"));
            values.add(courses.getValue(i));
            insertMoveCourse += "(?, ?), ";
        }

        values.add(course.getValue("folderId"));
        values.add(courses.getValue(courses.size() - 1));
        insertMoveCourse += "(?, ?);";

        sql.prepared(insertMoveCourse, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void updateCourseIdInRelationTable(final JsonObject course, final Handler<Either<String, JsonObject>> handler) {
        JsonArray values = new JsonArray();
        JsonArray courses = course.getJsonArray("coursesId");
        values.add(course.getValue("folderId"));
        for (int i = 0; i < courses.size(); i++) {
            values.add(courses.getValue(i));
        }

        String updateMoveCourse = "UPDATE " + Moodle.moodleSchema + ".rel_course_folder SET folder_id = ?" +
                " WHERE course_id IN " + Sql.listPrepared(courses.getList());

        sql.prepared(updateMoveCourse, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void deleteCourseInRelationTable(final JsonObject course, final Handler<Either<String, JsonObject>> handler) {
        JsonArray values = new JsonArray();
        JsonArray courses = course.getJsonArray("coursesId");
        for (int i = 0; i < courses.size(); i++) {
            values.add(courses.getValue(i));
        }

        String deleteMoveCourse = "DELETE FROM " + Moodle.moodleSchema + ".rel_course_folder" +
                " WHERE course_id IN " + Sql.listPrepared(courses.getList());

        sql.prepared(deleteMoveCourse, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void deleteCourse(final JsonObject course, final Handler<Either<String, JsonObject>> handler) {
        JsonArray values = new JsonArray();
        JsonArray courses = course.getJsonArray("coursesId");

        for (int i = 0; i < courses.size(); i++) {
            values.add(courses.getValue(i));
        }
        String deleteCourse = "" +
                "DELETE FROM " + Moodle.moodleSchema + ".course " +
                "WHERE moodle_id IN " + Sql.listPrepared(courses.getList());

        sql.prepared(deleteCourse, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void getFoldersInEnt(String id_user, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT id,  " +
                "CASE WHEN parent_id IS NULL then 0 ELSE parent_id END" +
                ", user_id, name " +
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
    public void getCoursesByUserInEnt(String userId, Handler<Either<String, JsonArray>> eitherHandler) {
        String query = "SELECT moodle_id,CASE WHEN folder_id IS NULL THEN 0 else folder_id end " +
                "FROM " + Moodle.moodleSchema + ".course " +
                "LEFT JOIN " + moodleSchema + ".rel_course_folder" +
                " ON course.moodle_id = rel_course_folder.course_id "+
                "WHERE course.user_id = ?;";

        JsonArray values = new JsonArray();
        values.add(userId);
        sql.prepared(query, values, SqlResult.validResultHandler(eitherHandler));
    }

    @Override
    public void getChoices(String userId, final Handler<Either<String, JsonArray>> eitherHandler) {
        String query = "SELECT * " +
                "FROM " + Moodle.moodleSchema + ".choices " +
                "WHERE user_id = ?;";

        JsonArray values = new JsonArray();
        values.add(userId);
        sql.prepared(query, values, SqlResult.validResultHandler(eitherHandler));
    }

    @Override
    public void setChoice(final JsonObject courses, String view, Handler<Either<String, JsonObject>> handler) {
        String query = "INSERT INTO " + Moodle.moodleSchema + ".choices (user_id, lastcreation, todo, tocome, coursestodosort, coursestocomesort)" +
                " VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (user_id) DO UPDATE SET " + view + "='" + courses.getValue(view) + "'; " +
                "DELETE FROM " + Moodle.moodleSchema + ".choices WHERE lastcreation=true AND todo=true AND tocome=true AND coursestodosort='doing' AND coursestocomesort='all';";

        JsonArray values = new JsonArray();
        values.add(courses.getString("userId"));
        values.add(courses.getBoolean("lastcreation"));
        values.add(courses.getBoolean("todo"));
        values.add(courses.getBoolean("tocome"));
        values.add(courses.getString("coursestodosort"));
        values.add(courses.getString("coursestocomesort"));

        sql.prepared(query, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void getUsers(final JsonArray usersIds, Handler<Either<String, JsonArray>> handler) {
        JsonObject params = new JsonObject()
                .put("usersIds", usersIds);

        String queryUsersNeo4j =
                "MATCH (u:User) WHERE  u.id IN {usersIds} " +
                        "RETURN u.id AS id, u.id as username, u.email AS email, u.firstName AS firstname, u.lastName AS lastname";

        Neo4j.getInstance().execute(queryUsersNeo4j, params, Neo4jResult.validResultHandler(handler));
    }

    @Override
    public void getGroups(final JsonArray groupsIds, Handler<Either<String, JsonArray>> handler) {
        JsonObject params = new JsonObject()
                .put("groupsIds", groupsIds);


        String queryGroupsNeo4j =
                "MATCH(g:Group)-[:IN]-(ug:User) WHERE g.id  IN {groupsIds} " +
                        "WITH g, collect({id: ug.id, username: ug.id, email: ug.email, firstname: ug.firstName, lastname: ug.lastName}) AS users " +
                        "return \"GR_\"+g.id AS id, g.name AS name, users";

        Neo4j.getInstance().execute(queryGroupsNeo4j, params, Neo4jResult.validResultHandler(handler));
    }

    @Override
    public void getSharedBookMark(final JsonArray bookmarksIds, Handler<Either<String, JsonArray>> handler) {
        JsonObject params = new JsonObject()
                .put("bookmarksIds", bookmarksIds);

        String queryNeo4j = "WITH {bookmarksIds} AS shareBookmarkIds " +
                "UNWIND shareBookmarkIds AS shareBookmarkId MATCH (u:User)-[:HAS_SB]->(sb:ShareBookmark) " +
                "UNWIND TAIL(sb[shareBookmarkId]) as vid MATCH (v:Visible {id : vid}) WHERE not(has(v.deleteDate)) " +
                "WITH {group: {id: \"SB\" + shareBookmarkId, name: HEAD(sb[shareBookmarkId]), users: COLLECT(DISTINCT{id: v.id, " +
                "email: v.email, lastname: v.lastName, firstname: v.firstName, username: v.id})}}as sharedBookMark "+
                "RETURN COLLECT(sharedBookMark) as sharedBookMarks;";

        Neo4j.getInstance().execute(queryNeo4j, params, Neo4jResult.validResultHandler(handler));
    }

    @Override
    public void getDistinctSharedBookMarkUsers(final JsonArray bookmarksIds, boolean addPrefix, Handler<Either<String, Map<String, JsonObject>>> handler) {
        getSharedBookMarkUsers(bookmarksIds, new Handler<Either<String, JsonArray>>() {
            @Override
            public void handle(Either<String, JsonArray> resultSharedBookMark) {
                if(resultSharedBookMark.isLeft()) {
                    log.error("Error getting getSharedBookMarkUsers", resultSharedBookMark.left());
                    handler.handle(new Either.Left<>("Error getting getSharedBookMarkUsers"));
                } else {
                    JsonArray results = resultSharedBookMark.right().getValue();
                    Map<String, JsonObject> uniqResults = new HashMap<String, JsonObject>();
                    if(results != null && !results.isEmpty()) {
                        for(Object objShareBook : results) {
                            JsonObject jsonShareBook = ((JsonObject)objShareBook).getJsonObject("sharedBookMark");
                            String idShareBook = jsonShareBook.getString("id");
                            if(addPrefix) {
                                idShareBook = "SB" + idShareBook;
                                jsonShareBook.put("id", idShareBook);
                            }

                            JsonObject shareBookToMerge = uniqResults.get(idShareBook);
                            if(shareBookToMerge != null) {
                                List<JsonObject> users = jsonShareBook.getJsonArray("users").getList();
                                List<JsonObject> usersToMerge = shareBookToMerge.getJsonArray("users").getList();

                                // fusion des listes sans doublon
                                users.removeAll(usersToMerge);
                                users.addAll(usersToMerge);
                                jsonShareBook.put("users", new JsonArray(users));
                            }

                            uniqResults.put(idShareBook, jsonShareBook);
                        }
                    }
                    handler.handle(new Either.Right<String, Map<String, JsonObject>>(uniqResults));
                }
            }
        });
    }

    private void getSharedBookMarkUsers(final JsonArray bookmarksIds, Handler<Either<String, JsonArray>> handler) {
        JsonObject params = new JsonObject()
                .put("bookmarksIds", bookmarksIds);

        String queryNeo4j = "WITH {bookmarksIds} AS shareBookmarkIds UNWIND shareBookmarkIds AS shareBookmarkId MATCH (u:User)-[:HAS_SB]->(sb:ShareBookmark) UNWIND TAIL(sb[shareBookmarkId]) as vid " +
                "MATCH (v:Visible {id : vid})<-[:IN]-(us:User) WHERE not(has(v.deleteDate)) and v:ProfileGroup WITH {id: shareBookmarkId, name: HEAD(sb[shareBookmarkId]), users: COLLECT(DISTINCT{id: us.id, email: us.email, lastname: us.lastName, firstname: us.firstName, username: us.id})} as sharedBookMark " +
                "RETURN sharedBookMark " +
                "UNION " +
                "WITH {bookmarksIds} AS shareBookmarkIds UNWIND shareBookmarkIds AS shareBookmarkId MATCH (u:User)-[:HAS_SB]->(sb:ShareBookmark) UNWIND TAIL(sb[shareBookmarkId]) as vid " +
                "MATCH (v:Visible {id : vid}) WHERE not(has(v.deleteDate)) and v:User WITH {id: shareBookmarkId, name: HEAD(sb[shareBookmarkId]), users: COLLECT(DISTINCT{id: v.id, email: v.email, lastname: v.lastName, firstname: v.firstName, username: v.id})} as sharedBookMark " +
                "RETURN sharedBookMark";

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

        if(status.equals(WAITING))
            query+= " LIMIT 1";

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
        if(status.equals(WAITING)){
            if(numberOfTentatives.equals(moodleConfig.getInteger("numberOfMaxDuplicationTentatives")))
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
