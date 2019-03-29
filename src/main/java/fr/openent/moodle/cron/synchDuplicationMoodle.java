package fr.openent.moodle.cron;
import fr.openent.moodle.Moodle;
import fr.openent.moodle.helper.HttpClientHelper;
import fr.openent.moodle.service.MoodleWebService;
import fr.openent.moodle.service.impl.DefaultMoodleWebService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicBoolean;

import static fr.openent.moodle.Moodle.*;

public class synchDuplicationMoodle extends ControllerHelper implements Handler<Long> {

    private final MoodleWebService moodleWebService;
    private final HttpClientHelper httpClientHelper;

    public synchDuplicationMoodle(Vertx vertx) {
        this.moodleWebService = new DefaultMoodleWebService(Moodle.moodleSchema, "course");
        this.httpClientHelper = new HttpClientHelper();
        this.vertx = vertx;
    }

    @Override
    public void handle(Long event) {
        String status = "en attente";
        moodleWebService.getCourseIdToDuplicate(status, new Handler<Either<String, JsonObject>>() {
            @Override
            public void handle(Either<String, JsonObject> event) {
                JsonObject courseToDuplicate = new JsonObject();
                courseToDuplicate.put("courseid", event.right().getValue().getInteger("id_course"));
                courseToDuplicate.put("userid", event.right().getValue().getString("id_users"));
                courseToDuplicate.put("id", event.right().getValue().getInteger("id"));
                if (event.isRight()) {
                    final AtomicBoolean responseIsSent = new AtomicBoolean(false);
                    URI moodleUri = null;
                    try {
                        final String service = moodleConfig.getString("address_moodle") + moodleConfig.getString("ws-path");
                        final String urlSeparator = service.endsWith("") ? "" : "/";
                        moodleUri = new URI(service + urlSeparator);
                    } catch (URISyntaxException e) {
                        log.debug("Invalid moodle web service uri", e);
                    }
                    if (moodleUri != null) {
                        JsonObject shareSend = new JsonObject();
                        shareSend = null;
                        final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx);
                        final String moodleUrl = moodleUri.toString() +
                                "?wstoken=" + WSTOKEN +
                                "&wsfunction=" + WS_POST_DUPLICATECOURSE +
                                "&parameters[idnumber]=" + courseToDuplicate.getString("userid") +
                                "&parameters[course][0][moodlecourseid]=" + courseToDuplicate.getInteger("courseid") +
                                "&moodlewsrestformat=" + JSON;
                        httpClientHelper.webServiceMoodlePost(shareSend, moodleUrl, httpClient, responseIsSent, new Handler<Either<String, Buffer>>() {
                            @Override
                            public void handle(Either<String, Buffer> event) {
                                if (event.isRight()) {
                                    final String status = "en cours";
                                    Integer id = courseToDuplicate.getInteger("id");
                                    moodleWebService.updateStatusCourseToDuplicate(status, id, new Handler<Either<String, JsonObject>>() {
                                        @Override
                                        public void handle(Either<String, JsonObject> event) {
                                            if (event.isRight()) {
                                                log.error("test");
                                            } else {
                                                handle(new Either.Left<>("There are no course waiting to be duplicate"));
                                            }
                                        }
                                    });
                                } else {
                                    handle(new Either.Left<>("Duplication web-service failed"));
                                }
                            }
                        });
                    }
                } else {
                    handle(new Either.Left<>("There are no course to duplicate in the duplication table"));
                }
            }
        });
    }
        //Maj de la BDD
}