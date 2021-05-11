package fr.openent.moodle.cron;
import fr.openent.moodle.Moodle;
import fr.openent.moodle.controllers.MoodleController;
import fr.openent.moodle.helper.HttpClientHelper;
import fr.openent.moodle.service.impl.DefaultModuleSQLRequestService;
import fr.openent.moodle.service.moduleSQLRequestService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;

import static fr.openent.moodle.Moodle.*;

public class synchDuplicationMoodle extends ControllerHelper implements Handler<Long> {

    MoodleController moodleController;
    private final moduleSQLRequestService moduleSQLRequestService;

    public synchDuplicationMoodle(Vertx vertx, MoodleController moodleController) {
        this.moodleController = moodleController;
        this.vertx = vertx;
        this.moduleSQLRequestService = new DefaultModuleSQLRequestService(Moodle.moodleSchema, "course");
    }

    @Override
    public void handle(Long event) {
        log.debug("Moodle cron started");
        synchronisationDuplication(event1 -> {
            if(event1.isRight())
                log.debug("Cron launch successful");
            else
                log.debug("Cron synchonisation not full");
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
                                                .put("id", courseDuplicate.getInteger("id"))
                                                .put("attemptsNumber", courseDuplicate.getInteger("nombre_tentatives"))
                                                .put("category_id", courseDuplicate.getInteger("category_id"))
                                                .put("auditeur_id", courseDuplicate.getString("auditeur"));

                                        URI moodleUri = null;
                                        try {
                                            final String service = moodleConfig.getString("address_moodle") + moodleConfig.getString("ws-path");
                                            moodleUri = new URI(service);
                                        } catch (URISyntaxException e) {
                                            log.error("Invalid moodle web service sending demand of duplication uri", e);
                                        }
                                        if (moodleUri != null) {
                                            String moodleUrl;
                                            if (courseToDuplicate.getInteger("category_id").equals(moodleConfig.getInteger("publicBankCategoryId"))) {
                                                moodleUrl = moodleUri +
                                                        "?wstoken=" + moodleConfig.getString("wsToken") +
                                                        "&wsfunction=" + WS_POST_DUPLICATECOURSE +
                                                        "&parameters[idnumber]=" + courseToDuplicate.getString("userid") +
                                                        "&parameters[course][0][moodlecourseid]=" + courseToDuplicate.getInteger("courseid") +
                                                        "&parameters[course][0][ident]=" + courseToDuplicate.getInteger("id") +
                                                        "&moodlewsrestformat=" + JSON +
                                                        "&parameters[auditeurid]=" + courseToDuplicate.getString("auditeur_id") +
                                                        "&parameters[course][0][categoryid]=" + courseToDuplicate.getInteger("category_id");
                                            } else {
                                                moodleUrl = moodleUri +
                                                        "?wstoken=" + moodleConfig.getString("wsToken") +
                                                        "&wsfunction=" + WS_POST_DUPLICATECOURSE +
                                                        "&parameters[idnumber]=" + courseToDuplicate.getString("userid") +
                                                        "&parameters[course][0][moodlecourseid]=" + courseToDuplicate.getInteger("courseid") +
                                                        "&parameters[course][0][ident]=" + courseToDuplicate.getInteger("id") +
                                                        "&moodlewsrestformat=" + JSON +
                                                        "&parameters[auditeurid]=" + "" +
                                                        "&parameters[course][0][categoryid]=" + courseToDuplicate.getInteger("category_id");
                                            }
                                            try {
                                                HttpClientHelper.webServiceMoodlePost(null, moodleUrl, vertx, postEvent -> {
                                                    if (postEvent.isRight()) {
                                                        log.info("Duplication request sent to Moodle");
                                                        eitherHandler.handle(new Either.Right<>(postEvent.right().getValue().toJsonArray()
                                                                .getJsonObject(0).getJsonArray("courses").getJsonObject(0)));
                                                    } else {
                                                        log.error("Failed to contact Moodle");
                                                        eitherHandler.handle(new Either.Left<>("Failed to contact Moodle"));
                                                    }
                                                });
                                            } catch (UnsupportedEncodingException e) {
                                                log.error("Fail to encode JSON",e);
                                                eitherHandler.handle(new Either.Left<>("failed to create webServiceMoodlePost"));
                                            }
                                        }
                                    } else {
                                        eitherHandler.handle(new Either.Left<>("There are no course to duplicate in the duplication table"));
                                    }
                                } else {
                                    log.error("The access to duplicate database failed !");
                                    eitherHandler.handle(new Either.Left<>("The access to duplicate database failed"));
                                }
                            });
                        } else {
                            eitherHandler.handle(new Either.Left<>("The quota of duplication in same time is reached, you have to wait"));
                        }
                    } else {
                        log.error("The access to duplicate database failed !");
                        eitherHandler.handle(new Either.Left<>("The access to duplicate database failed"));
                    }
                });
            } else {
                log.error("Problem to delete finished duplicate courses !");
                eitherHandler.handle(new Either.Left<>("Problem to delete finished duplicate courses"));
            }
        });
    }
}
