package fr.openent.moodle.service;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import fr.wseduc.webutils.Either;

public interface MoodleService {

    /**
     * Create a course
     * @param course course to create
     * @param handler function handler returning data
     */
    void create (JsonObject course, Handler<Either<String, JsonObject>> handler);

    /**
     * Delete a course
     * @param id_moodle course id
     * @param handler function handler returning data
     */
    void delete(String id_moodle, Handler<Either<String, JsonObject>> handler);
}
