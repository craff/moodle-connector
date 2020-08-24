package fr.openent.moodle.service.impl;

import fr.openent.moodle.service.PublishedCourseService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.sql.SqlResult;

public class DefaultPublishedCourseService extends SqlCrudService implements PublishedCourseService {

    public DefaultPublishedCourseService(String schema, String table) {
        super(schema, table);
    }

    private final Logger log = LoggerFactory.getLogger(DefaultModuleSQLRequestService.class);

    public void getLevels(Handler<Either<String, JsonArray>> handler) {
        String query = "Select * From moodle.levels;";
        sql.prepared(query, new JsonArray(), SqlResult.validResultHandler(handler));
    }
    public void getDisciplines(Handler<Either<String, JsonArray>> handler) {
        String query = "Select * From moodle.disciplines;";

        sql.prepared(query, new JsonArray(), SqlResult.validResultHandler(handler));
    }
}
