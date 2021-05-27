package fr.openent.moodle.controllers;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.filters.canShareResourceFilter;
import fr.openent.moodle.helper.HttpClientHelper;
import fr.openent.moodle.service.getShareProcessingService;
import fr.openent.moodle.service.impl.DefaultGetShareProcessingService;
import fr.openent.moodle.service.impl.DefaultModuleSQLRequestService;
import fr.openent.moodle.service.impl.DefaultMoodleEventBus;
import fr.openent.moodle.service.impl.DefaultMoodleService;
import fr.openent.moodle.service.impl.DefaultPostShareProcessingService;
import fr.openent.moodle.service.moduleSQLRequestService;
import fr.openent.moodle.service.moodleEventBus;
import fr.openent.moodle.service.postShareProcessingService;
import fr.openent.moodle.service.moodleService;
import fr.openent.moodle.utils.Utils;
import fr.wseduc.rs.*;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.http.response.DefaultResponseHandler;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
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
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.notification.TimelineHelper;
import org.entcore.common.storage.Storage;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.http.RouteMatcher;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static fr.openent.moodle.Moodle.*;
import static fr.wseduc.webutils.http.response.DefaultResponseHandler.arrayResponseHandler;
import static java.util.Objects.isNull;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class MoodleController extends ControllerHelper {

    private final moduleSQLRequestService moduleSQLRequestService;
    private final moodleEventBus moodleEventBus;
    private final Storage storage;
    private final getShareProcessingService getShareProcessingService;
    private final postShareProcessingService postShareProcessingService;
    private final moodleService moodleService;

    private EventStore eventStore;

    private enum MoodleEvent {ACCESS}

    private final String userMail;

    public static final String baseWsMoodleUrl = (moodleConfig.getString("address_moodle") + moodleConfig.getString("ws-path"));

    private final TimelineHelper timelineHelper;


    @Override
    public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
                     Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);
        eventStore = EventStoreFactory.getFactory().getEventStore(Moodle.class.getSimpleName());
    }

    public MoodleController(final Storage storage, EventBus eb, TimelineHelper timelineHelper) {
        super();
        this.eb = eb;
        this.storage = storage;

        this.moduleSQLRequestService = new DefaultModuleSQLRequestService(Moodle.moodleSchema, "course");
        this.moodleEventBus = new DefaultMoodleEventBus(eb);
        this.getShareProcessingService = new DefaultGetShareProcessingService();
        this.postShareProcessingService = new DefaultPostShareProcessingService();
        this.moodleService = new DefaultMoodleService();

        //todo remove mail constant and add mail from zimbra, ent ...
        this.userMail = Moodle.moodleConfig.getString("userMail");

        this.timelineHelper = timelineHelper;
    }

    //Permissions
    private static final String
            resource_read = "moodle.read",
            resource_contrib = "moodle.contrib",
            resource_manager = "moodle.manager",

    workflow_createCourse = "moodle.createCourse",
            workflow_deleteCourse = "moodle.deleteCourse",
            workflow_moveCourse = "moodle.moveCourse",
            workflow_createFolder = "moodle.createFolder",
            workflow_deleteFolder = "moodle.deleteFolder",
            workflow_moveFolder = "moodle.moveFolder",
            workflow_rename = "moodle.rename",
            workflow_duplicate = "moodle.duplicate",
            workflow_publish = "moodle.publish",
            workflow_accessPublicCourse = "moodle.accessPublicCourse",
            workflow_view = "moodle.view";

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

    @Put("/folders/move")
    @ApiDoc("move a folder")
    @SecuredAction(workflow_moveFolder)
    public void moveFolder(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "folder", folders ->
                UserUtils.getUserInfos(eb, request, user -> {
                    if (user != null) {
                        moduleSQLRequestService.moveFolder(folders, defaultResponseHandler(request));
                    } else {
                        log.error("User not found in session.");
                        unauthorized(request);
                    }
                })
        );
    }

    @Delete("/folder")
    @ApiDoc("delete a folder")
    @SecuredAction(workflow_deleteFolder)
    public void deleteFolder(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "folder", folder ->
                UserUtils.getUserInfos(eb, request, user -> {
                    if (user != null) {
                        moduleSQLRequestService.deleteFolders(folder, defaultResponseHandler(request));
                    } else {
                        log.error("User not found in session.");
                        unauthorized(request);
                    }
                }));
    }

    @Post("/folder")
    @ApiDoc("create a folder")
    @SecuredAction(workflow_createFolder)
    public void createFolder(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "folder", folder ->
                UserUtils.getUserInfos(eb, request, user -> {
                    if (user != null) {
                        folder.put("userId", user.getUserId());
                        folder.put("structureId", user.getStructures().get(0));
                        moduleSQLRequestService.createFolder(folder, defaultResponseHandler(request));
                    } else {
                        log.error("User not found in session.");
                        unauthorized(request);
                    }
                }));
    }

    @Put("/folder/rename")
    @ApiDoc("rename a folder")
    @SecuredAction(workflow_rename)
    public void renameFolder(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "folder", folder ->
                moduleSQLRequestService.renameFolder(folder, defaultResponseHandler(request)));
    }

    @Get("/folder/countsFolders/:id")
    @ApiDoc("Get course in database by folder id")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getCountsItemInFolder(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                long id_folder = Long.parseLong(request.params().get("id"));
                moduleSQLRequestService.countItemInFolder(id_folder, user.getUserId(), DefaultResponseHandler.defaultResponseHandler(request));
            } else {
                log.error("User not found in session.");
                unauthorized(request);
            }
        });
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
                    final String service = (moodleConfig.getString("address_moodle") + moodleConfig.getString("ws-path"));

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
                                "?wstoken=" + moodleConfig.getString("wsToken") +
                                "&wsfunction=" + WS_CREATE_FUNCTION +
                                "&parameters[username]=" + URLEncoder.encode(user.getUserId(), "UTF-8") +
                                "&parameters[idnumber]=" + URLEncoder.encode(user.getUserId(), "UTF-8") +
                                "&parameters[email]=" + URLEncoder.encode(this.userMail, "UTF-8") +
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
                        HttpClientHelper.webServiceMoodlePost(null, moodleUrl, vertx, responseMoodle -> {
                            if (responseMoodle.isRight()) {
                                log.info("SUCCESS creating course : ");
                                JsonObject courseCreatedInMoodle = responseMoodle.right().getValue().toJsonArray().getJsonObject(0);
                                log.info(courseCreatedInMoodle);
                                course.put("moodleid", courseCreatedInMoodle.getValue("courseid"))
                                        .put("userid", user.getUserId());
                                moduleSQLRequestService.createCourse(course, defaultResponseHandler(request));
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

    @ApiDoc("public Get picture for moodle website")
    @Get("/files/:id")
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
    public void getInfoImg(final HttpServerRequest request) {
        try {
            moodleEventBus.getImage(request.getParam("id"), DefaultResponseHandler.defaultResponseHandler(request));
        } catch (Exception e) {
            log.error("Gail to get image workspace", e);
        }
    }

    @Get("/folders")
    @ApiDoc("Get folder in database")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getFolder(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                moduleSQLRequestService.getFoldersInEnt(user.getUserId(), arrayResponseHandler(request));
            } else {
                log.error("User not found in session.");
                unauthorized(request);
            }
        });
    }

    @Get("/user/courses")
    @ApiDoc("Get courses by user in moodle and sql")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
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
                final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx);
                final String moodleUrl = createUrlMoodleGetCourses(idUser);
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
                    List<String> sqlCourseId = sqlCourses.stream().map(obj -> (((JsonObject) obj).getValue("moodle_id")).toString()).collect(Collectors.toList());
                    for (int i = 0; i < courses.size(); i++) {
                        JsonObject course = courses.getJsonObject(i);
                        String summary = course.getString("summary").replaceAll("</p><p>", " ; ")
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
                                course.put("levels", SQLCourse.getJsonArray("levels"));
                                course.put("disciplines", SQLCourse.getJsonArray("disciplines"));
                                course.put("plain_text", SQLCourse.getJsonArray("plain_text"));
                                if(!SQLCourse.getString("fullname").equals(course.getString("fullname")) ||
                                        !SQLCourse.getString("imageurl").equals(course.getString("imageurl")) ||
                                        !SQLCourse.getString("summary").equals(course.getString("summary")) ){
                                    callMediacentreEventBusToUpdate(course, ebEvent -> {
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
                            course.put("moodleid", course.getValue("courseid"))
                                    .put("userid", idUser).put("folderId", 0);
                            String auteur = course.getJsonArray("auteur").getJsonObject(0).getString("entidnumber");
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
                        log.error("Fail to find course to duplicate : " + courseDuplicate.toString());
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
                                if (preferencesCourse.getValue("moodle_id").toString().equals(courseAddPreference.getValue("courseid").toString())) {
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

    private String createUrlMoodleGetCourses(String idUser) {
        try {
            return "" +
                    (moodleConfig.getString("address_moodle") +
                            moodleConfig.getString("ws-path")) +
                    "?wstoken=" + moodleConfig.getString("wsToken") +
                    "&wsfunction=" + WS_GET_USERCOURSES +
                    "&parameters[userid]=" + idUser +
                    "&moodlewsrestformat=" + JSON;
        } catch (Exception error) {
            log.error("Error in createUrlMoodleGetCourses" + error);
            throw error;
        }
    }

    private void createUpdateWSUrlCreateuser(UserInfos user, Handler<Either<String, Buffer>> handlerUpdateUser) throws UnsupportedEncodingException {
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
                URI moodleDeleteUri = new URI(moodleConfig.getString("address_moodle") + moodleConfig.getString("ws-path"));
                if (moodleConfig.containsKey("deleteCategoryId")) {
                    final String moodleDeleteUrl = moodleDeleteUri +
                            "?wstoken=" + moodleConfig.getString("wsToken") +
                            "&wsfunction=" + WS_DELETE_FUNCTION +
                            "&parameters[categoryid]=" + moodleConfig.getInteger("deleteCategoryId").toString() +
                            idsDeletes +
                            "&moodlewsrestformat=" + JSON;
                    HttpClientHelper.webServiceMoodlePost(null, moodleDeleteUrl, vertx, responseMoodle -> {
                        if (responseMoodle.isRight()) {
                            if (courses.getBoolean("categoryType")) {
                                callMediacentreEventBusToDelete(request, event -> {
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
    public void redirectToMoodle(HttpServerRequest request) {
        String scope = request.params().contains("scope") ? request.getParam("scope") : "view";
        redirect(request, moodleConfig.getString("address_moodle"), "/course/" + scope + ".php?id=" +
                request.getParam("id") + "&notifyeditingon=1");
    }

    @Get("/choices")
    @ApiDoc("get a choice")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
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
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
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

    @Put("/course/preferences")
    @ApiDoc("set preferences")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
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

    @Get("/share/json/:id")
    @ApiDoc("Lists rights for a given course.")
    @ResourceFilter(canShareResourceFilter.class)
    @SecuredAction(value = resource_read, type = ActionType.RESOURCE)
    public void share(final HttpServerRequest request) {
        final Handler<Either<String, JsonObject>> handler = defaultResponseHandler(request);
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                Future<JsonObject> getShareJsonInfosFuture = Future.future();
                Future<JsonArray> getUsersEnrolmentsFuture = Future.future();

                getShareJsonInfos(request, user, getShareJsonInfosFuture);
                getUsersEnrolmentsFromMoodle(request, getUsersEnrolmentsFuture);

                CompositeFuture.all(getShareJsonInfosFuture, getUsersEnrolmentsFuture).setHandler(event -> {
                    if (event.succeeded()) {
                        generateShareJson(request, handler, user, getShareJsonInfosFuture.result(), getUsersEnrolmentsFuture.result());
                    } else {
                        badRequest(request, event.cause().getMessage());
                    }
                });
            } else {
                log.error("User or group not found.");
                unauthorized(request);
            }
        });
    }

    /**
     * Get the shareJson model with a future
     *
     * @param request Http request
     * @param user    User infos
     * @param future  Future to get the shareJson model
     */
    private void getShareJsonInfos(HttpServerRequest request, UserInfos user, Future<JsonObject> future) {
        shareService.shareInfos(user.getUserId(), request.getParam("id"), I18n.acceptLanguage(request), request.params().get("search"), event -> {
            if (event.isRight()) {
                future.complete(event.right().getValue());
            } else {
                future.fail("Share infos not found");
            }
        });
    }

    /**
     * Get the Moodle users with the Web-Service from Moodle
     *
     * @param request Http request
     * @param future  Future to get the Moodle users
     */
    private void getUsersEnrolmentsFromMoodle(HttpServerRequest request, Future<JsonArray> future) {
        final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx);
        final AtomicBoolean responseIsSent = new AtomicBoolean(false);
        Buffer wsResponse = new BufferImpl();
        final String moodleUrl = (moodleConfig.getString("address_moodle") + moodleConfig.getString("ws-path")) +
                "?wstoken=" + moodleConfig.getString("wsToken") +
                "&wsfunction=" + WS_GET_SHARECOURSE +
                "&parameters[courseid]=" + request.getParam("id") +
                "&moodlewsrestformat=" + JSON;
        Handler<HttpClientResponse> getUsersEnrolmentsHandler = response -> {
            if (response.statusCode() == 200) {
                response.handler(wsResponse::appendBuffer);
                response.endHandler(end -> {
                    JsonArray finalGroups = new JsonArray(wsResponse);
                    future.complete(finalGroups);
                    if (!responseIsSent.getAndSet(true)) {
                        httpClient.close();
                    }
                });
            } else {
                log.error("Fail to call get share course right webservice" + response.statusMessage());
                response.bodyHandler(event -> {
                    log.error("Returning body after GET CALL : " + moodleUrl + ", Returning body : " + event.toString("UTF-8"));
                    future.fail(response.statusMessage());
                    if (!responseIsSent.getAndSet(true)) {
                        httpClient.close();
                    }
                });
            }
        };

        final HttpClientRequest httpClientRequest = httpClient.getAbs(moodleUrl, getUsersEnrolmentsHandler);
        httpClientRequest.headers().set("Content-Length", "0");
        //Typically an unresolved Address, a timeout about connection or response
        httpClientRequest.exceptionHandler(event -> {
            log.error(event.getMessage(), event);
            future.fail(event.getMessage());
            if (!responseIsSent.getAndSet(true)) {
                renderError(request);
                httpClient.close();
            }
        }).end();
    }

    /**
     * Creation and implementation of shareJson model
     *
     * @param request             Http request
     * @param handler             function handler returning data
     * @param user                UserInfos
     * @param shareJsonInfosFinal JsonObject to fill
     * @param usersEnrolments     JsonArray with Moodle users
     */
    private void generateShareJson(HttpServerRequest request, Handler<Either<String, JsonObject>> handler, UserInfos user, JsonObject shareJsonInfosFinal, JsonArray usersEnrolments) {
        JsonObject checkedInherited = new JsonObject();
        shareJsonInfosFinal.getJsonObject("users").put("checkedInherited", checkedInherited);
        JsonObject userEnrolmentsArray = usersEnrolments.getJsonObject(0).getJsonArray("enrolments").getJsonObject(0);
        JsonArray rightToAdd = new JsonArray().add(MOODLE_READ).add(MOODLE_CONTRIB).add(MOODLE_MANAGER);

        if (!usersEnrolments.isEmpty() && !shareJsonInfosFinal.isEmpty()) {
            getShareProcessingService.shareTreatmentForGroups(userEnrolmentsArray, shareJsonInfosFinal, request, rightToAdd, groupsTreatmentEvent -> {
                if (groupsTreatmentEvent.isRight()) {
                    log.info("Groups treatment OK");
                } else {
                    log.error("Groups treatment KO");
                }
            });

            getShareProcessingService.shareTreatmentForUsers(userEnrolmentsArray, shareJsonInfosFinal, user, rightToAdd, usersTreatmentEvent -> {
                if (usersTreatmentEvent.isRight()) {
                    log.info("Users treatment OK");
                } else {
                    log.error("Users treatment KO");
                }
            });

            handler.handle(new Either.Right<>(shareJsonInfosFinal));
        } else {
            log.error("User future or share infos future is empty");
            unauthorized(request);
        }
    }

    @Put("/contrib")
    @ApiDoc("Adds rights for a given course.")
    @ResourceFilter(canShareResourceFilter.class)
    @SecuredAction(value = resource_contrib, type = ActionType.RESOURCE)
    public void contrib(final HttpServerRequest request) {

    }

    @Put("/share/resource/:id")
    @ApiDoc("Adds rights for a given course.")
    @ResourceFilter(canShareResourceFilter.class)
    @SecuredAction(value = resource_manager, type = ActionType.RESOURCE)
    public void shareSubmit(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "share", shareCourseObject -> {
            JsonObject shareObjectToFill = new JsonObject();
            shareObjectToFill.put("courseid", request.params().entries().get(0).getValue());
            UserUtils.getUserInfos(eb, request, user -> {
                if (user != null) {
                    for (Object idGroup : shareCourseObject.copy().getJsonObject("groups").getMap().keySet().toArray()) {
                        if (idGroup.toString().startsWith("GR_")) {
                            shareCourseObject.getJsonObject("groups")
                                    .put(idGroup.toString().substring(3), shareCourseObject.getJsonObject("groups").getValue(idGroup.toString()));
                            shareCourseObject.getJsonObject("groups").remove(idGroup.toString());
                        }
                        if (idGroup.toString().startsWith("SB_")) {
                            shareCourseObject.getJsonObject("bookmarks")
                                    .put(idGroup.toString().substring(2), shareCourseObject.getJsonObject("groups").getValue(idGroup.toString()));
                            shareCourseObject.getJsonObject("groups").remove(idGroup.toString());
                        }
                    }

                    Map<String, Object> idUsers = shareCourseObject.getJsonObject("users").getMap();
                    Map<String, Object> idGroups = shareCourseObject.getJsonObject("groups").getMap();
                    Map<String, Object> idBookmarks = shareCourseObject.getJsonObject("bookmarks").getMap();

                    JsonObject IdFront = new JsonObject();
                    JsonObject keyShare = new JsonObject();

                    JsonArray usersIds = new JsonArray(new ArrayList<>(idUsers.keySet()));
                    postShareProcessingService.getResultUsers(shareCourseObject, usersIds, idUsers, IdFront, keyShare, event -> {
                        if (event.isRight()) {
                            log.info("Users treatment for Post OK");
                        } else {
                            log.info("Users treatment for Post KO");
                        }
                    });

                    JsonArray groupsIds = new JsonArray(new ArrayList<>(idGroups.keySet()));
                    postShareProcessingService.getResultGroups(shareCourseObject, groupsIds, idGroups, IdFront, keyShare, event -> {
                        if (event.isRight()) {
                            log.info("Groups treatment for Post OK");
                        } else {
                            log.info("Groups treatment for Post KO");
                        }
                    });

                    JsonArray bookmarksIds = new JsonArray(new ArrayList<>(idBookmarks.keySet()));
                    postShareProcessingService.getResultBookmarks(shareCourseObject, bookmarksIds, idBookmarks, IdFront, keyShare, event -> {
                        if (event.isRight()) {
                            log.info("Bookmarks treatment for Post OK");
                        } else {
                            log.info("Bookmarks treatment for Post KO");
                        }
                    });

                    Future<JsonArray> getUsersFuture = Future.future();
                    Future<JsonArray> getUsersInGroupsFuture = Future.future();
                    Future<JsonArray> getBookmarksFuture = Future.future();

                    usersIds.add(user.getUserId());
                    postShareProcessingService.getUsersFuture(usersIds, getUsersFuture);
                    postShareProcessingService.getUsersInGroupsFuture(groupsIds, getUsersInGroupsFuture);
                    postShareProcessingService.getUsersInBookmarksFuture(bookmarksIds, getBookmarksFuture);

                    Future<JsonArray> getTheAuditeurIdFuture = Future.future();
                    getUsersEnrolmentsFromMoodle(request, getTheAuditeurIdFuture);

                    final Map<String, Object> mapInfo = keyShare.getMap();
                    mapInfo.put(user.getUserId(), moodleConfig.getInteger("idEditingTeacher"));

                    CompositeFuture.all(getUsersFuture, getUsersInGroupsFuture, getBookmarksFuture, getTheAuditeurIdFuture).setHandler(event -> {
                        if (event.succeeded()) {
                            JsonArray usersFutureResult = getUsersFuture.result();
                            JsonArray groupsFutureResult = getUsersInGroupsFuture.result();
                            JsonArray bookmarksFutureResult = getBookmarksFuture.result();
                            JsonArray getTheAuditeurIdFutureResult = getTheAuditeurIdFuture.result().getJsonObject(0)
                                    .getJsonArray("enrolments").getJsonObject(0).getJsonArray("users");

                            JsonObject auditeur = new JsonObject();
                            for (int i = 0; i < getTheAuditeurIdFutureResult.size(); i++) {
                                if (getTheAuditeurIdFutureResult.getJsonObject(i).getInteger("role") == moodleConfig.getValue("idAuditeur")) {
                                    auditeur = getTheAuditeurIdFutureResult.getJsonObject(i);
                                    break;
                                }
                            }
                            if (auditeur.size() == 0) {
                                badRequest(request, "No auditor role found for course : " + shareObjectToFill.getValue("courseid"));
                                return;
                            }
                            if (usersFutureResult != null && !usersFutureResult.isEmpty()) {
                                shareObjectToFill.put("users", usersFutureResult);
                                for (Object userObject : usersFutureResult) {
                                    JsonObject userJson = ((JsonObject) userObject);
                                    if (userJson.getString("id").equals(user.getUserId()) &&
                                            userJson.getString("id").equals(auditeur.getString("id"))) {
                                        userJson.put("role", moodleConfig.getInteger("idAuditeur"));
                                    } else {
                                        userJson.put("role", mapInfo.get(userJson.getString("id")));
                                    }
                                }
                            }
                            if (groupsFutureResult != null && !groupsFutureResult.isEmpty()) {
                                shareObjectToFill.put("groups", groupsFutureResult);
                                for (Object groupObject : groupsFutureResult) {
                                    JsonObject groupJson = ((JsonObject) groupObject);
                                    groupJson.put("role", mapInfo.get(groupJson.getString("id").substring(3)));
                                    UserUtils.groupDisplayName(groupJson, I18n.acceptLanguage(request));
                                }
                            }
                            ArrayList<Future> listUsersFutures = new ArrayList<>();
                            List<Integer> listRankGroup = new ArrayList<>();
                            int i = 0;
                            if (bookmarksFutureResult != null && !bookmarksFutureResult.isEmpty()) {
                                postShareProcessingService.getUsersInBookmarksFutureLoop(shareObjectToFill, mapInfo,
                                        bookmarksFutureResult, listUsersFutures, listRankGroup, i);
                            }
                            if (listUsersFutures.size() > 0) {
                                CompositeFuture.all(listUsersFutures).setHandler(finished -> {
                                    if (finished.succeeded()) {
                                        postShareProcessingService.processUsersInBookmarksFutureResult(shareObjectToFill,
                                                listUsersFutures, listRankGroup);
                                        sendRightShare(shareObjectToFill, request);
                                    } else {
                                        badRequest(request, event.cause().getMessage());
                                    }
                                });
                            } else {
                                sendRightShare(shareObjectToFill, request);
                            }
                        } else {
                            badRequest(request, event.cause().getMessage());
                        }
                    });
                } else {
                    log.error("User not found.");
                    unauthorized(request);
                }
            });
        });
    }

    private void sendRightShare(JsonObject shareObjectToFill, HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            JsonArray zimbraEmail = new JsonArray();
            if (shareObjectToFill.getJsonArray("users") != null) {
                for (int i = 0; i < shareObjectToFill.getJsonArray("users").size(); i++) {
                    zimbraEmail.add(shareObjectToFill.getJsonArray("users").getJsonObject(i).getString("id"));
                }
            }
            if (shareObjectToFill.getJsonArray("groups") != null) {
                for (int i = 0; i < shareObjectToFill.getJsonArray("groups").size(); i++) {
                    for (int j = 0; j < shareObjectToFill.getJsonArray("groups").getJsonObject(i).getJsonArray("users").size(); j++) {
                        zimbraEmail.add(shareObjectToFill.getJsonArray("groups").getJsonObject(i).getJsonArray("users")
                                .getJsonObject(j).getString("id"));
                    }
                }
            }

            JsonArray users = shareObjectToFill.getJsonArray("users");
            if (users != null) {
                for (int i = 0; i < users.size(); i++) {
                    JsonObject manualUsers = users.getJsonObject(i);
                    manualUsers.put("email", this.userMail);
                }
            }

            JsonArray groupsUsers = shareObjectToFill.getJsonArray("groups");
            if (groupsUsers != null) {
                for (int i = 0; i < groupsUsers.size(); i++) {
                    JsonArray usersInGroup = groupsUsers.getJsonObject(i).getJsonArray("users");
                    if (usersInGroup != null) {
                        for (int j = 0; j < usersInGroup.size(); j++) {
                            JsonObject userInGroup = usersInGroup.getJsonObject(j);
                            userInGroup.put("email", this.userMail);
                        }
                    }
                }
            }

            JsonObject shareSend = new JsonObject();
            shareSend.put("parameters", shareObjectToFill)
                    .put("wstoken", moodleConfig.getString("wsToken"))
                    .put("wsfunction", WS_CREATE_SHARECOURSE)
                    .put("moodlewsrestformat", JSON);
            URI moodleUri = null;
            try {
                final String service = (moodleConfig.getString("address_moodle") + moodleConfig.getString("ws-path"));
                final String urlSeparator = "";
                moodleUri = new URI(service + urlSeparator);
            } catch (URISyntaxException e) {
                log.error("Invalid moodle web service sending right share uri", e);
            }
            if (moodleUri != null) {
                final String moodleUrl = moodleUri.toString();
                log.info("CALL WS_CREATE_SHARECOURSE");
                try {
                    HttpClientHelper.webServiceMoodlePost(shareSend, moodleUrl, vertx, event -> {
                        if (event.isRight()) {
                            log.info("SUCCESS WS_CREATE_SHARECOURSE");
                            enrolNotify(event.right().getValue().toJsonArray().getJsonObject(0).getJsonArray("response").getJsonObject(0), user);
                            request.response()
                                    .setStatusCode(200)
                                    .end();
                        } else {
                            log.error("FAIL WS_CREATE_SHARECOURSE" + event.left().getValue());
                            unauthorized(request);
                        }
                    });
                } catch (UnsupportedEncodingException e) {
                    log.error("UnsupportedEncodingException",e);
                    renderError(request);
                }
            }
        });
    }

    private void enrolNotify( JsonObject response, UserInfos user) {
        int courseId = response.getInteger("courseid");
        JsonArray usersEnrolled = response.getJsonArray("users");
        JsonArray groupsEnrolled = response.getJsonArray("groups");

        String timelineSender = user.getUsername() != null ? user.getUsername() : null;
        List<String> recipients = new ArrayList<>();
        final JsonObject params = new JsonObject()
                .put("courseUri", moodleConfig.getString("address_moodle") + "/course/view.php?id=" + courseId)
                .put("disableAntiFlood", true);
        params.put("username", timelineSender).put("uri", "/userbook/annuaire#" + user.getUserId());

        for (Object userEnrolled : usersEnrolled) {
            JsonObject userJson = (JsonObject) userEnrolled;
            if (!userJson.getString("result").contains("already") && !userJson.getValue("idnumber").toString().equals("0")) {
                recipients.add(userJson.getString("idnumber"));
            }
        }
        for (Object group : groupsEnrolled) {
            JsonObject groupJson = (JsonObject) group;
            if (!groupJson.getString("result").contains("already") && !groupJson.getValue("idnumber").toString().equals("0")) {
                usersEnrolled = groupJson.getJsonArray("users");
                for (Object userEnrolled : usersEnrolled) {
                    JsonObject userJson = (JsonObject) userEnrolled;
                    if (!userJson.getString("result").contains("already") && !userJson.getValue("idnumber").toString().equals("0")) {
                        recipients.add(userJson.getString("idnumber"));
                    }
                }
            }
        }

        params.put("pushNotif", new JsonObject().put("title", "push.notif.moodle").put("body", ""));

        if(!recipients.isEmpty()) {
            timelineHelper.notifyTimeline(null, "moodle.enrol_notification", user, recipients, params);
        }
    }

    @Get("/course/share/BP/:id")
    @ApiDoc("Enroll the user on the course as a guest")
    @SecuredAction(workflow_accessPublicCourse)
    public void accessPublicCourse(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {

            int courseId;
            try {
                courseId = Integer.parseInt(request.getParam("id"));
            } catch (NumberFormatException n){
                Renders.badRequest(request);
                return;
            }

            moodleService.getAuditeur(courseId, vertx, getAuditeurEvent -> {
                if (getAuditeurEvent.isRight()) {
                    JsonArray usersId = new JsonArray();

                    usersId.add(getAuditeurEvent.right().getValue().getJsonObject(0).getString("id"))
                            .add(user.getUserId());
                    if (!getAuditeurEvent.right().getValue().getJsonObject(0).getString("id").equals(user.getUserId())) {
                        moodleService.registerUserInPublicCourse(usersId, courseId, vertx, registerEvent -> {
                            if (registerEvent.isRight()) {
                                redirect(request, moodleConfig.getString("address_moodle"), "/course/view.php?id=" +
                                        request.getParam("id") + "&notifyeditingon=1");
                            } else {
                                log.error("FAIL WS_CREATE_SHARECOURSE" + registerEvent.left().getValue());
                                unauthorized(request);
                            }
                        });
                    } else {
                        redirect(request, moodleConfig.getString("address_moodle"), "/course/view.php?id=" +
                                request.getParam("id") + "&notifyeditingon=1");
                    }
                } else {
                    Renders.badRequest(request);
                }
            });
        });
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

    @Post("/course/duplicate/BP/:id")
    @ApiDoc("Duplicate a BP course")
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
    public void getMoodleResponse(HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "duplicateResponse", duplicateResponse ->
                UserUtils.getUserInfos(eb, request, user -> {
                    switch (duplicateResponse.getString("status")) {
                        case "pending":
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
                            break;
                        case "finished":
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
                                                        if (!infoDuplicateSQL.getJsonObject("info").getInteger("category_id").equals(moodleConfig.getInteger("publicBankCategoryId"))) {
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
                                                                                callMediacentreEventBusForPublish(id, mediacentreEvent ->
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
                            break;
                        case "busy":
                            log.info("A duplication is already in progress");
                            break;
                        case "error":
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
                            break;
                        default:
                            log.error("Failed to read the Moodle response");
                            unauthorized(request);
                    }
                })
        );
    }

    @Post("/metadata/update")
    @ApiDoc("Update public course metadata")
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
            newMetadata.put("key_words", plainTextArray);
            callMediacentreEventBusToUpdateMetadata(updateMetadata, ebEvent -> {
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

    public void callMediacentreEventBusForPublish(JsonArray id, final Handler<Either<String, JsonObject>> handler) {
        moodleEventBus.publishInMediacentre(id, handler);
    }

    public void callMediacentreEventBusToDelete(HttpServerRequest request, final Handler<Either<String, JsonObject>> handler) {
        RequestUtils.bodyToJson(request, deleteEvent -> {
            moodleEventBus.deleteResourceInMediacentre(deleteEvent, handler);
        });
    }

    public void callMediacentreEventBusToUpdateMetadata(JsonObject updateMetadata, final Handler<Either<String, JsonObject>> handler) {
        moodleEventBus.updateResourceInMediacentre(updateMetadata, handler);
    }

    public void callMediacentreEventBusToUpdate(JsonObject updateCourse, final Handler<Either<String, JsonObject>> handler) {
        moodleEventBus.updateInMediacentre(updateCourse, handler);
    }
}
