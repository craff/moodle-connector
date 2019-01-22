package fr.openent.moodle.service;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public interface MoodleWebService{
    /**
     * Create a course
     * @param course course to create
     * @param handler function handler returning data
     */
    void create (JsonObject course, Handler<Either<String, JsonObject>> handler);

    /**
     * Delete a course
     * @param id course id
     * @param handler function handler returning data
     */
    void delete(String id, Handler<Either<String, JsonObject>> handler);
}
