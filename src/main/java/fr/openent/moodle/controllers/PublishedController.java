package fr.openent.moodle.controllers;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.security.PublicateRight;
import fr.openent.moodle.service.impl.DefaultModuleSQLRequestService;
import fr.openent.moodle.service.impl.DefaultMoodleEventBus;
import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.UserUtils;

import static fr.openent.moodle.Moodle.*;
import static fr.wseduc.webutils.http.response.DefaultResponseHandler.arrayResponseHandler;

public class PublishedController extends ControllerHelper {

    private final fr.openent.moodle.service.moduleSQLRequestService moduleSQLRequestService;
    private final fr.openent.moodle.service.moodleEventBus moodleEventBus;
    public PublishedController(EventBus eb) {
        super();
        this.moduleSQLRequestService = new DefaultModuleSQLRequestService(Moodle.moodleSchema, "course");
        this.moodleEventBus = new DefaultMoodleEventBus(eb);
    }

    @Get("/levels")
    @ApiDoc("get all levels")
    @ResourceFilter(PublicateRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void getLevels (HttpServerRequest request) {
        moduleSQLRequestService.getLevels(arrayResponseHandler(request));
    }


    @Get("/disciplines")
    @ApiDoc("get all disciplines")
    @ResourceFilter(PublicateRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void getDisciplines (HttpServerRequest request) {
        moduleSQLRequestService.getDisciplines(arrayResponseHandler(request));
    }

    @Post("/course/publish")
    @ApiDoc("Publish a course in BP")
    @SecuredAction(workflow_publish)
    public void publish (HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "duplicate", duplicateCourse ->
                UserUtils.getUserInfos(eb, request, user -> {
                    duplicateCourse.put("userid", user.getUserId());
                    duplicateCourse.put("username", user.getFirstName().toUpperCase() + " " + user.getLastName());
                    duplicateCourse.put("author", duplicateCourse.getJsonArray("authors").getJsonObject(0).getString("firstname").toUpperCase() +
                            " " + duplicateCourse.getJsonArray("authors").getJsonObject(0).getString("lastname").toUpperCase());
                    duplicateCourse.put("author_id", duplicateCourse.getJsonArray("authors").getJsonObject(0).getString("entidnumber"));
                    moduleSQLRequestService.insertPublishedCourseMetadata(duplicateCourse, event -> {
                        if (event.isRight()) {
                            JsonObject courseToPublish = new JsonObject();
                            courseToPublish.put("userId", user.getUserId());
                            courseToPublish.put("courseid", duplicateCourse.getJsonArray("coursesId").getInteger(0));
                            courseToPublish.put("folderId", 0);
                            courseToPublish.put("status", WAITING);
                            courseToPublish.put("category_id", moodleConfig.getInteger("publicBankCategoryId"));
                            courseToPublish.put("auditeur_id", user.getUserId());
                            courseToPublish.put("publishFK", event.right().getValue().getInteger("id"));

                            moduleSQLRequestService.insertDuplicateTable(courseToPublish, new Handler<Either<String, JsonObject>>() {
                                @Override
                                public void handle(Either<String, JsonObject> event) {
                                    if (event.isRight()) {
                                        request.response()
                                                .setStatusCode(200)
                                                .end();
                                    } else {
                                        handle(new Either.Left<>("Failed to insert in duplicate table"));
                                    }
                                }
                            });
                        } else {
                            log.error("Failed to insert in publication table");
                        }
                    });
                }));
    }

    @Post("/metadata/update")
    @ApiDoc("Update public course metadata")
    @ResourceFilter(PublicateRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void updatePublicCourseMetadata(HttpServerRequest request) {
        RequestUtils.bodyToJson(request, updateMetadata -> {
            Integer course_id = updateMetadata.getJsonArray("coursesId").getInteger(0);
            JsonObject newMetadata = new JsonObject();

            JsonArray levelArray = new JsonArray();
            for (int i = 0; i < updateMetadata.getJsonArray("levels").size(); i++) {
                levelArray.add((updateMetadata.getJsonArray("levels").getJsonObject(i).getString("label")));
            }
            if(levelArray.isEmpty()) {
                levelArray.add("");
            }
            newMetadata.put("level_label", levelArray);

            JsonArray disciplineArray = new JsonArray();
            for (int i = 0; i < updateMetadata.getJsonArray("disciplines").size(); i++) {
                disciplineArray.add((updateMetadata.getJsonArray("disciplines").getJsonObject(i).getString("label")));
            }
            if(disciplineArray.isEmpty()) {
                disciplineArray.add("");
            }
            newMetadata.put("discipline_label", disciplineArray);

            JsonArray plainTextArray = new JsonArray();
            for (int i = 0; i < updateMetadata.getJsonArray("plain_text").size(); i++) {
                plainTextArray.add((updateMetadata.getJsonArray("plain_text").getJsonObject(i).getString("label")));
            }
            if(plainTextArray.isEmpty()) {
                plainTextArray.add("");
            }
            newMetadata.put("plain_text", plainTextArray);
            callMediacentreEventBusToUpdateMetadata(updateMetadata, moodleEventBus, ebEvent -> {
                if (ebEvent.isRight()) {
                    moduleSQLRequestService.updatePublicCourseMetadata(course_id, newMetadata, event -> {
                        if (event.isRight()) {
                            request.response()
                                    .setStatusCode(200)
                                    .end();
                        } else {
                            log.error("Problem updating the public course metadata : " + event.left().getValue());
                            unauthorized(request);
                        }
                    });
                } else {
                    log.error("Problem with ElasticSearch updating : " + ebEvent.left().getValue());
                    unauthorized(request);
                }
            });
        });
    }

    static public void callMediacentreEventBusForPublish(JsonArray id, fr.openent.moodle.service.moodleEventBus eventBus,
                                                         final Handler<Either<String, JsonObject>> handler) {
        eventBus.publishInMediacentre(id, handler);
    }

    static public void callMediacentreEventBusToDelete(HttpServerRequest request, fr.openent.moodle.service.moodleEventBus eventBus,
                                                       final Handler<Either<String, JsonObject>> handler) {
        RequestUtils.bodyToJson(request, deleteEvent -> eventBus.deleteResourceInMediacentre(deleteEvent, handler));
    }

    public void callMediacentreEventBusToUpdateMetadata(JsonObject updateMetadata, fr.openent.moodle.service.moodleEventBus eventBus,
                                                        final Handler<Either<String, JsonObject>> handler) {
        eventBus.updateResourceInMediacentre(updateMetadata, handler);
    }

    static public void callMediacentreEventBusToUpdate(JsonObject updateCourse, fr.openent.moodle.service.moodleEventBus eventBus,
                                                       final Handler<Either<String, JsonObject>> handler) {
        eventBus.updateInMediacentre(updateCourse, handler);
    }
}
