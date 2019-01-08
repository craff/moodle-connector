package fr.openent.moodle.service.impl;

import com.sun.org.apache.xpath.internal.operations.Number;
import fr.openent.moodle.Moodle;
import fr.openent.moodle.service.MoodleService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.sql.SqlResult;

import static org.entcore.common.sql.SqlResult.validUniqueResultHandler;

public class DefaultMoodleService extends SqlCrudService implements MoodleService {

    public DefaultMoodleService(String schema, String table) {
        super(schema, table);
    }

    @Override
    public void create(final JsonObject course, final Handler<Either<String, JsonObject>> handler){
        String createCourse = "INSERT INTO " + Moodle.moodleSchema + ".course(moodle_id, folder_id, user_id)" +
                " VALUES (?, ?, ?)";

        JsonArray values = new JsonArray();
        values.add(course.getInteger("moodle_id", 1));
        values.add(course.getInteger("folder_id", 1));
        values.add(course.getString("user_id", "1a"));

        sql.prepared(createCourse, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void delete(final String id_moodle, final Handler<Either<String, JsonObject>> handler) {
        String deleteCourse = "DELETE FROM " + Moodle.moodleSchema + ".course WHERE moodle_id = ?;";

        JsonArray values = new JsonArray();
        values.add(id_moodle).getInteger(1);

        sql.prepared(deleteCourse, values, SqlResult.validUniqueResultHandler(handler));
    }
}
