package fr.openent.moodle.service;

import io.vertx.core.Handler;
import fr.wseduc.webutils.Either;
import io.vertx.core.json.JsonObject;

public interface MoodleEventBusService {

    /**
     * Create a course
     * @param action User Id
     * @param handler function handler returning data
     */

    void getParams (JsonObject action, Handler<Either<String,JsonObject>> handler);
}
