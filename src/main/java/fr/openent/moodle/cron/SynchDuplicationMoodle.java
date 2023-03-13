package fr.openent.moodle.cron;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.helper.HttpClientHelper;
import fr.openent.moodle.service.impl.DefaultModuleSQLRequestService;
import fr.openent.moodle.service.ModuleSQLRequestService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;

import static fr.openent.moodle.Moodle.*;
import static fr.openent.moodle.core.Field.*;

public class SynchDuplicationMoodle extends ControllerHelper implements Handler<Long> {

    private final ModuleSQLRequestService moduleSQLRequestService;
    private final JsonObject moodleClient;

    public SynchDuplicationMoodle(Vertx vertx) {
        this.vertx = vertx;
        this.moduleSQLRequestService = new DefaultModuleSQLRequestService(Moodle.moodleSchema, "course");
        moodleClient = moodleMultiClient.getJsonObject(moodleConfig.getString("host")
                .replace("http://","").replace("https://",""));
    }

    @Override
    public void handle(Long event) {
        log.debug(String.format("[Moodle@%s::handle] Moodle cron started", this.getClass().getSimpleName()));
        synchronisationDuplication(event1 -> {
            if(event1.isRight())
                log.debug(String.format("[Moodle@%s::handle] Cron launch successful", this.getClass().getSimpleName()));
            else
                log.debug(String.format("[Moodle@%s::handle] Cron synchonisation not full",
                        this.getClass().getSimpleName()));
        });
    }

