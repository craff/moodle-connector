package fr.openent.moodle.controllers;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.filters.canShareResourceFilter;
import fr.openent.moodle.helper.HttpClientHelper;
import fr.openent.moodle.service.getShareProcessingService;
import fr.openent.moodle.service.impl.DefaultGetShareProcessingService;
import fr.openent.moodle.service.impl.DefaultModuleSQLRequestService;
import fr.openent.moodle.service.impl.DefaultMoodleEventBus;
import fr.openent.moodle.service.impl.DefaultPostShareProcessingService;
import fr.openent.moodle.service.moduleSQLRequestService;
import fr.openent.moodle.service.moodleEventBus;
import fr.openent.moodle.service.postShareProcessingService;
import fr.openent.moodle.utils.Utils;
import fr.wseduc.rs.*;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.http.response.DefaultResponseHandler;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
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
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.storage.Storage;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;

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
import static fr.openent.moodle.helper.HttpClientHelper.webServiceMoodlePost;
import static fr.wseduc.webutils.http.response.DefaultResponseHandler.arrayResponseHandler;
import static java.util.Objects.isNull;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class MoodleController extends ControllerHelper {

    private final moduleSQLRequestService moduleSQLRequestService;
    private final moodleEventBus moodleEventBus;
    private final Storage storage;
    private final getShareProcessingService getShareProcessingService;
    private final postShareProcessingService postShareProcessingService;

    public MoodleController(final Storage storage, EventBus eb) {
        super();
        this.eb = eb;
        this.storage = storage;

        this.moduleSQLRequestService = new DefaultModuleSQLRequestService(Moodle.moodleSchema, "course");
        this.moodleEventBus = new DefaultMoodleEventBus(Moodle.moodleSchema, "course", eb);
        this.getShareProcessingService = new DefaultGetShareProcessingService();
        this.postShareProcessingService = new DefaultPostShareProcessingService();
    }


    //Permissions
    private static final String
            resource_read = "moodle.read",
            resource_contrib = "moodle.contrib",
            resource_manager = "moodle.manager",
            workflow_create = "moodle.create",
            workflow_delete = "moodle.delete",
            workflow_view = "moodle.view",
            workflow_duplicate = "moodle.duplicate";

    /**
     * Displays the home view.
     *
     * @param request Client request
     */
    @Get("")
    @SecuredAction(workflow_view)
    public void view(HttpServerRequest request) {
        renderView(request);
    }

    @Put("/folders/move")
    @ApiDoc("move a folder")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void moveFolder(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "folder", folders ->
                UserUtils.getUserInfos(eb, request, user -> {
                    if (user != null) {
                        moduleSQLRequestService.moveFolder(folders, defaultResponseHandler(request));
                    } else {
                        log.error("User not found in session.");
                        unauthorized(request);
                    }
                }));
    }

    @Delete("/folder")
    @ApiDoc("delete a folder")
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
    @SecuredAction(workflow_create)
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
                final AtomicBoolean responseIsSent = new AtomicBoolean(false);
                try {
                    GregorianCalendar calendar = new GregorianCalendar();
                    String uniqueID = UUID.randomUUID().toString();
                    course.put("shortname", calendar.toZonedDateTime().toString().substring(0, 7) +
                            user.getFirstName().substring(0, 1) + user.getLastName().substring(0, 3) +
                            course.getString("fullname").substring(0, 4) + uniqueID);
                    final String service = (config.getString("address_moodle") + config.getString("ws-path"));
                    final String urlSeparator = service.endsWith("") ? "" : "/";
                    moodleUri = new URI(service + urlSeparator);
                } catch (URISyntaxException error) {
                    Utils.sendErrorRequest(request,"Invalid moodle web service creating a course uri" + error);
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
                        //todo remove mail constant and add mail from zimbra, ent ...
                        String userMail = "moodleNotif@lyceeconnecte.fr";
                        final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx, config);
                        final String moodleUrl = moodleUri.toString() +
                                "?wstoken=" + config.getString("wsToken") +
                                "&wsfunction=" + WS_CREATE_FUNCTION +
                                "&parameters[username]=" + URLEncoder.encode(user.getUserId(), "UTF-8") +
                                "&parameters[idnumber]=" + URLEncoder.encode(user.getUserId(), "UTF-8") +
                                "&parameters[email]=" + URLEncoder.encode(userMail, "UTF-8") +
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
                        webServiceMoodlePost(null, moodleUrl, httpClient, responseIsSent, responseMoodle -> {
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
                        }, true);
                    } catch (UnsupportedEncodingException error) {
                        Utils.sendErrorRequest(request,"fail to create course by sending the URL to the WS" + error);
                    }
                }
            });
        });
    }

    @ApiDoc("public Get picture for moodle website")
    @Get("/files/:id")
    public void getFile(HttpServerRequest request) {
        String idImage = request.getParam("id").substring(0, request.getParam("id").lastIndexOf('.'));
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
    @Get("/info/image/:id")
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
    public void listFoldersAndShared(final HttpServerRequest request) {
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
        try {
            UserUtils.getUserInfos(eb, request, user -> {
                try {
                    if (user != null) {
                        String idUser = user.getUserId();
                        moduleSQLRequestService.getCoursesByUserInEnt(idUser, eventSqlCourses -> {
                            if (eventSqlCourses.isRight()) {
                                final JsonArray sqlCourses = eventSqlCourses.right().getValue();
                                final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx, config);
                                final String moodleUrl = createUrlMoodleGetCourses(idUser);
                                final AtomicBoolean responseIsSent = new AtomicBoolean(false);
                                Buffer wsResponse = new BufferImpl();
                                log.info("CALL WS_GET_USERCOURSES : " + moodleUrl);
                                final HttpClientRequest httpClientRequest = httpClient.getAbs(moodleUrl, responseMoodle -> {
                                    if (responseMoodle.statusCode() == 200) {
                                        log.info("SUCCESS WS_GET_USERCOURSES");
                                        responseMoodle.handler(wsResponse::appendBuffer);
                                        responseMoodle.endHandler(end -> {
                                            try {
                                                JsonArray object = new JsonArray(wsResponse);
                                                JsonArray coursesDirtyFromMoodle = object.getJsonObject(0).getJsonArray("enrolments");
                                                JsonArray courses = Utils.removeDuplicateCourses(coursesDirtyFromMoodle);
                                                List<String> sqlCourseId = sqlCourses.stream().map(obj -> (((JsonObject) obj).getValue("moodle_id")).toString()).collect(Collectors.toList());

                                                for (int i = 0; i < courses.size(); i++) {
                                                    JsonObject course = courses.getJsonObject(i);
                                                    course.put("duplication", "non");

                                                    if (sqlCourseId.contains(course.getValue("courseid").toString())) {
                                                        int courseIndex = sqlCourseId.indexOf(course.getValue("courseid").toString());
                                                        String idFolder = sqlCourses.getJsonObject(courseIndex).getValue("folder_id").toString();
                                                        course.put("folderid", Integer.parseInt(idFolder));
                                                    } else {
                                                        course.put("moodleid", course.getValue("courseid"))
                                                                .put("userid", idUser).put("folderid", 0);
                                                        String auteur = course.getJsonArray("auteur").getJsonObject(0).getString("entidnumber");
                                                        if (auteur != null && auteur.equals(user.getUserId())) {
                                                            moduleSQLRequestService.createCourse(course, eventResponseCreate -> {
                                                                if (eventResponseCreate.isLeft()) {
                                                                    log.error("Error when created course in function get course " +
                                                                            eventResponseCreate.left().getValue());
                                                                } else {
                                                                    log.info("" +
                                                                            "Course created " +
                                                                            eventResponseCreate.right().getValue());
                                                                }
                                                            });
                                                        }
                                                    }
                                                }
                                                List<String> courseId = courses.stream().map(obj -> (((JsonObject) obj).getValue("courseid")).toString()).collect(Collectors.toList());
                                                moduleSQLRequestService.getCourseToDuplicate(idUser, eventCoursesDuplication -> {
                                                    try {
                                                        if (eventCoursesDuplication.right().getValue().size() != 0) {
                                                            JsonArray coursesInDuplication = eventCoursesDuplication.right().getValue();
                                                            for (int i = 0; i < coursesInDuplication.size(); i++) {
                                                                JsonObject courseDuplicate = coursesInDuplication.getJsonObject(i);
                                                                int indexCourseDuplication = courseId.indexOf(courseDuplicate.getValue("id_course").toString());
                                                                JsonObject course = courses.getJsonObject(indexCourseDuplication);
                                                                courses.add(createCourseForSend(course, courseDuplicate));
                                                            }
                                                        }
                                                        moduleSQLRequestService.getPreferences(idUser, eventPreference -> {
                                                            try {
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
                                                                    finalResponse.put("allCourses", courses);
                                                                    finalResponse.put("idAuditeur", moodleConfig.getInteger("idAuditeur"));
                                                                            finalResponse.put("idEditingTeacher", moodleConfig.getInteger("idEditingTeacher"));
                                                                            finalResponse.put("idStudent", moodleConfig.getInteger("idStudent"));
                                                                            Renders.renderJson(request, finalResponse);
                                                                        } else {
                                                                            Utils.sendErrorRequest(request,
                                                                                    "Fail to call get courses webservice : " +
                                                                                            responseMoodle.statusMessage());
                                                                        }
                                                            } catch (Exception error) {
                                                                Utils.sendErrorRequest(request, "Error in get preference in get courses by user : " +
                                                                        error.toString());
                                                            }
                                                        });
                                                    } catch (Exception error) {
                                                        Utils.sendErrorRequest(request, "Error in get duplication in get courses by user : " +
                                                                error.toString());
                                                    }
                                                });
                                                if (!responseIsSent.getAndSet(true)) {
                                                    httpClient.close();
                                                }
                                            } catch (Exception error) {
                                                Utils.sendErrorRequest(request, "Error in get courses by user : " +
                                                        error.toString());
                                            }
                                                }
                                        );
                                    } else {
                                        responseMoodle.bodyHandler(eventResponse -> {
                                            log.error("Returning body after GET CALL : " +
                                                    moodleUrl +
                                                    ", Returning body : " +
                                                    eventResponse.toString("UTF-8"));
                                            if (!responseIsSent.getAndSet(true)) {
                                                httpClient.close();
                                            }
                                        });
                                        Utils.sendErrorRequest(request,
                                                "Fail to call get courses webservice : " +
                                                        responseMoodle.statusMessage());
                                    }
                                });
                                httpClientRequest.headers().set("Content-Length", "0");
                                httpClientRequest.exceptionHandler(eventClientRequest -> {
                                    Utils.sendErrorRequest(request,
                                            "Typically an unresolved Address, a timeout about connection or response : " +
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
                        });
                    } else {
                        unauthorized(request, "User is not authorized to access courses : ");
                    }
                } catch (Exception error) {
                    Utils.sendErrorRequest(request, "Error in get courses by user after get user : " + error);
                }
            });
        } catch (Exception error) {
            Utils.sendErrorRequest(request, "Error in get courses by user : " + error);
        }
    }

    private final String createUrlMoodleGetCourses(String idUser) {
        try {
            return "" +
                    (config.getString("address_moodle") +
                            config.getString("ws-path")) +
                    "?wstoken=" + config.getString("wsToken") +
                    "&wsfunction=" + WS_GET_USERCOURSES +
                    "&parameters[userid]=" + idUser +
                    "&moodlewsrestformat=" + JSON;
        } catch (Exception error) {
            log.error("Error in createUrlMoodleGetCourses" + error);
            throw error;
        }
    }

    private final JsonObject createCourseForSend(JsonObject course, JsonObject courseDuplicate) {
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
                    .put("folderid", courseDuplicate.getInteger("id_folder"))
                    .put("role", course.getString("role"))
                    .put("courseid", courseDuplicate.getInteger("id"));
        } catch (Exception error) {
            log.error("Error in createCourseForsent" + error);
            throw error;
        }
    }

    @Put("/courses/move")
    @ApiDoc("move a course")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
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
    @SecuredAction(workflow_delete)
    public void delete(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "courses", courses -> {
            try {
                JsonArray coursesIds = courses.getJsonArray("coursesId");
                String idsDeletes = "";
                for (int i = 0; i < coursesIds.size(); i++) {
                    idsDeletes += "&parameters[course][" + i + "][courseid]=" + coursesIds.getValue(i);
                }
                final String service = (config.getString("address_moodle") + config.getString("ws-path"));
                final String urlSeparator = service.endsWith("") ? "" : "/";
                URI moodleDeleteUri = new URI(service + urlSeparator);
                if (moodleDeleteUri != null && config.containsKey("deleteCategoryId")) {
                    final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx, config);
                    final String moodleDeleteUrl = moodleDeleteUri.toString() +
                            "?wstoken=" + config.getString("wsToken") +
                            "&wsfunction=" + WS_DELETE_FUNCTION +
                            "&parameters[categoryid]=" + config.getInteger("deleteCategoryId").toString() +
                            idsDeletes +
                            "&moodlewsrestformat=" + JSON;
                    webServiceMoodlePost(null, moodleDeleteUrl, httpClient, new AtomicBoolean(false), responseMoodle -> {
                        if (responseMoodle.isRight()) {
                            moduleSQLRequestService.deleteCourse(courses, defaultResponseHandler(request));
                        } else {
                            Utils.sendErrorRequest(request, "Response to moodle error");
                            log.error("Post service failed" + responseMoodle.left().getValue());
                        }
                    }, true);
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
        redirect(request, config.getString("address_moodle"), "/course/" + scope + ".php?id=" +
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
        final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx, config);
        final AtomicBoolean responseIsSent = new AtomicBoolean(false);
        Buffer wsResponse = new BufferImpl();
        final String moodleUrl = (config.getString("address_moodle") + config.getString("ws-path")) +
                "?wstoken=" + config.getString("wsToken") +
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
                log.error("fail to call get share course right webservice" + response.statusMessage());
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
                        if (idGroup.toString().substring(0, 3).equals("GR_")) {
                            shareCourseObject.getJsonObject("groups").put(idGroup.toString().substring(3), shareCourseObject.getJsonObject("groups").getValue(idGroup.toString()));
                            shareCourseObject.getJsonObject("groups").remove(idGroup.toString());
                        }
                        if (idGroup.toString().substring(0, 3).equals("SB_")) {
                            shareCourseObject.getJsonObject("bookmarks").put(idGroup.toString().substring(2), shareCourseObject.getJsonObject("groups").getValue(idGroup.toString()));
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
                    mapInfo.put(user.getUserId(), config.getInteger("idEditingTeacher"));

                    CompositeFuture.all(getUsersFuture, getUsersInGroupsFuture, getBookmarksFuture, getTheAuditeurIdFuture).setHandler(event -> {
                        if (event.succeeded()) {
                            JsonArray usersFutureResult = getUsersFuture.result();
                            JsonArray groupsFutureResult = getUsersInGroupsFuture.result();
                            JsonArray bookmarksFutureResult = getBookmarksFuture.result();
                            JsonArray getTheAuditeurIdFutureResult = getTheAuditeurIdFuture.result().getJsonObject(0).getJsonArray("enrolments").getJsonObject(0).getJsonArray("users");

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
                                    if (userJson.getString("id").equals(user.getUserId()) && userJson.getString("id").equals(auditeur.getString("id"))) {
                                        userJson.put("role", config.getInteger("idAuditeur"));
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
                                postShareProcessingService.getUsersInBookmarksFutureLoop(shareObjectToFill, mapInfo, bookmarksFutureResult, listUsersFutures, listRankGroup, i);
                            }
                            if (listUsersFutures.size() > 0) {
                                CompositeFuture.all(listUsersFutures).setHandler(finished -> {
                                    if (finished.succeeded()) {
                                        postShareProcessingService.processUsersInBookmarksFutureResult(shareObjectToFill, listUsersFutures, listRankGroup);
                                        MoodleController.this.sendRightShare(shareObjectToFill, request);
                                    } else {
                                        badRequest(request, event.cause().getMessage());
                                    }
                                });
                            } else {
                                MoodleController.this.sendRightShare(shareObjectToFill, request);
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
                        zimbraEmail.add(shareObjectToFill.getJsonArray("groups").getJsonObject(i).getJsonArray("users").getJsonObject(j).getString("id"));
                    }
                }
            }
            log.info("JSON PARTAGE : " + shareObjectToFill.toString());

            /*log.info("CALL getZimbraEmail : ");
                moodleEventBus.getZimbraEmail(zimbraEmail, event -> {

                log.info("END getZimbraEmail : ");
                JsonObject zimbraResult = event.right().getValue();
                ArrayList zimbraArray = new ArrayList(zimbraResult.getMap().keySet());
                JsonObject zimbraObject = new JsonObject();
                for (int i = 0; i < zimbraArray.size(); i++) {
                    zimbraObject.put(zimbraArray.get(i).toString(), zimbraResult.getJsonObject(zimbraArray.get(i)
                            .toString()).getString("email"));
                }
                Map<String, Object> zimbraMap = (zimbraObject.getMap());
                JsonArray users = share.getJsonArray("users");
                if(users != null) {
                    for(int i = 0; i < users.size(); i++) {
                        JsonObject user1 = users.getJsonObject(i);
                        String idUser = user1.getString("id");
                        user1.put("email", zimbraMap.get(idUser));
                    }
                }
            */
            JsonArray users = shareObjectToFill.getJsonArray("users");
            if (users != null) {
                for (int i = 0; i < users.size(); i++) {
                    JsonObject manualUsers = users.getJsonObject(i);
                    manualUsers.put("email", "moodleNotif@lyceeconnecte.fr");
                }
            }

            JsonArray groupsUsers = shareObjectToFill.getJsonArray("groups");
            if (groupsUsers != null) {
                for (int i = 0; i < groupsUsers.size(); i++) {
                    JsonArray usersInGroup = groupsUsers.getJsonObject(i).getJsonArray("users");
                    if (usersInGroup != null) {
                        for (int j = 0; j < usersInGroup.size(); j++) {
                            JsonObject userInGroup = usersInGroup.getJsonObject(j);
                            userInGroup.put("email", "moodleNotif@lyceeconnecte.fr");
                        }
                    }
                }
            }

            log.info("JSON PARTAGE WITH MAIL : " + shareObjectToFill.toString());

            JsonObject shareSend = new JsonObject();
            shareSend.put("parameters", shareObjectToFill)
                    .put("wstoken", config.getString("wsToken"))
                    .put("wsfunction", WS_CREATE_SHARECOURSE)
                    .put("moodlewsrestformat", JSON);
            final AtomicBoolean responseIsSent = new AtomicBoolean(false);
            URI moodleUri = null;
            try {
                final String service = (config.getString("address_moodle") + config.getString("ws-path"));
                final String urlSeparator = service.endsWith("") ? "" : "/";
                moodleUri = new URI(service + urlSeparator);
            } catch (URISyntaxException e) {
                log.error("Invalid moodle web service sending right share uri", e);
            }
            if (moodleUri != null) {
                final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx, config);
                final String moodleUrl = moodleUri.toString();
                log.info("CALL WS_CREATE_SHARECOURSE");
                webServiceMoodlePost(shareSend, moodleUrl, httpClient, responseIsSent, event -> {
                    if (event.isRight()) {
                        log.info("SUCCESS WS_CREATE_SHARECOURSE");
                        request.response()
                                .setStatusCode(200)
                                .end();
                    } else {
                        log.error("FAIL WS_CREATE_SHARECOURSE" + event.left().getValue());
                        unauthorized(request);
                    }
                }, true);
            }
            //});
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
                    courseToDuplicate.put("folderid", duplicateCourse.getInteger("folderId"));
                    courseToDuplicate.put("status", WAITING);
                    courseToDuplicate.put("userId", user.getUserId());
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

    @Get("/duplicateCourses")
    @ApiDoc("Get duplicate courses")
    @SecuredAction(workflow_duplicate)
    public void getDuplicateCourses(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                moduleSQLRequestService.deleteFinishedCoursesDuplicate(event -> {
                    if (event.isRight()) {
                        moduleSQLRequestService.getCourseToDuplicate(user.getUserId(), arrayResponseHandler(request));
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
                moduleSQLRequestService.updateStatusCourseToDuplicate(status, id, 1, event -> {
                    if (event.isRight()) {
                        moduleSQLRequestService.deleteFinishedCoursesDuplicate(event1 -> {
                            if (event1.isRight()) {
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
                                    Integer.parseInt(duplicateResponse.getString("ident")), 1, event -> {
                                        if (event.isRight()) {
                                            moduleSQLRequestService.getCourseIdToDuplicate(FINISHED, event1 -> {
                                                JsonObject createCourseDuplicate = new JsonObject();
                                                createCourseDuplicate.put("userid", event1.right().getValue().getJsonObject(0).getString("id_users"));
                                                createCourseDuplicate.put("folderid", event1.right().getValue().getJsonObject(0).getInteger("id_folder"));
                                                createCourseDuplicate.put("moodleid", Integer.parseInt(duplicateResponse.getString("courseid")));
                                                moduleSQLRequestService.createCourse(createCourseDuplicate, event11 -> {
                                                    if (event11.isRight()) {
                                                        moduleSQLRequestService.deleteFinishedCoursesDuplicate(event111 -> {
                                                            if (event111.isRight()) {
                                                                request.response()
                                                                        .setStatusCode(200)
                                                                        .end();
                                                            } else {
                                                                log.error("Problem to delete finished duplicate courses !");
                                                                unauthorized(request);
                                                            }
                                                        });
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
                            log.debug("A duplication is already in progress");
                            break;
                        case "error":
                            moduleSQLRequestService.getCourseToDuplicate(duplicateResponse.getString("userid"), event -> {
                                if (event.isRight()) {
                                    if (event.right().getValue().getInteger(6) == 3) {
                                        moduleSQLRequestService.deleteFinishedCoursesDuplicate(event12 -> {
                                            if (event12.isRight()) {
                                                request.response()
                                                        .setStatusCode(200)
                                                        .end();
                                            } else {
                                                log.error("Problem to delete the duplicate course in database !");
                                                unauthorized(request);
                                            }
                                        });
                                    } else if (event.right().getValue().getInteger(6) < 3) {
                                        log.error("Duplication web-service failed !");
                                        final String status = WAITING;
                                        Integer id = duplicateResponse.getInteger("id");
                                        Integer nbrTentatives = duplicateResponse.getInteger("numberOfTentatives");
                                        moduleSQLRequestService.updateStatusCourseToDuplicate(status, id, nbrTentatives, event13 -> {
                                            if (event13.isRight()) {
                                                log.error("Duplication web-service failed");
                                            } else {
                                                log.error("Failed to access database");
                                            }
                                            unauthorized(request);
                                        });
                                    }
                                }
                            });
                            break;
                        default:
                            log.error("Failed to read the Moodle response");
                    }
                }));
    }

    public void synchronisationDuplication(final Handler<Either<String, JsonObject>> eitherHandler) {
        String status = PENDING;
        moduleSQLRequestService.getCourseIdToDuplicate(status, event -> {
            if (event.isRight()) {
                if (event.right().getValue().size() < config.getInteger("numberOfMaxPendingDuplication")) {
                    String status1 = WAITING;
                    moduleSQLRequestService.getCourseIdToDuplicate(status1, event1 -> {
                        if (event1.isRight()) {
                            if (event1.right().getValue().size() != 0) {
                                JsonObject courseDuplicate = event1.right().getValue().getJsonObject(0);
                                JsonObject courseToDuplicate = new JsonObject();
                                courseToDuplicate.put("courseid", courseDuplicate.getInteger("id_course"));
                                courseToDuplicate.put("userid", courseDuplicate.getString("id_users"));
                                courseToDuplicate.put("folderid", courseDuplicate.getInteger("id_folder"));
                                courseToDuplicate.put("id", courseDuplicate.getInteger("id"));
                                courseToDuplicate.put("numberOfTentatives", courseDuplicate.getInteger("nombre_tentatives"));
                                final AtomicBoolean responseIsSent = new AtomicBoolean(false);
                                URI moodleUri = null;
                                try {
                                    final String service = config.getString("address_moodle") + config.getString("ws-path");
                                    final String urlSeparator = service.endsWith("") ? "" : "/";
                                    moodleUri = new URI(service + urlSeparator);
                                } catch (URISyntaxException e) {
                                    log.error("Invalid moodle web service sending demand of duplication uri", e);
                                }
                                if (moodleUri != null) {
                                    final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx, config);
                                    final String moodleUrl = moodleUri.toString() +
                                            "?wstoken=" + config.getString("wsToken") +
                                            "&wsfunction=" + WS_POST_DUPLICATECOURSE +
                                            "&parameters[idnumber]=" + courseToDuplicate.getString("userid") +
                                            "&parameters[course][0][moodlecourseid]=" + courseToDuplicate.getInteger("courseid") +
                                            "&parameters[course][0][ident]=" + courseDuplicate.getInteger("id") +
                                            "&moodlewsrestformat=" + JSON;

                                    webServiceMoodlePost(null, moodleUrl, httpClient, responseIsSent, event11 -> {
                                        if (event11.isRight()) {
                                            eitherHandler.handle(new Either.Right<>(event11.right().getValue().toJsonArray().getJsonObject(0).getJsonArray("courses").getJsonObject(0)));
                                        } else {
                                            log.error("Failed to contact Moodle");
                                            eitherHandler.handle(new Either.Left<>("Failed to contact Moodle"));
                                        }
                                    }, true);
                                }
                            } else {
                                log.debug("There are no course to duplicate in the duplication table !");
                                eitherHandler.handle(new Either.Left<>("There are no course to duplicate in the duplication table"));
                            }
                        } else {
                            log.error("The access to duplicate database failed !");
                            eitherHandler.handle(new Either.Left<>("The access to duplicate database failed"));
                        }
                    });
                } else {
                    log.error("The quota of duplication in same time is reached, you have to wait !");
                    eitherHandler.handle(new Either.Left<>("The quota of duplication in same time is reached, you have to wait"));
                }
            } else {
                log.error("The access to duplicate database failed !");
                eitherHandler.handle(new Either.Left<>("The access to duplicate database failed"));
            }
        });
    }
}
