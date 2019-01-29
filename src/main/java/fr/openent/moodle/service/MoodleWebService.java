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
    void create (JsonObject course, Handler<Either<String, JsonObject>> handler);

    /**
     * Delete a course
     * @param course course to delete
     * @param handler function handler returning data
     */
    void delete(JsonObject course, Handler<Either<String, JsonObject>> handler);

    /**
     * get list courses ant shared by folder
     * @param id_folder
     * @param id_user
     * @param handler
     */
    void getCoursInEnt(long id_folder, String id_user, Handler<Either<String, JsonArray>> handler);
    /**
     * is containt a object with value of courid
     * @param courid
     * @return
     */
    boolean getValueMoodleIdinEnt(Integer courid,JsonArray object);

    /**
     * get list folders by user
     * @param id_user
     * @param handler
     */
    void getFoldersInEnt(String id_user,Handler<Either<String, JsonArray>> handler);

    /**
     * count Item In folder
     * @param id_folder
     * @param userId
     * @param defaultResponseHandler
     */
    void countItemInfolder(long id_folder, String userId, Handler<Either<String, JsonObject>> defaultResponseHandler);
}
