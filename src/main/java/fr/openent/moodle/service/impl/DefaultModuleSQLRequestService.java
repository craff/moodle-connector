package fr.openent.moodle.service.impl;


import fr.openent.moodle.Moodle;
import fr.openent.moodle.service.ModuleSQLRequestService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;

import java.text.SimpleDateFormat;
import java.util.Date;

import static fr.openent.moodle.Moodle.*;

public class DefaultModuleSQLRequestService extends SqlCrudService implements ModuleSQLRequestService {

    private final Logger log = LoggerFactory.getLogger(DefaultModuleSQLRequestService.class);

    public DefaultModuleSQLRequestService(String schema, String table) {
        super(schema, table);
    }

    @Override
    public void createFolder(final JsonObject folder, final Handler<Either<String, JsonObject>> handler) {
        JsonArray values = new JsonArray();
        Object parentId = folder.getValue("parentId");
        values.add(folder.getValue("userId"));
        values.add(folder.getValue("name"));
        String createFolder;
        if (!parentId.equals(0)) {
            values.add(folder.getValue("parentId"));
            createFolder = "INSERT INTO " + Moodle.moodleSchema + ".folder(user_id, name, parent_id)" +
                    " VALUES (?, ?, ?)";
        } else {
            createFolder = "INSERT INTO " + Moodle.moodleSchema + ".folder(user_id, name)" +
                    " VALUES (?, ?)";
        }
        sql.prepared(createFolder, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void renameFolder(final JsonObject folder, final Handler<Either<String, JsonObject>> handler){

        String renameFolder = "UPDATE " + Moodle.moodleSchema + ".folder SET name= ? WHERE id= ?";
        JsonArray values = new JsonArray().add(folder.getValue("name")).add(folder.getValue("id"));
        sql.prepared(renameFolder, values, SqlResult.validUniqueResultHandler(handler));
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
    public void createCourse(final JsonObject course, final Handler<Either<String, JsonObject>> handler) {
        String createCourse = "INSERT INTO " + Moodle.moodleSchema + ".course(moodle_id, user_id)" +
                " VALUES (?, ?) RETURNING moodle_id as id;";

        JsonArray values = new JsonArray();
        values.add(course.getValue("moodleid"));
        values.add(course.getString("userid"));

        sql.prepared(createCourse, values, SqlResult.validUniqueResultHandler(event -> {
            if (event.isRight()) {
                if (!course.getValue("folderId").equals(0)) {
                    createRelCourseFolder(course.getValue("moodleid"), course.getValue("folderId"), handler);
                } else {
                    handler.handle(new Either.Right<>(course));
                }

            } else {
                log.error("Error when inserting new courses before inserting rel_course_folders elements ");
            }

        }));

    }

    private void createRelCourseFolder(Object moodleid, Object folderId, Handler<Either<String, JsonObject>> handler) {
        String createCourse = "INSERT INTO " + Moodle.moodleSchema + ".rel_course_folder(course_id, folder_id)" +
                " VALUES (?, ?) RETURNING course_id as id;";

        JsonArray values = new JsonArray();
        values.add(moodleid);
        values.add(folderId);

        sql.prepared(createCourse, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void getPreferences(String id_user, final Handler<Either<String, JsonArray>> handler) {
        String getCoursesPreferences = "SELECT moodle_id, masked, favorites FROM " + Moodle.moodleSchema + ".preferences" + " WHERE user_id = ?;";
        JsonArray value = new JsonArray();
        value.add(id_user);
        sql.prepared(getCoursesPreferences, value, SqlResult.validResultHandler(handler));
    }

    @Override
    public void setPreferences(final JsonObject course, final Handler<Either<String, JsonObject>> handler) {
        String query = "INSERT INTO " + Moodle.moodleSchema + ".preferences (moodle_id, user_id, masked, favorites)" +
                " VALUES (?, ?, ?, ?) ON CONFLICT (moodle_id, user_id) DO UPDATE SET masked = ?, favorites = ?; " +
                "DELETE FROM " + Moodle.moodleSchema + ".preferences WHERE masked = false AND favorites = false;";

        JsonArray values = new JsonArray();
        values.add(course.getInteger("courseid"));
        values.add(course.getString("userId"));
        values.add(course.getBoolean("masked"));
        values.add(course.getBoolean("favorites"));
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
        String deleteCourse = "DELETE FROM " + Moodle.moodleSchema + ".course " +
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
    public void countItemInFolder(long id_folder, String userId, Handler<Either<String, JsonObject>> defaultResponseHandler) {
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
                ", null as disciplines, null as levels, null as plain_text, null as fullname, null as imageurl, " +
                "null as summary, null as author, null as author_id, null as user_id, null as username, null as license " +
                "FROM " + Moodle.moodleSchema + ".course " +
                "LEFT JOIN " + moodleSchema + ".rel_course_folder " +
                "ON course.moodle_id = rel_course_folder.course_id " +
                "WHERE course.user_id = ? AND NOT EXISTS (SELECT course_id FROM " + Moodle.moodleSchema + ".publication WHERE course_id = moodle_id) " +
                "UNION SELECT course_id as moodle_id, 0 as folder_id, discipline_label as disciplines, level_label as levels, key_words as plain_text, fullname, " +
                "imageurl, summary, author, author_id, user_id, username, license " +
                "FROM " + Moodle.moodleSchema + ".publication WHERE publication.user_id = ? AND course_id is not null;";

        JsonArray values = new JsonArray();
        values.add(userId);
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
                "DELETE FROM " + Moodle.moodleSchema + ".choices WHERE lastcreation = true AND todo = true AND tocome = true " +
                "AND coursestodosort = 'doing' AND coursestocomesort = 'all';";

        JsonArray values = new JsonArray();
        values.add(courses.getString("userId"));
        values.add(courses.getBoolean("lastCreation",true));
        values.add(courses.getBoolean("toDo",true));
        values.add(courses.getBoolean("toCome",true));
        values.add(courses.getString("coursestodosort","all"));
        values.add(courses.getString("coursestocomesort","doing"));

        sql.prepared(query, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void insertDuplicateTable (JsonObject courseToDuplicate, Handler<Either<String, JsonObject>> handler) {
        JsonArray values = new JsonArray();
        String query = "INSERT INTO " + Moodle.moodleSchema + ".duplication (id_course, id_folder, id_users, status, category_id";

        if (courseToDuplicate.getInteger("publishFK") != null) {
            query += ", auditeur, publishFK) VALUES (?, ?, ?, ?, ?, ?, ?);";
            values.add(courseToDuplicate.getInteger("courseid"))
                    .add(courseToDuplicate.getInteger("folderId"))
                    .add(courseToDuplicate.getString("userId"))
                    .add(courseToDuplicate.getString("status"))
                    .add(courseToDuplicate.getInteger("category_id"))
                    .add(courseToDuplicate.getString("auditeur_id"))
                    .add(courseToDuplicate.getInteger("publishFK"));

        } else {
            query += ") VALUES (?, ?, ?, ?, ?);";
            values.add(courseToDuplicate.getInteger("courseid"))
                    .add(courseToDuplicate.getInteger("folderId"))
                    .add(courseToDuplicate.getString("userId"))
                    .add(courseToDuplicate.getString("status"))
                    .add(courseToDuplicate.getInteger("category_id"));
        }

        sql.prepared(query, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void getCourseIdToDuplicate (String status, Handler<Either<String, JsonArray>> handler){
        String query = "SELECT id, id_course, id_users, id_folder, nombre_tentatives, category_id, auditeur FROM " + Moodle.moodleSchema +
                ".duplication WHERE status = ?";

        if(status.equals(WAITING))
            query+= " LIMIT 1";

        query += ";";

        JsonArray values = new JsonArray();
        values.add(status);

        sql.prepared(query, values, SqlResult.validResultHandler(handler));
    }

    @Override
    public void getUserCourseToDuplicate (String userId, final Handler<Either<String, JsonArray>> handler){
        String query = "SELECT * FROM " + Moodle.moodleSchema + ".duplication WHERE id_users = ? ;";

        JsonArray values = new JsonArray();
        values.add(userId);

        sql.prepared(query, values, SqlResult.validResultHandler(handler));
    }

    @Override
    public void getCourseToDuplicate (Integer ident, final Handler<Either<String, JsonObject>> handler){
        String query = "SELECT * FROM " + Moodle.moodleSchema + ".duplication WHERE id = ? ;";

        JsonArray values = new JsonArray();
        values.add(ident);

        sql.prepared(query, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void updateStatusCourseToDuplicate (String status, Integer id, Integer attemptsNumber, Handler<Either<String, JsonObject>> handler){
        JsonArray values = new JsonArray();
        String query = "UPDATE " + Moodle.moodleSchema + ".duplication SET ";
        if(status.equals(WAITING)){
            if(attemptsNumber.equals(moodleConfig.getInteger("numberOfMaxDuplicationTentatives")))
                status=ERROR;
            query += "nombre_tentatives = ?, ";

            values.add(attemptsNumber + 1);
        }

        query += "status = ? WHERE id = ?";

        values.add(status);
        values.add(id);

        sql.prepared(query, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void deleteFinishedCoursesDuplicate (Handler<Either<String, JsonObject>> handler){
        String query = "DELETE FROM " + Moodle.moodleSchema + ".duplication " +
                "WHERE status = '" + FINISHED + "' "+
                "OR (status != '" + ERROR + "' AND now()-date > interval '" + moodleConfig.getString("timeDuplicationBeforeDelete") + "');";

        sql.raw(query, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void insertPublishedCourseMetadata (JsonObject courseToPublish, Handler<Either<String, JsonObject>> handler){
        JsonArray values = new JsonArray();

        SimpleDateFormat formater;
        Date now = new Date();
        formater = new SimpleDateFormat("_yyyy-MM-dd");
        courseToPublish.put("title", courseToPublish.getString("title") + formater.format(now));

        JsonArray levelArray = new JsonArray();
        if(courseToPublish.containsKey("levels")) {
            for (int i = 0; i < courseToPublish.getJsonArray("levels").size(); i++) {
                levelArray.add((courseToPublish.getJsonArray("levels").getJsonObject(i).getString("label")));
            }
        }

        JsonArray disciplineArray = new JsonArray();
        if(courseToPublish.containsKey("disciplines")) {
            for (int i = 0; i < courseToPublish.getJsonArray("disciplines").size(); i++) {
                disciplineArray.add((courseToPublish.getJsonArray("disciplines").getJsonObject(i).getString("label")));
            }
        }

        JsonArray plainTextArray = new JsonArray();
        if(courseToPublish.containsKey("plain_text")) {
            for (int i = 0; i < courseToPublish.getJsonArray("plain_text").size(); i++) {
                plainTextArray.add((courseToPublish.getJsonArray("plain_text").getJsonObject(i).getString("label")));
            }
        }

        String test = "";
        if (disciplineArray.isEmpty())
            disciplineArray.add(test);
        if (levelArray.isEmpty())
            levelArray.add(test);
        if (plainTextArray.isEmpty())
            plainTextArray.add(test);

        String query = "INSERT INTO " + Moodle.moodleSchema + ".publication (discipline_label, level_label, key_words, " +
                "fullname, imageurl, summary, author, author_id, user_id, username, license) " +
                "VALUES (" + Sql.arrayPrepared(disciplineArray) + " ," + Sql.arrayPrepared(levelArray) +
                ", " + Sql.arrayPrepared(plainTextArray) + ", ?, ?, ?, ?, ?, ?, ?, ?) RETURNING publication.id AS id;";

        values.addAll(disciplineArray)
                .addAll(levelArray)
                .addAll(plainTextArray)
                .add(courseToPublish.getString("title"))
                .add(courseToPublish.getString("urlImage"))
                .add(courseToPublish.getString("description"))
                .add(courseToPublish.getString("author"))
                .add(courseToPublish.getString("author_id"))
                .add(courseToPublish.getString("userid"))
                .add(courseToPublish.getString("username"))
                .add(true);

        sql.prepared(query, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void updatePublishedCourseId (JsonObject createCourseDuplicate, Handler<Either<String, JsonObject>> handler) {
        String query = "UPDATE " + Moodle.moodleSchema + ".publication SET course_id = ? WHERE id = ?";

        JsonArray values = new JsonArray();
        values.add(createCourseDuplicate.getInteger("moodleid"));
        values.add(createCourseDuplicate.getInteger("duplicationFK"));

        sql.prepared(query, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void updatePublicCourseMetadata(Integer course_id, JsonObject newMetadata, Handler<Either<String, JsonObject>> handler) {
        String query = "UPDATE " + Moodle.moodleSchema + ".publication SET discipline_label = " +
                Sql.arrayPrepared(newMetadata.getJsonArray("discipline_label"))
                + ", level_label = " + Sql.arrayPrepared(newMetadata.getJsonArray("level_label")) + ", key_words = " +
                Sql.arrayPrepared(newMetadata.getJsonArray("plain_text")) + " WHERE course_id = ?";

        JsonArray values = new JsonArray();
        values.addAll(newMetadata.getJsonArray("discipline_label"));
        values.addAll(newMetadata.getJsonArray("level_label"));
        values.addAll(newMetadata.getJsonArray("plain_text"));
        values.add(course_id);

        sql.prepared(query, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void updatePublicCourse(JsonObject newCourse, Handler<Either<String, JsonObject>> handler) {
        String query = "UPDATE " + Moodle.moodleSchema + ".publication SET fullname = ?, imageurl = ?, summary = ? " +
                "WHERE course_id = ?";

        JsonArray values = new JsonArray();
        values.add(newCourse.getString("fullname"));
        values.add(newCourse.getString("imageurl"));
        values.add(newCourse.getString("summary"));
        values.add(newCourse.getInteger("courseid"));

        sql.prepared(query, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void deletePublicCourse(JsonArray idsToDelete, Handler<Either<String, JsonObject>> handler) {
        String deleteCourse = "DELETE FROM " + Moodle.moodleSchema + ".publication " +
                "WHERE course_id IN " + Sql.listPrepared(idsToDelete.getList());

        sql.prepared(deleteCourse, idsToDelete, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void getDuplicationId(JsonArray publicationId, Handler<Either<String, JsonObject>> handler) {
        String getId = "SELECT publishfk FROM " + Moodle.moodleSchema + ".duplication " +
                "WHERE id = ?";

        sql.prepared(getId, publicationId, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void getPublicCourseData(JsonArray id, final Handler<Either<String, JsonObject>> handler) {
        String selectCourse = "SELECT * FROM " + Moodle.moodleSchema + ".publication " +
                "WHERE course_id = ?";

        sql.prepared(selectCourse, id, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void getLevels(Handler<Either<String, JsonArray>> handler) {
        String query = "Select * From " + Moodle.moodleSchema + ".levels;";
        sql.prepared(query, new JsonArray(), SqlResult.validResultHandler(handler));
    }

    @Override
    public void getDisciplines(Handler<Either<String, JsonArray>> handler) {
        String query = "Select * From " + Moodle.moodleSchema + ".disciplines;";
        sql.prepared(query, new JsonArray(), SqlResult.validResultHandler(handler));
    }
}
