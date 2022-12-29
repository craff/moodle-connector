package fr.openent.moodle.controllers;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.helper.HttpClientHelper;
import fr.openent.moodle.security.AccessRight;
import fr.openent.moodle.service.impl.DefaultModuleSQLRequestService;
import fr.openent.moodle.service.impl.DefaultMoodleEventBus;
import fr.openent.moodle.service.moduleSQLRequestService;
import fr.openent.moodle.utils.Utils;
import fr.wseduc.rs.*;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.events.EventStore;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.http.RouteMatcher;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static fr.openent.moodle.Moodle.*;
import static fr.openent.moodle.controllers.PublishedController.callMediacentreEventBusToDelete;
import static fr.openent.moodle.controllers.PublishedController.callMediacentreEventBusToUpdate;
import static java.util.Objects.isNull;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class CourseController extends ControllerHelper {
    private final EventStore eventStore;
    private final moduleSQLRequestService moduleSQLRequestService;
    private final fr.openent.moodle.service.moodleEventBus moodleEventBus;


    @Override
    public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
                     Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);
    }

    public CourseController(EventStore eventStore, EventBus eb) {
        super();
        this.eventStore = eventStore;
        this.eb = eb;
        this.moduleSQLRequestService = new DefaultModuleSQLRequestService(Moodle.moodleSchema, "course");
        this.moodleEventBus = new DefaultMoodleEventBus(eb);
    }

    @Post("/course")
    @ApiDoc("create a course")
    @SecuredAction(workflow_createCourse)
    public void create(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "course", course -> {
            if ("1".equals(course.getString("type"))) {
                course.put("typeA", "");
            }
            if (isNull(course.getString("summary"))) {
                course.put("summary", "");
            }

            JsonObject moodleClient = moodleMultiClient.getJsonObject(request.host());

            UserUtils.getUserInfos(eb, request, user -> {
                URI moodleUri = null;
                try {
                    GregorianCalendar calendar = new GregorianCalendar();
                    String uniqueID = UUID.randomUUID().toString();
                    if (user.getLastName().length() < 3)
                        course.put("shortname", calendar.toZonedDateTime().toString().substring(0, 7) +
                                user.getFirstName().charAt(0) + user.getLastName() +
                                course.getString("fullname").substring(0, 4) + uniqueID);
                    else
                        course.put("shortname", calendar.toZonedDateTime().toString().substring(0, 7) +
                                user.getFirstName().charAt(0) + user.getLastName().substring(0, 3) +
                                course.getString("fullname").substring(0, 4) + uniqueID);
                    final String service = (moodleClient.getString("address_moodle") + moodleClient.getString("ws-path"));

                    final String urlSeparator = "";
                    moodleUri = new URI(service + urlSeparator);
                } catch (URISyntaxException error) {
                    Utils.sendErrorRequest(request, "Invalid moodle web service creating a course uri" + error);
                }
                if (moodleUri != null) {
                    try {
                        String urlImage = "", idImage = course.getString("imageurl");
                        if (idImage != null) {
                            urlImage = "&parameters[imageurl]=" +
                                    URLEncoder.encode(getScheme(request), "UTF-8") +
                                    "://" +
                                    URLEncoder.encode(getHost(request), "UTF-8") +
                                    "/moodle/files/" +
                                    URLEncoder.encode(idImage, "UTF-8");
                        }

                        final String moodleUrl = moodleUri +
                                "?wstoken=" + moodleClient.getString("wsToken") +
                                "&wsfunction=" + WS_CREATE_FUNCTION +
                                "&parameters[username]=" + URLEncoder.encode(user.getUserId(), "UTF-8") +
                                "&parameters[idnumber]=" + URLEncoder.encode(user.getUserId(), "UTF-8") +
                                "&parameters[email]=" + URLEncoder.encode(user.getUserId() + "@moodle.net", "UTF-8") +
                                "&parameters[firstname]=" + URLEncoder.encode(user.getFirstName(), "UTF-8") +
                                "&parameters[lastname]=" + URLEncoder.encode(user.getLastName(), "UTF-8") +
                                "&parameters[fullname]=" + URLEncoder.encode(course.getString("fullname"), "UTF-8") +
                                "&parameters[shortname]=" + URLEncoder.encode(course.getString("shortname"), "UTF-8") +
                                "&parameters[categoryid]=" + URLEncoder.encode("" + course.getInteger("categoryid"), "UTF-8") +
                                "&parameters[summary]=" + URLEncoder.encode(course.getString("summary"), "UTF-8") +
                                urlImage +
                                "&parameters[coursetype]=" + URLEncoder.encode(course.getString("type"), "UTF-8") +
                                "&parameters[activity]=" + URLEncoder.encode(course.getString("typeA"), "UTF-8") +
                                "&moodlewsrestformat=" + JSON;
                        log.info("CALL WS create course : " + moodleUrl);
                        HttpClientHelper.webServiceMoodlePost(null, moodleUrl, vertx, moodleClient, responseMoodle -> {
                            if (responseMoodle.isRight()) {
                                log.info("SUCCESS creating course : ");
                                JsonObject courseCreatedInMoodle = responseMoodle.right().getValue().toJsonArray().getJsonObject(0);
                                log.info(courseCreatedInMoodle);
                                course.put("moodleid", courseCreatedInMoodle.getValue("courseid"))
                                        .put("userid", user.getUserId());

                                moduleSQLRequestService.createCourse(course, event -> {
                                    if (event.isRight()) {
                                        eventStore.createAndStoreEvent(Moodle.MoodleEvent.CREATE.name(), request);
                                        Renders.renderJson(request, event.right().getValue(), 200);
                                    } else {
                                        JsonObject error = (new JsonObject()).put("error", event.left().getValue());
                                        Renders.renderJson(request, error, 400);
                                    }
                                });
                            } else {
                                Utils.sendErrorRequest(request, "FAIL creating course : " + responseMoodle.left().getValue());
                            }
                        });
                    } catch (UnsupportedEncodingException error) {
                        Utils.sendErrorRequest(request, "fail to create course by sending the URL to the WS" + error);
                    }
                }
            });
        });
    }

    @Get("/user/courses")
    @ApiDoc("Get courses by user in moodle and sql")
    @ResourceFilter(AccessRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void getCoursesByUser(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                String idUser = user.getUserId();
                moduleSQLRequestService.getCoursesByUserInEnt(idUser, getSQLCoursesHandler(request, user, idUser));
            } else
                unauthorized(request, "User is not authorized to access courses : ");
        });
    }

    private Handler<Either<String, JsonArray>> getSQLCoursesHandler(HttpServerRequest request, UserInfos user, String idUser) {
        return eventSqlCourses -> {
            if (eventSqlCourses.isRight()) {
                final JsonArray sqlCourses = eventSqlCourses.right().getValue();
                JsonObject moodleClient = moodleMultiClient.getJsonObject(request.host());
                final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx, moodleClient);
                final String moodleUrl = createUrlMoodleGetCourses(idUser, request);
                final AtomicBoolean responseIsSent = new AtomicBoolean(false);
                Buffer wsResponse = new BufferImpl();
                log.info("CALL WS_GET_USERCOURSES : " + moodleUrl);
                final HttpClientRequest httpClientRequest = httpClient.getAbs(moodleUrl,
                        getHttpClientResponseHandler(request, user, idUser, sqlCourses, httpClient, moodleUrl, responseIsSent, wsResponse));
                httpClientRequest.headers().set("Content-Length", "0");
                httpClientRequest.exceptionHandler(eventClientRequest -> {
                    Utils.sendErrorRequest(request, "Typically an unresolved Address, a timeout about connection or response : " +
                            eventClientRequest.getMessage() +
                            eventClientRequest);
                    if (!responseIsSent.getAndSet(true)) {
                        renderError(request);
                        httpClient.close();
                    }
                }).end();
            } else {
                Utils.sendErrorRequest(request, "Get list courses in Ent Base failed : " +
                        eventSqlCourses.left().getValue());
            }
        };
    }

    private Handler<HttpClientResponse> getHttpClientResponseHandler(HttpServerRequest request, UserInfos user, String idUser,
                                                                     JsonArray sqlCourses, HttpClient httpClient, String moodleUrl,
                                                                     AtomicBoolean responseIsSent, Buffer wsResponse) {
        return responseMoodle -> {
            if (responseMoodle.statusCode() == 200) {
                log.info("SUCCESS WS_GET_USERCOURSES");
                responseMoodle.handler(wsResponse::appendBuffer);
                responseMoodle.endHandler(end -> {
                    JsonArray object = new JsonArray(wsResponse);
                    JsonArray coursesDirtyFromMoodle = object.getJsonObject(0).getJsonArray("enrolments");
                    JsonArray courses = Utils.removeDuplicateCourses(coursesDirtyFromMoodle);
                    List<String> sqlCourseId = sqlCourses.stream()
                            .map(obj -> (((JsonObject) obj).getValue("moodle_id")).toString()).collect(Collectors.toList());
                    for (int i = 0; i < courses.size(); i++) {
                        JsonObject course = courses.getJsonObject(i);
                        String summary = course.getString("summary", "").replaceAll("</p><p>", " ; ")
                                .replaceAll("<p>", "").replaceAll("</p>", "");
                        if(summary.endsWith("<br>"))
                            summary = summary.substring(0, summary.length() - 4);
                        course.put("summary",summary);
                        course.put("duplication", "non");
                        if (sqlCourseId.contains(course.getValue("courseid").toString())) {
                            int courseIndex = sqlCourseId.indexOf(course.getValue("courseid").toString());
                            JsonObject SQLCourse = sqlCourses.getJsonObject(courseIndex);
                            String idFolder = SQLCourse.getValue("folder_id").toString();
                            course.put("folderId", Integer.parseInt(idFolder));
                            if(!isNull(SQLCourse.getString("fullname"))){
                                course.put("levels", SQLCourse.getJsonArray("levels", new JsonArray()));
                                course.put("disciplines", SQLCourse.getJsonArray("disciplines", new JsonArray()));
                                course.put("plain_text", SQLCourse.getJsonArray("plain_text", new JsonArray()));
                                if(!SQLCourse.getString("fullname", "").equals(course.getString("fullname", "")) ||
                                        !SQLCourse.getString("imageurl", "").equals(course.getString("imageurl", "")) ||
                                        !SQLCourse.getString("summary", "").equals(course.getString("summary", "")) ){
                                    callMediacentreEventBusToUpdate(course, moodleEventBus, ebEvent -> {
                                        if (ebEvent.isRight()) {
                                            moduleSQLRequestService.updatePublicCourse(course, event -> {
                                                if (event.isRight()) {
                                                    log.info("Public Course updated in SQL Table " + event.right().getValue());
                                                } else {
                                                    log.error("Problem updating the public course SQL : " + event.left().getValue());
                                                }
                                            });
                                        } else {
                                            log.error("Problem with ElasticSearch updating : " + ebEvent.left().getValue());
                                        }
                                    });
                                }
                            }
                        } else {
                            course.put("moodleid", course.getValue("courseid", ""))
                                    .put("userid", idUser).put("folderId", 0);
                            String auteur = course.getJsonArray("auteur", new JsonArray()).getJsonObject(0).getString("entidnumber", "");
                            if (auteur != null && auteur.equals(user.getUserId())) {
                                moduleSQLRequestService.createCourse(course, eventResponseCreate -> {
                                    if (eventResponseCreate.isLeft()) {
                                        log.error("Error when created course in function get course " +
                                                eventResponseCreate.left().getValue());
                                    } else {
                                        log.info("" + "Course created " + eventResponseCreate.right().getValue());
                                    }
                                });
                            }
                        }
                    }
                    List<String> courseId = courses.stream().map(obj ->
                            (((JsonObject) obj).getValue("courseid")).toString()).collect(Collectors.toList());
                    moduleSQLRequestService.getUserCourseToDuplicate(idUser,
                            getDuplicateCoursesHandler(request, idUser, responseMoodle, courses, courseId));
                    if (!responseIsSent.getAndSet(true))
                        httpClient.close();
                });
            } else {
                responseMoodle.bodyHandler(eventResponse -> {
                    log.error("Returning body after GET CALL : " + moodleUrl + ", Returning body : " +
                            eventResponse.toString("UTF-8"));
                    if (!responseIsSent.getAndSet(true))
                        httpClient.close();
                });
                Utils.sendErrorRequest(request, "Fail to call get courses webservice : " +
                        responseMoodle.statusMessage());
            }
        };
    }

    private Handler<Either<String, JsonArray>> getDuplicateCoursesHandler(HttpServerRequest request, String idUser,
                                                                          HttpClientResponse responseMoodle, JsonArray courses,
                                                                          List<String> courseId) {
        return eventCoursesDuplication -> {
            if (eventCoursesDuplication.right().getValue().size() != 0) {
                JsonArray coursesInDuplication = eventCoursesDuplication.right().getValue();
                for (int i = 0; i < coursesInDuplication.size(); i++) {
                    JsonObject courseDuplicate = coursesInDuplication.getJsonObject(i);
                    int indexCourseDuplication = courseId.indexOf(courseDuplicate.getValue("id_course").toString());
                    if (indexCourseDuplication >= 0) {
                        JsonObject course = courses.getJsonObject(indexCourseDuplication);
                        courses.add(createCourseForSend(course, courseDuplicate));
                    }
                    else {
                        log.error("Fail to find course to duplicate : " + courseDuplicate);
                    }
                }
            }
            moduleSQLRequestService.getPreferences(idUser, getCoursePreferencesHandler(request, responseMoodle, courses));
        };
    }

    private Handler<Either<String, JsonArray>> getCoursePreferencesHandler(HttpServerRequest request,
                                                                           HttpClientResponse responseMoodle,
                                                                           JsonArray courses) {
        return eventPreference -> {
            if (eventPreference.isRight()) {
                JsonArray list = eventPreference.right().getValue();
                if (list.size() != 0) {
                    for (int i = 0; i < courses.size(); i++) {
                        JsonObject courseAddPreference = courses.getJsonObject(i);
                        for (int j = 0; j < list.size(); j++) {
                            JsonObject preferencesCourse = list.getJsonObject(j);
                            if (courseAddPreference.containsKey("courseid")) {
                                if (preferencesCourse.getValue("moodle_id").toString()
                                        .equals(courseAddPreference.getValue("courseid").toString())) {
                                    courseAddPreference.put("masked", preferencesCourse.getBoolean("masked"));
                                    courseAddPreference.put("favorites", preferencesCourse.getBoolean("favorites"));
                                }
                            }
                        }
                        if (!(courseAddPreference.containsKey("masked"))) {
                            courseAddPreference.put("masked", false);
                            courseAddPreference.put("favorites", false);
                        }
                    }
                } else {
                    for (int i = 0; i < courses.size(); i++) {
                        JsonObject courseWithoutPreference = courses.getJsonObject(i);
                        courseWithoutPreference.put("masked", false);
                        courseWithoutPreference.put("favorites", false);
                    }
                }
                JsonObject finalResponse = new JsonObject();
                finalResponse.put("allCourses", courses)
                        .put("idAuditeur", moodleConfig.getInteger("idAuditeur"))
                        .put("idEditingTeacher", moodleConfig.getInteger("idEditingTeacher"))
                        .put("idStudent", moodleConfig.getInteger("idStudent"))
                        .put("publicBankCategoryId", moodleConfig.getInteger("publicBankCategoryId"))
                        .put("deleteCategoryId", moodleConfig.getInteger("deleteCategoryId"));
                Renders.renderJson(request, finalResponse);
            } else {
                Utils.sendErrorRequest(request, "Fail to call get courses webservice : " +
                        responseMoodle.statusMessage());
            }
        };
    }

    private String createUrlMoodleGetCourses(String idUser, HttpServerRequest request) {
        try {
            JsonObject moodleClient = moodleMultiClient.getJsonObject(request.host());
            return "" +
                    (moodleClient.getString("address_moodle") +
                            moodleClient.getString("ws-path")) +
                    "?wstoken=" + moodleClient.getString("wsToken") +
                    "&wsfunction=" + WS_GET_USERCOURSES +
                    "&parameters[userid]=" + idUser +
                    "&moodlewsrestformat=" + JSON;
        } catch (Exception error) {
            log.error("Error in createUrlMoodleGetCourses" + error);
            throw error;
        }
    }

    private JsonObject createCourseForSend(JsonObject course, JsonObject courseDuplicate) {
        try {
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            return new JsonObject()
                    .put("fullname", "" +
                            course.getString("fullname") +
                            "_" +
                            LocalDateTime.now().getYear() +
                            "-" +
                            LocalDateTime.now().getMonthValue() +
                            "-" +
                            LocalDateTime.now().getDayOfMonth())
                    .put("summary", course.getString("summary"))
                    .put("auteur", course.getJsonArray("auteur"))
                    .put("type", course.getString("type"))
                    .put("course_type", course.getString("course_type"))
                    .put("imageurl", course.getString("imageurl"))
                    .put("date", Long.toString((timestamp.getTime() / 1000)))
                    .put("timemodified", timestamp.getTime() / 1000)
                    .put("duplication", courseDuplicate.getString("status"))
                    .put("originalCourseId", courseDuplicate.getInteger("id_course"))
                    .put("folderId", courseDuplicate.getInteger("id_folder"))
                    .put("role", course.getString("role"))
                    .put("courseid", courseDuplicate.getInteger("id"))
                    .put("categoryid", courseDuplicate.getInteger("category_id"));
        } catch (Exception error) {
            log.error("Error in createCourseForSend" + error);
            throw error;
        }
    }

    @Put("/courses/move")
    @ApiDoc("move a course")
    @SecuredAction(workflow_moveCourse)
    public void moveCourse(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "courses", courses ->
                UserUtils.getUserInfos(eb, request, user -> {
                    if (user != null) {
                        moduleSQLRequestService.checkIfCoursesInRelationTable(courses, checkEvent -> {
                            if (checkEvent.isRight()) {
                                if (courses.getValue("folderId").equals(0)) {
                                    moduleSQLRequestService.deleteCourseInRelationTable(courses, defaultResponseHandler(request));
                                } else {
                                    moduleSQLRequestService.updateCourseIdInRelationTable(courses, defaultResponseHandler(request));
                                }
                            } else {
                                moduleSQLRequestService.insertCourseInRelationTable(courses, defaultResponseHandler(request));
                            }
                        });
                    } else {
                        log.error("User not found in session.");
                        unauthorized(request);
                    }
                }));
    }


    @Delete("/course")
    @ApiDoc("Delete a course")
    @SecuredAction(workflow_deleteCourse)
    public void delete(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "courses", courses -> {
            try {
                JsonArray coursesIds = courses.getJsonArray("coursesId");
                StringBuilder idsDeletes = new StringBuilder();
                for (int i = 0; i < coursesIds.size(); i++) {
                    idsDeletes.append("&parameters[course][").append(i).append("][courseid]=").append(coursesIds.getValue(i));
                }
                JsonObject moodleClient = moodleMultiClient.getJsonObject(request.host());
                URI moodleDeleteUri = new URI(moodleClient.getString("address_moodle") + moodleClient.getString("ws-path"));
                if (moodleConfig.containsKey("deleteCategoryId")) {
                    final String moodleDeleteUrl = moodleDeleteUri +
                            "?wstoken=" + moodleClient.getString("wsToken") +
                            "&wsfunction=" + WS_DELETE_FUNCTION +
                            "&parameters[categoryid]=" + moodleConfig.getInteger("deleteCategoryId").toString() +
                            idsDeletes +
                            "&moodlewsrestformat=" + JSON;
                    HttpClientHelper.webServiceMoodlePost(null, moodleDeleteUrl, vertx, moodleClient, responseMoodle -> {
                        if (responseMoodle.isRight()) {
                            if (courses.getBoolean("categoryType")) {
                                callMediacentreEventBusToDelete(request, moodleEventBus, event -> {
                                    if (event.isRight()) {
                                        log.info("ElasticSearch course deletion is a success");
                                        request.response()
                                                .setStatusCode(200)
                                                .end();
                                    } else {
                                        log.error("Failed to delete elasticSearch course");
                                    }
                                });
                            } else {
                                moduleSQLRequestService.deleteCourse(courses, defaultResponseHandler(request));
                            }
                        } else {
                            Utils.sendErrorRequest(request, "Response to moodle error");
                            log.error("Post service failed" + responseMoodle.left().getValue());
                        }
                    });
                } else {
                    Utils.sendErrorRequest(request, "Url and category config is incorrect");
                }
            } catch (Exception error) {
                Utils.sendErrorRequest(request, "Error when create call moodle");
                log.error("Invalid moodle web service deleting course uri", error);
            }
        });
    }

    @Get("/course/:id")
    @ApiDoc("Redirect to Moodle")
    @ResourceFilter(AccessRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void redirectToMoodle(HttpServerRequest request) {
        String scope = request.params().contains("scope") ? request.getParam("scope") : "view";
        JsonObject moodleClient = moodleMultiClient.getJsonObject(request.host());
        redirect(request, moodleClient.getString("address_moodle"), "/course/" + scope + ".php?id=" +
                request.getParam("id") + "&notifyeditingon=1");
    }

    @Put("/course/preferences")
    @ApiDoc("set preferences")
    @ResourceFilter(AccessRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void setPreferences(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "coursePreferences", coursePreferences ->
                UserUtils.getUserInfos(eb, request, user -> {
                    if (user != null) {
                        coursePreferences.put("userId", user.getUserId());
                        moduleSQLRequestService.setPreferences(coursePreferences, defaultResponseHandler(request));
                    } else {
                        log.error("User not found in session.");
                        unauthorized(request);
                    }
                })
        );
    }
}
