package fr.openent.moodle.service;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public interface MoodleService {

    void registerUserInPublicCourse(JsonArray usersId, Integer courseId, Vertx vertx, JsonObject moodleClient,
                                    Handler<Either<String, JsonArray>> handler);

    void getAuditeur(Integer courseId, Vertx vertx, JsonObject moodleClient, Handler<Either<String, JsonArray>> handler);
}
