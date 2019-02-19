package fr.openent.moodle.service;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public interface MoodleWebService{
    /**
     * Create a course
     * @param course course to create
     * @param handler function handler returning data
     */
    void createCourse (JsonObject course, Handler<Either<String, JsonObject>> handler);

    /**
     * Delete a course
     * @param course course to delete
     * @param handler function handler returning data
     */
    void deleteCourse(JsonObject course, Handler<Either<String, JsonObject>> handler);

    /**
     * is containt a object with value of courid
     * @param courid
     * @return
     */
    boolean getValueMoodleIdinEnt(Integer courid,JsonArray object);

    /**
     * get courses and shared by users
     * @param userId
     * @param eitherHandler
     */
    void getCoursesByUserInEnt(String userId, Handler<Either<String, JsonArray>> eitherHandler);

}
