package fr.openent.moodle.controllers;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.helper.HttpClientHelper;
import fr.openent.moodle.security.AccessRight;
import fr.openent.moodle.service.impl.DefaultModuleSQLRequestService;
import fr.openent.moodle.service.impl.DefaultMoodleEventBus;
import fr.openent.moodle.service.moduleSQLRequestService;
import fr.openent.moodle.service.moodleEventBus;
import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.response.DefaultResponseHandler;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.storage.Storage;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.http.RouteMatcher;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import static fr.openent.moodle.Moodle.*;
import static fr.wseduc.webutils.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class MoodleController extends ControllerHelper {

    private final moduleSQLRequestService moduleSQLRequestService;
    private final moodleEventBus moodleEventBus;
    private final Storage storage;

    private EventStore eventStore;

    private enum MoodleEvent {ACCESS}

    private final String userMail;

    public static final String baseWsMoodleUrl = (moodleConfig.getString("address_moodle") + moodleConfig.getString("ws-path"));

    @Override
    public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
                     Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);
        eventStore = EventStoreFactory.getFactory().getEventStore(Moodle.class.getSimpleName());
    }

    public MoodleController(final Storage storage, EventBus eb) {
        super();
        this.eb = eb;
        this.storage = storage;

        this.moduleSQLRequestService = new DefaultModuleSQLRequestService(Moodle.moodleSchema, "course");
        this.moodleEventBus = new DefaultMoodleEventBus(eb);

        //todo remove mail constant and add mail from zimbra, ent ...
        this.userMail = Moodle.moodleConfig.getString("userMail");

    }

    /**
     * Displays the home view.
     *
     * @param request Client request
     */
    @Get("")
    @SecuredAction(workflow_view)
    public void view(HttpServerRequest request) {
        renderView(request);
        eventStore.createAndStoreEvent(MoodleEvent.ACCESS.name(), request);
    }

    @ApiDoc("public Get picture for moodle website")
    @Get("/files/:id")
    @ResourceFilter(AccessRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void getFile(HttpServerRequest request) {
        String idImage = request.getParam("id");
        moodleEventBus.getImage(idImage, event -> {
            if (event.isRight()) {
                JsonObject document = event.right().getValue();
                JsonObject metadata = document.getJsonObject("metadata");
                String contentType = metadata.getString("content-type");
                storage.readFile(document.getString("file"), buffer ->
                        request.response()
                                .setStatusCode(200)
                                .putHeader("Content-Type", contentType)
                                .end(buffer));
            } else {
                badRequest(request);
            }
        });
    }

    @ApiDoc("get info image workspace")
    @Get("info/image/:id/")
    @ResourceFilter(AccessRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void getInfoImg(final HttpServerRequest request) {
        try {
            moodleEventBus.getImage(request.getParam("id"), DefaultResponseHandler.defaultResponseHandler(request));
        } catch (Exception e) {
            log.error("Gail to get image workspace", e);
        }
    }

    @Get("/choices")
    @ApiDoc("get a choice")
    @ResourceFilter(AccessRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void getChoices(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                String userId = user.getUserId();
                moduleSQLRequestService.getChoices(userId, arrayResponseHandler(request));
            } else {
                log.error("User not found in session.");
                unauthorized(request);
            }
        });
    }

    @Put("/choices/:view")
    @ApiDoc("set a choice")
    @ResourceFilter(AccessRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void setChoice(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "courses", courses ->
                UserUtils.getUserInfos(eb, request, user -> {
                    if (user != null) {
                        courses.put("userId", user.getUserId());
                        String view = request.getParam("view");
                        moduleSQLRequestService.setChoice(courses, view, defaultResponseHandler(request));
                    } else {
                        log.error("User not found in session.");
                        unauthorized(request);
                    }
                }));
    }

    private void createUpdateWSUrlCreateuser(UserInfos user, Handler<Either<String, Buffer>> handlerUpdateUser)
            throws UnsupportedEncodingException {
        JsonObject body = new JsonObject();
        JsonObject userJson = new JsonObject()
                .put("username",user.getUserId())
                .put("firstname",user.getFirstName())
                .put("lastname",user.getLastName())
                .put("id",user.getUserId())
                .put("email",this.userMail);
        body.put("parameters", new JsonArray().add(userJson))
                .put("wstoken", moodleConfig.getString("wsToken"))
                .put("wsfunction", WS_POST_CREATE_OR_UPDATE_USER)
                .put("moodlewsrestformat", JSON);
        HttpClientHelper.webServiceMoodlePost(body, baseWsMoodleUrl, vertx, handlerUpdateUser);
    }
}
