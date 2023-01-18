package fr.openent.moodle.service;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;

public interface ModuleNeoRequestService {

    /**
     * get users
     *
     * @param usersIds
     * @param handler
     */
    void getUsers(JsonArray usersIds, Handler<Either<String, JsonArray>> handler);

    /**
     * get groups
     *
     * @param groupsIds
     * @param handler
     */
    void getGroups(JsonArray groupsIds, Handler<Either<String, JsonArray>> handler);

    /**
     * get sharedbookmark
     *
     * @param bookmarksIds
     * @param handler
     */
    void getSharedBookMark(JsonArray bookmarksIds, Handler<Either<String, JsonArray>> handler);

    void getSharedBookMarkUsers(JsonArray bookmarksIds, Handler<Either<String, JsonArray>> handler);
}
