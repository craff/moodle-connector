package fr.openent.moodle.service;

import fr.wseduc.webutils.Either;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Map;

public interface postShareProcessingService {

    void getResultUsers(JsonObject shareCourseObject, JsonArray usersIds, Map<String, Object> idUsers, JsonObject idFront, JsonObject keyShare, Handler<Either<String, JsonArray>> handler);

    void getResultGroups(JsonObject shareCourseObject, JsonArray groupsIds, Map<String, Object> idGroups, JsonObject idFront, JsonObject keyShare, Handler<Either<String, JsonArray>> handler);

    void getResultBookmarks(JsonObject shareCourseObject, JsonArray bookmarksIds, Map<String, Object> idBookmarks, JsonObject idFront, JsonObject keyShare, Handler<Either<String, JsonArray>> handler);

    void getUsersFuture(JsonArray usersIds, Future<JsonArray> getUsersFuture);

    void getUsersInGroupsFuture(JsonArray groupsIds, Future<JsonArray> getUsersInGroupsFuture);

    void getUsersInBookmarksFuture(JsonArray bookmarksIds, Future<JsonArray> getBookmarksFuture);

    void getUsersInBookmarksFutureLoop(JsonObject shareObjectToFill, Map<String, Object> mapInfo, JsonArray bookmarksFutureResult, List<Future> listUsersFutures, List<Integer> listRankGroup, int i);

    void processUsersInBookmarksFutureResult(JsonObject shareObjectToFill, List<Future> listUsersFutures, List<Integer> listRankGroup);
}
