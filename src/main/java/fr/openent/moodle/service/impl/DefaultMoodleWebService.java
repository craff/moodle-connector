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

        sql.prepared(createCourse, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void delete(final String id, final Handler<Either<String, JsonObject>> handler) {
        String deleteCourse = "DELETE FROM " + Moodle.moodleSchema + ".course WHERE moodle_id = ?;";

        JsonArray values = new JsonArray();
        values.add(id);

        sql.prepared(deleteCourse, values, SqlResult.validUniqueResultHandler(handler));
    }
}
