package fr.openent.moodle.service.impl;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.service.MoodleWebService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;


public class DefaultMoodleWebService extends SqlCrudService implements MoodleWebService {

    public DefaultMoodleWebService(String schema, String table) {
        super(schema, table);
    }

    @Override
    public void deleteFolders(final JsonObject folder, final Handler<Either<String, JsonObject>> handler){
        JsonArray values = new JsonArray();
        JsonArray folders = folder.getJsonArray("foldersId");

        for (int i = 0; i < folders.size(); i++) {
            values.add(folders.getValue(i));
        }

        String deleteFolders = "DELETE FROM " + Moodle.moodleSchema + ".folder" + " WHERE id IN " + Sql.listPrepared(folders.getList());

        sql.prepared(deleteFolders,values , SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void moveFolder(final JsonObject folder, final Handler<Either<String, JsonObject>> handler){
        JsonArray values = new JsonArray();
        JsonArray folders = folder.getJsonArray("foldersId");
        values.add(folder.getValue("parentId"));

        for (int i = 0; i < folders.size(); i++) {
            values.add(folders.getValue(i));
        }

        String moveFolder = "UPDATE " + Moodle.moodleSchema + ".folder" + " SET parent_id = ? WHERE id IN " + Sql.listPrepared(folders.getList());

        sql.prepared(moveFolder,values , SqlResult.validUniqueResultHandler(handler));
    }
    @Override
    public void createFolder(final JsonObject folder, final Handler<Either<String, JsonObject>> handler){
        JsonArray values = new JsonArray();
        values.add(folder.getValue("parentId"));
        values.add(folder.getValue("userId"));
        values.add(folder.getValue("structureId"));
        values.add(folder.getValue("name"));

        String createFolder = "INSERT INTO " + Moodle.moodleSchema + ".folder(parent_id, user_id, structure_id, name)" +
                    " VALUES (?, ?, ?, ?)";
        sql.prepared(createFolder,values , SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void createCourse(final JsonObject course, final Handler<Either<String, JsonObject>> handler){
        String createCourse = "INSERT INTO " + Moodle.moodleSchema + ".course(moodle_id, folder_id, user_id)" +
                " VALUES (?, ?, ?) RETURNING moodle_id as id";

        JsonArray values = new JsonArray();
        values.add(course.getValue("moodleid"));

        Integer folderId = course.getInteger("folderid");
        if(folderId == null) {
            values.addNull();
        } else {
            values.add(folderId);
        }
        values.add(course.getString("userid"));

        sql.prepared(createCourse, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void deleteCourse(final JsonObject course, final Handler<Either<String, JsonObject>> handler) {
        String deleteCourse = "DELETE FROM " + Moodle.moodleSchema + ".course WHERE moodle_id = ?;";

        JsonArray values = new JsonArray();
        values.add(course.getValue("courseids"));

        sql.prepared(deleteCourse, values, SqlResult.validUniqueResultHandler(handler));
    }
/*
___________________delete courses à tester______________
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
 */
    @Override
    public void getCoursInEnt(final long id_folder, String id_user, final Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT moodle_id, folder_id  " +
                "FROM " + Moodle.moodleSchema + ".course " +
                "WHERE folder_id = ? and  user_id = ?;";

        JsonArray values = new JsonArray();
        values.add(id_folder)
                .add(id_user);
        sql.prepared(query, values, SqlResult.validResultHandler(handler));
    }

    @Override
    public boolean getValueMoodleIdinEnt(Integer courid, JsonArray object) {
        for(int i=0;i<object.size();i++){
            JsonObject  o=object.getJsonObject(i);
            if(o.getInteger("moodle_id").equals(courid)){
                return true;
            }
        }
        return false;
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
    public void getChoice(String userId, String view, final Handler<Either<String, JsonObject>> handler) {
        String query = "SELECT choice " +
                "FROM " + Moodle.moodleSchema + ".choices " +
                "WHERE user_id = ? AND view = ?;";

        JsonArray values = new JsonArray();
        values.add(userId);
        values.add(view);
        sql.prepared(query, values,SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void setChoice(final JsonObject courses, String view, Handler<Either<String, JsonObject>> handler) {
        String query = "UPDATE " + Moodle.moodleSchema + ".choices " +
                "SET choice = ? WHERE user_id = ? AND view = ?;";

        JsonArray values = new JsonArray();
        values.add(courses.getBoolean("printcreatecoursesrecents"));
        values.add(courses.getString("userId"));
        values.add(view);
        sql.prepared(query, values,SqlResult.validUniqueResultHandler(handler));
    }
}
