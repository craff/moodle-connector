package fr.openent.moodle.service.impl;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.service.MoodleWebService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.sql.SqlResult;


public class DefaultMoodleWebService extends SqlCrudService implements MoodleWebService {

    public DefaultMoodleWebService(String schema, String table) {
        super(schema, table);
    }

    @Override
    public void create(final JsonObject course, final Handler<Either<String, JsonObject>> handler){
        String createCourse = "INSERT INTO " + Moodle.moodleSchema + ".course(moodle_id, folder_id, user_id)" +
                " VALUES (?, ?, ?)";

        JsonArray values = new JsonArray();
        values.add(course.getValue("moodleid"));
        values.add(1);
        values.add(course.getValue("idnumber"));

        sql.prepared(createCourse, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void delete(final JsonObject course, final Handler<Either<String, JsonObject>> handler) {
        String deleteCourse = "DELETE FROM " + Moodle.moodleSchema + ".course WHERE moodle_id = ?;";

        JsonArray values = new JsonArray();
        values.add(course.getValue("courseids"));

        sql.prepared(deleteCourse, values, SqlResult.validUniqueResultHandler(handler));
    }

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
            if(o.getInteger("moodle_id")==courid){
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
                "FROM " + Moodle.moodleSchema + ".course " +
                "WHERE user_id = ? AND folder_id = ?;";
        JsonArray values = new JsonArray();
        values.add(userId).add(id_folder);
        sql.prepared(query, values, SqlResult.validUniqueResultHandler(defaultResponseHandler));
    }


}
