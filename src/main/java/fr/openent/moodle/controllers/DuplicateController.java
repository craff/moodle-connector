package fr.openent.moodle.controllers;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.security.DuplicateRight;
import fr.openent.moodle.service.MoodleEventBus;
import fr.openent.moodle.service.impl.DefaultModuleSQLRequestService;
import fr.openent.moodle.service.impl.DefaultMoodleEventBus;
import fr.openent.moodle.service.ModuleSQLRequestService;
import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Delete;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.http.RouteMatcher;

import java.util.Map;

import static fr.openent.moodle.Moodle.*;
import static fr.openent.moodle.controllers.PublishedController.callMediacentreEventBusForPublish;
import static fr.wseduc.webutils.http.response.DefaultResponseHandler.arrayResponseHandler;

public class DuplicateController extends ControllerHelper {

    private final ModuleSQLRequestService moduleSQLRequestService;
    private final MoodleEventBus moodleEventBus;

    @Override
    public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
                     Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);
    }

    public DuplicateController(EventBus eb) {
        super();
        this.eb = eb;
        this.moduleSQLRequestService = new DefaultModuleSQLRequestService(Moodle.moodleSchema, "course");
        this.moodleEventBus = new DefaultMoodleEventBus(eb);
    }

    @Post("/course/duplicate")
    @ApiDoc("Duplicate courses")
    @SecuredAction(workflow_duplicate)
    public void duplicate(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "duplicate", duplicateCourse ->
                UserUtils.getUserInfos(eb, request, user -> {
                    JsonArray courseId = duplicateCourse.getJsonArray("coursesId");
                    JsonObject courseToDuplicate = new JsonObject();
                    courseToDuplicate.put("folderId", duplicateCourse.getInteger("folderId"));
                    courseToDuplicate.put("status", WAITING);
                    courseToDuplicate.put("userId", user.getUserId());
                    courseToDuplicate.put("category_id", 0);
                    for (int i = 0; i < courseId.size(); i++) {
                        courseToDuplicate.put("courseid", courseId.getValue(i));
                        moduleSQLRequestService.insertDuplicateTable(courseToDuplicate, new Handler<Either<String, JsonObject>>() {
                            @Override
                            public void handle(Either<String, JsonObject> event) {
                                if (event.isRight()) {
                                    request.response()
                                            .setStatusCode(200)
                                            .end();
                                } else {
                                    handle(new Either.Left<>("Failed to insert in database"));
                                }
                            }
                        });
                    }
                }));
    }

    @Post("/course/duplicate/BP/:id")
    @ApiDoc("Duplicate a BP course")
    @ResourceFilter(DuplicateRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void duplicatePublicCourse(HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            int courseId = 0;

            JsonObject courseToDuplicate = new JsonObject();
            courseToDuplicate.put("folderId", 0);
            courseToDuplicate.put("status", WAITING);
            courseToDuplicate.put("userId", user.getUserId());
            courseToDuplicate.put("category_id", moodleConfig.getInteger("mainCategoryId"));

            try {
                courseId = Integer.parseInt(request.getParam("id"));
            } catch (NumberFormatException n){
                Renders.badRequest(request);
                return;
            }

            courseToDuplicate.put("courseid", courseId);
            moduleSQLRequestService.insertDuplicateTable(courseToDuplicate, event -> {
                if (event.isRight()) {
                    request.response()
                            .setStatusCode(202)
                            .end();
                } else {
                    Renders.renderError(request);
                }
            });
        });
    }

    @Get("/duplicateCourses")
    @ApiDoc("Get duplicate courses")
    @ResourceFilter(DuplicateRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void getDuplicateCourses(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                moduleSQLRequestService.deleteFinishedCoursesDuplicate(event -> {
                    if (event.isRight()) {
                        moduleSQLRequestService.getUserCourseToDuplicate(user.getUserId(), arrayResponseHandler(request));
                    } else {
                        log.error("Problem to delete finished duplicate courses !");
                        renderError(request);
                    }
                });
            } else {
                log.error("User not found in session.");
                unauthorized(request);
            }
        });
    }

    @Delete("/courseDuplicate/:id")
    @ApiDoc("delete a duplicateFailed folder")
    @ResourceFilter(DuplicateRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void deleteDuplicateCourse(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                final String status = FINISHED;
                Integer id = Integer.parseInt(request.params().get("id"));
                moduleSQLRequestService.updateStatusCourseToDuplicate(status, id, 1, updateEvent -> {
                    if (updateEvent.isRight()) {
                        moduleSQLRequestService.deleteFinishedCoursesDuplicate(deleteEvent -> {
                            if (deleteEvent.isRight()) {
                                request.response()
                                        .setStatusCode(200)
                                        .end();
                            } else {
                                log.error("Problem to delete finished duplicate courses !");
                                renderError(request);
                            }
                        });
                    } else {
                        log.error("Update Duplicate course database didn't work!");
                        renderError(request);
                    }
                });
            } else {
                log.error("User not found in session.");
                unauthorized(request);
            }
        });
    }

    @Post("/course/duplicate/response")
    @ApiDoc("Duplicate courses")
    @ResourceFilter(DuplicateRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void getMoodleResponse(HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "duplicateResponse", duplicateResponse ->
                UserUtils.getUserInfos(eb, request, user -> {
                    switch (duplicateResponse.getString("status")) {
                        case "pending":
                            pendingResponse(request, duplicateResponse);
                            break;
                        case "finished":
                            finishedResponse(request, duplicateResponse);
                            break;
                        case "busy":
                            log.info("A duplication is already in progress");
                            break;
                        case "error":
                            errorResponse(request, duplicateResponse);
                            break;
                        default:
                            log.error("Failed to read the Moodle response");
                            unauthorized(request);
                    }
                })
        );
    }

    private void errorResponse(HttpServerRequest request, JsonObject duplicateResponse) {
        moduleSQLRequestService.getCourseToDuplicate(Integer.parseInt(duplicateResponse.getString("ident")), duplicateEvent -> {
            if (duplicateEvent.isRight()) {
                Integer nbAttempts = duplicateEvent.right().getValue().getInteger("nombre_tentatives");
                final String status = WAITING;
                Integer id = duplicateResponse.getInteger("ident");
                moduleSQLRequestService.updateStatusCourseToDuplicate(status, id, nbAttempts, updateEvent -> {
                    if (updateEvent.isRight()) {
                        request.response()
                                .setStatusCode(200)
                                .end();
                        log.error("Duplication web-service failed");
                    } else {
                        log.error("Failed to update database updateStatusCourseToDuplicate");
                        unauthorized(request);
                    }
                });
            } else {
                log.error("Failed to access database getCourseToDuplicate");
                unauthorized(request);
            }
        });
    }

    private void finishedResponse(HttpServerRequest request, JsonObject duplicateResponse) {
        moduleSQLRequestService.updateStatusCourseToDuplicate(FINISHED,
                Integer.parseInt(duplicateResponse.getString("ident")), 1, updateEvent -> {
                    if (updateEvent.isRight()) {
                        moduleSQLRequestService.getCourseIdToDuplicate(FINISHED, selectEvent -> {
                            JsonObject infoDuplicateSQL = new JsonObject();
                            infoDuplicateSQL.put("info", selectEvent.right().getValue().getJsonObject(0));
                            JsonObject createCourseDuplicate = new JsonObject();
                            createCourseDuplicate.put("userid", selectEvent.right().getValue().getJsonObject(0).getString("id_users"));
                            createCourseDuplicate.put("folderId", selectEvent.right().getValue().getJsonObject(0).getInteger("id_folder"));
                            createCourseDuplicate.put("moodleid", Integer.parseInt(duplicateResponse.getString("courseid")));
                            moduleSQLRequestService.createCourse(createCourseDuplicate, insertEvent -> {
                                if (insertEvent.isRight()) {
                                    if (!infoDuplicateSQL.getJsonObject("info").getInteger("category_id")
                                            .equals(moodleConfig.getInteger("publicBankCategoryId"))) {
                                        moduleSQLRequestService.deleteFinishedCoursesDuplicate(deleteEvent -> {
                                            if (deleteEvent.isRight()) {
                                                request.response()
                                                        .setStatusCode(200)
                                                        .end();
                                            } else {
                                                log.error("Problem to delete finished duplicate courses !");
                                                unauthorized(request);
                                            }
                                        });
                                    } else {
                                        JsonArray publicationId = new JsonArray().add(Integer.parseInt(duplicateResponse.getString("ident")));
                                        moduleSQLRequestService.getDuplicationId(publicationId, event -> {
                                            createCourseDuplicate.put("duplicationFK", event.right().getValue().getInteger("publishfk"));
                                            moduleSQLRequestService.updatePublishedCourseId(createCourseDuplicate, updatePublicationEvent -> {
                                                if (updatePublicationEvent.isRight()) {
                                                    moduleSQLRequestService.deleteFinishedCoursesDuplicate(deleteEvent -> {
                                                        if (deleteEvent.isRight()) {
                                                            JsonArray id = new JsonArray().add(Integer.parseInt(duplicateResponse.getString("courseid")));
                                                            callMediacentreEventBusForPublish(id, moodleEventBus, mediacentreEvent ->
                                                                    request.response()
                                                                            .setStatusCode(200)
                                                                            .end());
                                                        } else {
                                                            log.error("Problem to delete finished duplicate courses !");
                                                            unauthorized(request);
                                                        }
                                                    });
                                                } else {
                                                    log.error("Problem to update publication table !");
                                                    unauthorized(request);
                                                }
                                            });
                                        });
                                    }
                                } else {
                                    log.error("Problem to insert in course database !");
                                    unauthorized(request);
                                }
                            });
                        });
                    }
                });
    }

    private void pendingResponse(HttpServerRequest request, JsonObject duplicateResponse) {
        moduleSQLRequestService.updateStatusCourseToDuplicate(PENDING,
                Integer.parseInt(duplicateResponse.getString("ident")), 1, event -> {
                    if (event.isRight()) {
                        request.response()
                                .setStatusCode(200)
                                .end();
                    } else {
                        log.error("Cannot update the status of the course in duplication table");
                        unauthorized(request);
                    }
                });
    }
}
