package fr.openent.moodle.service.impl;

import fr.openent.moodle.helper.HttpClientHelper;
import fr.openent.moodle.service.moduleNeoRequestService;
import fr.openent.moodle.service.moodleService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicBoolean;

import static fr.openent.moodle.Moodle.*;

public class DefaultMoodleService implements moodleService {

    private final moduleNeoRequestService moduleNeoRequestService;
    private final Logger log = LoggerFactory.getLogger(DefaultMoodleService.class);

    public DefaultMoodleService() {
        super();
        this.moduleNeoRequestService = new DefaultModuleNeoRequestService();
    }

    public void getAuditeur (Integer coursesId, Vertx vertx, Handler<Either<String, JsonArray>> handler) {
        final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx);
        final AtomicBoolean responseIsSent = new AtomicBoolean(false);
        Buffer wsResponse = new BufferImpl();
        final String moodleUrl = (moodleConfig.getString("address_moodle") + moodleConfig.getString("ws-path")) +
                "?wstoken=" + moodleConfig.getString("wsToken") +
                "&wsfunction=" + WS_GET_SHARECOURSE +
                "&parameters[courseid]=" + coursesId +
                "&moodlewsrestformat=" + JSON;

        Handler<HttpClientResponse> getAuditeurHandler = response -> {
            if (response.statusCode() == 200) {
                response.handler(wsResponse::appendBuffer);
                response.endHandler(end -> {
                    JsonArray getResponse = new JsonArray(wsResponse);
                    JsonArray auditeurInfo = getResponse.getJsonObject(0).getJsonArray("enrolments").getJsonObject(0).getJsonArray("users");
                    handler.handle(new Either.Right<>(auditeurInfo));
                    if (!responseIsSent.getAndSet(true)) {
                        httpClient.close();
                    }
                });
            } else {
                log.error("Fail to call get share course right webservice" + response.statusMessage());
                response.bodyHandler(event -> {
                    log.error("Returning body after GET CALL : " + moodleUrl + ", Returning body : " + event.toString("UTF-8"));
                    if (!responseIsSent.getAndSet(true)) {
                        httpClient.close();
                    }
                });
            }
        };

        final HttpClientRequest httpClientRequest = httpClient.getAbs(moodleUrl, getAuditeurHandler);
        httpClientRequest.headers().set("Content-Length", "0");
        //Typically an unresolved Address, a timeout about connection or response
        httpClientRequest.exceptionHandler(event -> {
            log.error(event.getMessage(), event);
            if (!responseIsSent.getAndSet(true)) {
                httpClient.close();
            }
        }).end();
    }

    public void registerUserInPublicCourse(JsonArray usersId, Integer courseId, Vertx vertx, Handler<Either<String, JsonArray>> handler) {
        moduleNeoRequestService.getUsers(usersId, getUsersEvent -> {
            JsonObject shareObjectToFill = new JsonObject();
            shareObjectToFill.put("courseid", courseId);
            shareObjectToFill.put("users", getUsersEvent.right().getValue());

            JsonObject shareSend = new JsonObject();

            shareSend.put("parameters", shareObjectToFill)
                    .put("wstoken", moodleConfig.getString("wsToken"))
                    .put("wsfunction", WS_CREATE_SHARECOURSE)
                    .put("moodlewsrestformat", JSON);

            URI moodleUri = null;
            try {
                final String service = (moodleConfig.getString("address_moodle") + moodleConfig.getString("ws-path"));
                moodleUri = new URI(service);
            } catch (URISyntaxException e) {
                log.error("Invalid moodle web service sending right share uri", e);
            }
            if (moodleUri != null) {
                final String moodleUrl = moodleUri.toString();
                HttpClientHelper.webServiceMoodlePost(shareSend, moodleUrl, vertx, registerEvent -> {
                    if (registerEvent.isRight()) {
                        handler.handle(new Either.Right<>(registerEvent.right().getValue().toJsonArray()));
                    } else {
                        log.error("FAIL WS_CREATE_SHARECOURSE" + registerEvent.left().getValue());
                    }
                });
            }
        });
    }
}
