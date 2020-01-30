package fr.openent.moodle.service;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

public interface getShareProcessingService {

    /**
     * Fill the final shareJson with groups
     *
     * @param userEnrolmentsArray JsonObject with Moodle users
     * @param shareJsonInfosFinal JsonObject to fill
     * @param request             Http request
     * @param rightToAdd          ENT Share rights
     * @param handler             function handler returning data
     */
    void shareTreatmentForGroups(JsonObject userEnrolmentsArray, JsonObject shareJsonInfosFinal, HttpServerRequest request, JsonArray rightToAdd, Handler<Either<String, JsonObject>> handler);

    /**
     * Fill the final shareJson with users
     *
     * @param userEnrolmentsArray JsonObject with Moodle Users
     * @param shareJsonInfosFinal JsonObject to fill
     * @param user                User
     * @param rightToAdd          ENT Share right
     * @param handler             function handler returning data
     */
    void shareTreatmentForUsers(JsonObject userEnrolmentsArray, JsonObject shareJsonInfosFinal, UserInfos user, JsonArray rightToAdd, Handler<Either<String, JsonObject>> handler);
}