    private void synchronisationDuplication(final Handler<Either<String, JsonObject>> eitherHandler) {
        String status = PENDING;
        moduleSQLRequestService.deleteFinishedCoursesDuplicate(deleteEvent -> {
            if (deleteEvent.isRight()) {
                moduleSQLRequestService.getCourseIdToDuplicate(status, event -> {
                    if (event.isRight()) {
                        if (event.right().getValue().size() < moodleConfig.getInteger("numberOfMaxPendingDuplication")) {
                            String status1 = WAITING;
                            moduleSQLRequestService.getCourseIdToDuplicate(status1, getCourseEvent -> {
                                if (getCourseEvent.isRight()) {
                                    if (getCourseEvent.right().getValue().size() != 0) {
                                        JsonObject courseDuplicate = getCourseEvent.right().getValue().getJsonObject(0);
                                        JsonObject courseToDuplicate = new JsonObject();
                                        courseToDuplicate.put("courseid", courseDuplicate.getInteger("id_course"))
                                                .put("userid", courseDuplicate.getString("id_users"))
                                                .put("folderId", courseDuplicate.getInteger("id_folder"))
                                                .put(ID, courseDuplicate.getInteger(ID))
                                                .put(ATTEMPTS_NUMBER, courseDuplicate.getInteger("nombre_tentatives"))
                                                .put("category_id", courseDuplicate.getInteger("category_id"))
                                                .put("auditeur_id", courseDuplicate.getString("auditeur"));

                                        URI moodleUri = null;
                                        try {
                                            final String service = moodleClient.getString("address_moodle") + moodleClient.getString("ws-path");
                                            moodleUri = new URI(service);
                                        } catch (URISyntaxException e) {
                                            log.error(String.format("[Moodle@%s::synchronisationDuplication] Invalid moodle web service sending demand of duplication uri : %s",
                                                    this.getClass().getSimpleName(), e.getMessage()));
                                        }
                                        if (moodleUri != null) {
                                            String moodleUrl;
                                            if (courseToDuplicate.getInteger("category_id").equals(moodleConfig.getInteger("publicBankCategoryId"))) {
                                                moodleUrl = moodleUri +
                                                        "?wstoken=" + moodleClient.getString("wsToken") +
                                                        "&wsfunction=" + WS_POST_DUPLICATECOURSE +
                                                        "&parameters[idnumber]=" + courseToDuplicate.getString("userid") +
                                                        "&parameters[course][0][moodlecourseid]=" + courseToDuplicate.getInteger("courseid") +
                                                        "&parameters[course][0][ident]=" + courseToDuplicate.getInteger(ID) +
                                                        "&moodlewsrestformat=" + JSON +
                                                        "&parameters[auditeurid]=" + courseToDuplicate.getString("auditeur_id") +
                                                        "&parameters[course][0][categoryid]=" + courseToDuplicate.getInteger("category_id");
                                            } else {
                                                moodleUrl = moodleUri +
                                                        "?wstoken=" + moodleClient.getString("wsToken") +
                                                        "&wsfunction=" + WS_POST_DUPLICATECOURSE +
                                                        "&parameters[idnumber]=" + courseToDuplicate.getString("userid") +
                                                        "&parameters[course][0][moodlecourseid]=" + courseToDuplicate.getInteger("courseid") +
                                                        "&parameters[course][0][ident]=" + courseToDuplicate.getInteger(ID) +
                                                        "&moodlewsrestformat=" + JSON +
                                                        "&parameters[auditeurid]=" + "" +
                                                        "&parameters[course][0][categoryid]=" + courseToDuplicate.getInteger("category_id");
                                            }
                                            moduleSQLRequestService.updateStatusCourseToDuplicate(WAITING,
                                                        courseToDuplicate.getInteger(ID),
                                                        courseToDuplicate.getInteger(ATTEMPTS_NUMBER), updateEvent -> {
                                                if (updateEvent.isRight()) {
                                                    try {
                                                        HttpClientHelper.webServiceMoodlePost(null, moodleUrl, vertx, moodleClient, postEvent -> {
                                                            if (postEvent.isRight()) {
                                                                log.info(String.format("[Moodle@%s::synchronisationDuplication] Duplication request sent to Moodle",
                                                                        this.getClass().getSimpleName()));
                                                                eitherHandler.handle(new Either.Right<>(postEvent.right().getValue().toJsonArray()
                                                                        .getJsonObject(0).getJsonArray(COURSES).getJsonObject(0)));
                                                            } else {
                                                                log.error(String.format("[Moodle@%s::synchronisationDuplication] Failed to contact Moodle : %s",
                                                                        this.getClass().getSimpleName(), postEvent.left().getValue()));
                                                                eitherHandler.handle(new Either.Left<>("Failed to contact Moodle"));
                                                            }
                                                        });
                                                    } catch (UnsupportedEncodingException e) {
                                                        log.error(String.format("[Moodle@%s::synchronisationDuplication] Fail to encode JSON : %s",
                                                                this.getClass().getSimpleName(), e.getMessage()));
                                                        eitherHandler.handle(new Either.Left<>("failed to create webServiceMoodlePost"));
                                                    }
                                                } else {
                                                    log.error(String.format("[Moodle@%s::synchronisationDuplication] Failed to update database updateStatusCourseToDuplicate : %s",
                                                            this.getClass().getSimpleName(), updateEvent.left().getValue()));
                                                    eitherHandler.handle(new Either.Left<>("Failed to update database updateStatusCourseToDuplicate"));
                                                }
                                            });
                                        }
                                    } else {
                                        eitherHandler.handle(new Either.Left<>("There are no course to duplicate in the duplication table"));
                                    }
                                } else {
                                    log.error(String.format("[Moodle@%s::synchronisationDuplication] The access to duplicate database failed ! : %s",
                                            this.getClass().getSimpleName(), getCourseEvent.left().getValue()));
                                    eitherHandler.handle(new Either.Left<>("The access to duplicate database failed"));
                                }
                            });
                        } else {
                            eitherHandler.handle(new Either.Left<>("The quota of duplication in same time is reached, you have to wait"));
                        }
                    } else {
                        log.error(String.format("[Moodle@%s::synchronisationDuplication] The access to duplicate database failed ! : %s",
                                this.getClass().getSimpleName(), event.left().getValue()));
                        eitherHandler.handle(new Either.Left<>("The access to duplicate database failed"));
                    }
                });
            } else {
                log.error(String.format("[Moodle@%s::synchronisationDuplication] Problem to delete finished duplicate courses ! : %s",
                        this.getClass().getSimpleName(), deleteEvent.left().getValue()));
                eitherHandler.handle(new Either.Left<>("Problem to delete finished duplicate courses"));
            }
        });
    }
}
