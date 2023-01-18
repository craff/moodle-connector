package fr.openent.moodle.service;

import io.vertx.core.Handler;
import fr.wseduc.webutils.Either;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public interface MoodleEventBus {

    /**
     * Get email to create a course
     * @param action Method call
     * @param handler function handler returning data
     */
    void getParams (JsonObject action, Handler<Either<String, JsonObject>> handler);

    /**
     * get image to bus
     * @param idImage User Id
     * @param handler function handler returning data
     */

    void getImage (String idImage, Handler<Either<String,JsonObject>> handler);

    /**
     * get users & groups to bus
     * @param groupIds Group Ids
     * @param handler function handler returning data
     */

    void getUsers (JsonArray groupIds, Handler<Either<String,JsonArray>> handler);

    /**
     * get users & groups to bus
     * @param zimbraEmail Array with user(s) id
     * @param handler function handler returning data
     */

    void getZimbraEmail(final JsonArray zimbraEmail, final Handler<Either<String, JsonObject>> handler);

    /**
     * Update resources in Mediacentre
     * @param updateCourse JsonArray with the id to publish
     * @param handler function handler returning data
     */

    void updateInMediacentre(JsonObject updateCourse, final Handler<Either<String, JsonObject>> handler);

    /**
     * Create resources in Mediacentre
     * @param id JsonArray with the id to publish
     * @param handler function handler returning data
     */

    void publishInMediacentre(JsonArray id, final Handler<Either<String, JsonObject>> handler);

    /**
     * Delete resources in Mediacentre
     * @param deleteEvent Resource JsonObject with the id to delete
     * @param handler function handler returning data
     */

    void deleteResourceInMediacentre(JsonObject deleteEvent, final Handler<Either<String, JsonObject>> handler);

    /**
     * Modify metadata resources in Mediacentre
     * @param updateMetadata Resource JsonObject with the id to modify
     * @param handler function handler returning data
     */

    void updateResourceInMediacentre(JsonObject updateMetadata, final Handler<Either<String, JsonObject>> handler);
}
