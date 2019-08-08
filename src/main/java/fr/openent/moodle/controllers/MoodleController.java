package fr.openent.moodle.controllers;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.filters.CanShareResoourceFilter;
import fr.openent.moodle.helper.HttpClientHelper;
import fr.openent.moodle.service.MoodleEventBus;
import fr.openent.moodle.service.MoodleWebService;
import fr.openent.moodle.service.impl.DefaultMoodleEventBus;
import fr.openent.moodle.service.impl.DefaultMoodleWebService;
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
import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static fr.openent.moodle.Moodle.*;
import static fr.wseduc.webutils.http.response.DefaultResponseHandler.arrayResponseHandler;
import static java.util.Objects.isNull;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class MoodleController extends ControllerHelper {

	private final MoodleWebService moodleWebService;
	private final MoodleEventBus moodleEventBus;
	private final HttpClientHelper httpClientHelper;
    private final Storage storage;

    public MoodleController(final Storage storage, EventBus eb) {
		super();
        this.eb = eb;
        this.storage = storage;

		this.moodleWebService = new DefaultMoodleWebService(Moodle.moodleSchema, "course");
		this.moodleEventBus = new DefaultMoodleEventBus(Moodle.moodleSchema, "course", eb);
		this.httpClientHelper = new HttpClientHelper();
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
	 * @param request Client request
	 */
	@Get("")
	@SecuredAction(workflow_view)
	public void view(HttpServerRequest request) {
		renderView(request);
	}

    @Put("/folders/move")
    @ApiDoc("move a folder")
    //@SecuredAction("moodle.modify")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void moveFolder(final HttpServerRequest request){
        RequestUtils.bodyToJson(request, pathPrefix + "folder", new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject folders) {
                UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
                    @Override
                    public void handle(UserInfos user) {
                        if (user != null) {
                            moodleWebService.moveFolder(folders, defaultResponseHandler(request));
                        } else {
                            log.error("User not found in session.");
                            unauthorized(request);
                        }
                    }
                });
            }
        });
    }

    @Delete("/folder")
    @ApiDoc("delete a folder")
    public void deleteFolder(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "folder", new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject folder) {
                UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
                    @Override
                    public void handle(UserInfos user) {
                        if (user != null) {
                            moodleWebService.deleteFolders(folder, defaultResponseHandler(request));
                        } else {
                            log.error("User not found in session.");
                            unauthorized(request);
                        }
                    }
                });
            }
        });
    }

    @Post("/folder")
    @ApiDoc("create a folder")
    public void createFolder(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "folder", new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject folder) {
                UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
                    @Override
                    public void handle(UserInfos user){
                        if (user != null) {
                            folder.put("userId", user.getUserId());
                            folder.put("structureId", user.getStructures().get(0));
                            moodleWebService.createFolder(folder, defaultResponseHandler(request));
                        } else {
                            log.error("User not found in session.");
                            unauthorized(request);
                        }
                    }
                });
            }
        });
    }

    @Put("/folder/rename")
    @ApiDoc("rename a folder")
    public void renameFolder(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "folder", new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject folder) {
                moodleWebService.renameFolder(folder, defaultResponseHandler(request));
            }
        });
    }

    @Get("/folder/countsFolders/:id")
    @ApiDoc("Get cours in database by folder id")
    //@SecuredAction("moodle.list")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getCountsItemInFolder(final HttpServerRequest request){
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    long id_folder =Long.parseLong(request.params().get("id"));
                    moodleWebService.countItemInfolder(id_folder, user.getUserId(), DefaultResponseHandler.defaultResponseHandler(request));
                }
                else {
                    log.error("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    @Get("/folder/countsCourses/:id")
    @ApiDoc("Get cours in database by folder id")
    //@SecuredAction("moodle.list")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getCountsItemCoursesInFolder(final HttpServerRequest request){
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    long id_folder = Long.parseLong(request.params().get("id"));
                    moodleWebService.countCoursesItemInfolder(id_folder, user.getUserId(), DefaultResponseHandler.defaultResponseHandler(request));
                }
                else {
                    log.error("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    @Post("/course")
    @ApiDoc("create a course")
    @SecuredAction(workflow_create)
	public void create(final HttpServerRequest request) {
	    RequestUtils.bodyToJson(request, pathPrefix + "course", new Handler<JsonObject>() {
	        @Override
            public void handle(JsonObject course) {
	            if ("1".equals(course.getString("type"))) {
                    course.put("typeA", "");
                }
                if (isNull(course.getString("summary"))) {
	                course.put("summary", "");
	            }
                UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
	                @Override
                    public void handle(final UserInfos user) {
                        Calendar calendar =new GregorianCalendar();
                        String uniqueID = UUID.randomUUID().toString();
                        course.put("shortname", ((GregorianCalendar) calendar).toZonedDateTime().toString().substring(0, 7) +
                                user.getFirstName().substring(0, 1) + user.getLastName().substring(0, 3) +
                                course.getString("fullname").substring(0, 4) + uniqueID);
                        JsonArray zimbraEmail = new JsonArray();
                        zimbraEmail.add(user.getUserId());
                        log.info("getZimbraEmail : " + zimbraEmail.toString());
                        moodleEventBus.getZimbraEmail(zimbraEmail, new Handler<Either<String, JsonObject>>() {
                            @Override
                            public void handle(Either<String, JsonObject> event) {
                                if (event.isRight()) {
                                    log.info("succes getZimbraEmail : ");
                                    final AtomicBoolean responseIsSent = new AtomicBoolean(false);
                                    URI moodleUri = null;
                                    try {
                                        final String service = (config.getString("address_moodle") + config.getString("ws-path"));
                                        final String urlSeparator = service.endsWith("") ? "" : "/";
                                        moodleUri = new URI(service + urlSeparator);
                                    } catch (URISyntaxException e) {
                                        log.error("Invalid moodle web service creating a course uri",e);
                                    }
                                    if (moodleUri != null) {
                                        JsonObject shareSend = new JsonObject();
                                        shareSend = null;
                                        try {
                                            String idImage = course.getString("imageurl");
                                            String urlImage = "";
                                            if (idImage != null) {
                                                urlImage = "&parameters[imageurl]=" + URLEncoder.encode(getScheme(request), "UTF-8") + "://" + URLEncoder.encode(getHost(request), "UTF-8") + "/moodle/files/" + URLEncoder.encode(idImage, "UTF-8");
                                            }

                                            log.info(event.right().getValue());
                                            String userMail = event.right().getValue().getJsonObject(user.getUserId()).getString("email");
                                            log.info("userMail : " + userMail);
                                            final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx);
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

                                            httpClientHelper.webServiceMoodlePost(shareSend, moodleUrl, httpClient, responseIsSent, new Handler<Either<String, Buffer>>() {
                                                @Override
                                                public void handle(Either<String, Buffer> event) {
                                                    if (event.isRight()) {
                                                        log.info("SUCCESS creating course : ");
                                                        JsonObject object = event.right().getValue().toJsonArray().getJsonObject(0);
                                                        log.info(object);
                                                        course.put("moodleid", object.getValue("courseid"))
                                                                .put("userid", user.getUserId());
                                                        moodleWebService.createCourse(course, defaultResponseHandler(request));
                                                    } else {
                                                        log.error("FAIL creating course : " + event.left().getValue());
                                                        log.error("fail to call create course webservice" + event.left().getValue());
                                                    }
                                                }
                                            }, true);
                                        } catch (UnsupportedEncodingException e) {
                                            log.error("fail to create course by sending the URL to the WS", e);
                                        }
                                    }
                                } else {
                                    log.error("fail getZimbraEmail : ");
                                    log.error(event.left().getValue());
                                    handle(new Either.Left<>("Failed to gets the http params"));
                                }
                            }
                        });
                    }
                });
            }
        });
	}

    @ApiDoc("public Get pictutre for moodle webside")
    @Get("/files/:id")
    public void getFile(HttpServerRequest request) {
        moodleEventBus.getImage(request.getParam("id"), event -> {
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
        if (true) {
            moodleEventBus.getImage(request.getParam("id"), DefaultResponseHandler.defaultResponseHandler(request));
        } else {
            badRequest(request);
        }
    }

    @Get("/folders")
    @ApiDoc("Get folder in database")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void listFoldersAndShared(final HttpServerRequest request){
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    moodleWebService.getFoldersInEnt(user.getUserId(), arrayResponseHandler(request));
                }
                else {
                    log.error("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    @Get("/users/courses")
    @ApiDoc("Get cours by user in database")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void listCouresByuser(final HttpServerRequest request){
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(UserInfos user) {
                if(user != null){
                    moodleWebService.getCoursesByUserInEnt(user.getUserId(), new Handler<Either<String, JsonArray>>() {
                        @Override
                        public void handle(Either<String, JsonArray> sqlCours) {
                            if(sqlCours.isRight()){
                                final JsonArray sqlCoursArray = sqlCours.right().getValue();
                                final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx);
                                final String moodleUrl = (config.getString("address_moodle")+ config.getString("ws-path")) +
                                        "?wstoken=" + config.getString("wsToken") +
                                        "&wsfunction=" + WS_GET_USERCOURSES +
                                        //"&parameters[userid]=" + "00d17c30-e693-42f8-a9ba-c2e3f7f62186" +
                                        "&parameters[userid]=" + user.getUserId() +
                                        "&moodlewsrestformat=" + JSON;
                                final AtomicBoolean responseIsSent = new AtomicBoolean(false);
                                Buffer wsResponse = new BufferImpl();
                                log.info("CALL WS_GET_USERCOURSES : "+moodleUrl);
                                final HttpClientRequest httpClientRequest = httpClient.getAbs(moodleUrl, new Handler<HttpClientResponse>() {
                                    @Override
                                    public void handle(HttpClientResponse response) {
                                        if (response.statusCode() == 200) {
                                            log.info("SUCCESS WS_GET_USERCOURSES : ");
                                            response.handler(wsResponse::appendBuffer);
                                            response.endHandler(new Handler<Void>() {
                                                @Override
                                                public void handle(Void end) {
                                                    JsonArray object = new JsonArray(wsResponse);
                                                    JsonArray duplicatesCours = object.getJsonObject(0).getJsonArray("enrolments");
                                                    log.info(duplicatesCours.toString());
                                                    JsonArray coursArray = Utils.removeDuplicateCourses(duplicatesCours);

                                                    List<String> sqlCoursId = sqlCoursArray.stream().map(obj -> (((JsonObject) obj).getValue("moodle_id")).toString()).collect(Collectors.toList());

                                                    for(int i = 0; i < coursArray.size(); i++){
                                                        coursArray.getJsonObject(i).put("duplication","non");
                                                        if(sqlCoursId.contains(coursArray.getJsonObject(i).getValue("courseid").toString()))
                                                            coursArray.getJsonObject(i).put("folderid",Integer.parseInt(sqlCoursArray.getJsonObject(sqlCoursId.indexOf(coursArray.getJsonObject(i).getValue("courseid").toString())).getValue("folder_id").toString()));
                                                        else{
                                                            coursArray.getJsonObject(i).put("moodleid", coursArray.getJsonObject(i).getValue("courseid"))
                                                                    .put("userid", user.getUserId()).put("folderid",0);
                                                            String auteur = coursArray.getJsonObject(i).getJsonArray("auteur").getJsonObject(0).getString("entidnumber");
                                                            if(auteur!= null && auteur.equals(user.getUserId())) {
                                                                    moodleWebService.createCourse(coursArray.getJsonObject(i), defaultResponseHandler(request));
                                                            }
                                                        }
                                                    }

                                                    List<String> coursId = coursArray.stream().map(obj -> (((JsonObject) obj).getValue("courseid")).toString()).collect(Collectors.toList());

                                                    moodleWebService.getCourseToDuplicate(user.getUserId(), new Handler<Either<String, JsonArray>> () {
                                                        @Override
                                                        public void handle(Either<String, JsonArray> event) {
                                                            if (event.right().getValue().size() != 0) {
                                                                JsonArray coursesInDuplication = event.right().getValue();
                                                                for(int i = 0; i < coursesInDuplication.size(); i++){
                                                                    JsonObject courseToAdd = new JsonObject();
                                                                    Timestamp timestamp = new Timestamp(System.currentTimeMillis());
                                                                    courseToAdd.put("fullname",coursArray.getJsonObject(coursId.indexOf(coursesInDuplication.getJsonObject(i).getInteger("id_course").toString())).getString("fullname") + "_" + LocalDateTime.now().getYear() + "-" + LocalDateTime.now().getMonthValue() + "-" + LocalDateTime.now().getDayOfMonth());
                                                                    courseToAdd.put("summary", coursArray.getJsonObject(coursId.indexOf(coursesInDuplication.getJsonObject(i).getInteger("id_course").toString())).getString("summary"));
                                                                    courseToAdd.put("auteur", coursArray.getJsonObject(coursId.indexOf(coursesInDuplication.getJsonObject(i).getInteger("id_course").toString())).getJsonArray("auteur"));
                                                                    courseToAdd.put("type", coursArray.getJsonObject(coursId.indexOf(coursesInDuplication.getJsonObject(i).getInteger("id_course").toString())).getString("type"));
                                                                    courseToAdd.put("course_type", coursArray.getJsonObject(coursId.indexOf(coursesInDuplication.getJsonObject(i).getInteger("id_course").toString())).getString("course_type"));
                                                                    courseToAdd.put("imageurl", coursArray.getJsonObject(coursId.indexOf(coursesInDuplication.getJsonObject(i).getInteger("id_course").toString())).getString("imageurl"));
                                                                    courseToAdd.put("date", Long.toString((timestamp.getTime()/1000)));
                                                                    courseToAdd.put("timemodified", timestamp.getTime()/1000);
                                                                    courseToAdd.put("duplication",coursesInDuplication.getJsonObject(i).getString("status"));
                                                                    courseToAdd.put("originalCourseId",coursesInDuplication.getJsonObject(i).getInteger("id_course"));
                                                                    courseToAdd.put("folderid",coursesInDuplication.getJsonObject(i).getInteger("id_folder"));
                                                                    courseToAdd.put("courseid",coursesInDuplication.getJsonObject(i).getInteger("id"));
                                                                    coursArray.add(courseToAdd);
                                                                }
                                                            } else {
                                                                log.debug("There are no course to duplicate in the duplication table !");
                                                            }
                                                        }
                                                    });
                                                    moodleWebService.getPreferences(user.getUserId(), new Handler<Either<String, JsonArray>>() {
                                                        @Override
                                                        public void handle(Either<String, JsonArray> event) {
                                                            if (event.isRight()){
                                                                JsonArray list = event.right().getValue();
                                                                if(list.size() != 0) {
                                                                    for (int i = 0; i < coursArray.size(); i++) {
                                                                        JsonObject cours = coursArray.getJsonObject(i);
                                                                        for (int j = 0; j < list.size(); j++) {
                                                                            JsonObject PreferencesCours = list.getJsonObject(j);
                                                                            if(cours.containsKey("courseid")) {
                                                                                if (PreferencesCours.getValue("moodle_id").toString().equals(cours.getValue("courseid").toString())) {
                                                                                    coursArray.getJsonObject(i).put("masked", PreferencesCours.getValue("masked"));
                                                                                    coursArray.getJsonObject(i).put("favorites", PreferencesCours.getValue("favorites"));
                                                                                }
                                                                            }
                                                                        }
                                                                        if(!(coursArray.getJsonObject(i).containsKey("masked"))){
                                                                            coursArray.getJsonObject(i).put("masked", false);
                                                                            coursArray.getJsonObject(i).put("favorites", false);
                                                                        }
                                                                    }
                                                                }else{
                                                                    for (int i = 0; i < coursArray.size(); i++) {
                                                                        coursArray.getJsonObject(i).put("masked", false);
                                                                        coursArray.getJsonObject(i).put("favorites", false);
                                                                    }
                                                                }
                                                                Renders.renderJson(request, coursArray);
                                                            } else {
                                                                log.error("Get list favorites and masked failed !");
                                                                renderError(request);
                                                            }
                                                        }
                                                    });
                                                    if (!responseIsSent.getAndSet(true)) {
                                                        httpClient.close();
                                                    }
                                                }
                                            });
                                        } else {
                                            log.error("fail to call get courses webservice" + response.statusMessage());
                                            response.bodyHandler(new Handler<Buffer>() {
                                                @Override
                                                public void handle(Buffer event) {
                                                    log.error("Returning body after GET CALL : " +  moodleUrl + ", Returning body : " + event.toString("UTF-8"));
                                                    if (!responseIsSent.getAndSet(true)) {
                                                        httpClient.close();
                                                    }
                                                }
                                            });
                                            renderError(request);
                                        }
                                    }
                                });
                                httpClientRequest.headers().set("Content-Length", "0");
                                //Typically an unresolved Address, a timeout about connection or response
                                httpClientRequest.exceptionHandler(new Handler<Throwable>() {
                                    @Override
                                    public void handle(Throwable event) {
                                        log.error(event.getMessage(), event);
                                        if (!responseIsSent.getAndSet(true)) {
                                            renderError(request);
                                            httpClient.close();
                                        }
                                    }
                                }).end();
                            } else {
                                log.error("Get list in Ent Base failed");
                                renderError(request);
                            }
                        }
                    });
                } else {
                    unauthorized(request);
                }
            }
        });
    }

    @Put("/courses/move")
    @ApiDoc("move a course")
    //@SecuredAction("moodle.modify")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void moveCourse(final HttpServerRequest request){
        RequestUtils.bodyToJson(request, pathPrefix + "courses", new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject courses) {
                UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
                    @Override
                    public void handle(UserInfos user) {
                        if (user != null) {
                            moodleWebService.moveCourse(courses, defaultResponseHandler(request));
                        } else {
                            log.error("User not found in session.");
                            unauthorized(request);
                        }
                    }
                });
            }
        });
    }

    @Delete("/course")
    @ApiDoc("Delete a course")
    @SecuredAction(workflow_delete)
    public void delete (final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "courses", new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject courses) {
                JsonArray coursesIds = courses.getJsonArray("coursesId");
                String idsDeletes = "";
                for (int i = 0; i < coursesIds.size(); i++) {
                    idsDeletes += "&courseids[" + i + "]=" + coursesIds.getValue(i);
                }
                final AtomicBoolean responseIsSent = new AtomicBoolean(false);
                URI moodleDeleteUri = null;
                try {
                    final String service = (config.getString("address_moodle") + config.getString("ws-path"));
                    final String urlSeparator = service.endsWith("") ? "" : "/";
                    moodleDeleteUri = new URI(service + urlSeparator);
                } catch (URISyntaxException e) {
                    log.error("Invalid moodle web service deleting course uri",e);
                }
                if (moodleDeleteUri != null) {
                    JsonObject shareSend = new JsonObject();
                    shareSend = null;
                    final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx);
                    final String moodleDeleteUrl = moodleDeleteUri.toString() +
                            "?wstoken=" + config.getString("wsToken") +
                            "&wsfunction=" + WS_DELETE_FUNCTION +
                            idsDeletes +
                            "&moodlewsrestformat=" + JSON;
                    httpClientHelper.webServiceMoodlePost(shareSend, moodleDeleteUrl, httpClient, responseIsSent, new Handler<Either<String, Buffer>>() {
                        @Override
                        public void handle(Either<String, Buffer> event) {
                            if (event.isRight()) {
                                moodleWebService.deleteCourse(courses, defaultResponseHandler(request));
                            } else {
                                log.error("Post service failed"  + event.left().getValue());
                            }
                        }
                    }, true);
                }
            }
        });
	}

	@Get("/course/:id")
    @ApiDoc("Redirect to Moodle")
    public void redirectToMoodle (HttpServerRequest request){
	    String scope = request.params().contains("scope") ? request.getParam("scope") : "view";
	    redirect(request, config.getString("address_moodle"), "/course/" + scope + ".php?id=" +
                request.getParam("id") + "&notifyeditingon=1");
    }

    @Get("/choices")
    @ApiDoc("get a choice")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getChoices (final HttpServerRequest request) {
	    UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
	        @Override
            public void handle(UserInfos user) {
	            if (user != null) {
	                String userId = user.getUserId();
	                moodleWebService.getChoices(userId, arrayResponseHandler(request));
	            } else {
	                log.error("User not found in session.");
	                unauthorized(request);
	            }
	        }
	    });
	}

    @Put("/choices/:view")
    @ApiDoc("set a choice")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void setChoice (final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "courses", new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject courses) {
                UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
                    @Override
                    public void handle(UserInfos user) {
                        if (user != null) {
                            courses.put("userId", user.getUserId());
                            String view = request.getParam("view");
                            moodleWebService.setChoice(courses, view, defaultResponseHandler(request));
                        } else {
                            log.error("User not found in session.");
                            unauthorized(request);
                        }
                    }
                });
            }
        });
    }

    @Put("/course/preferences")
    @ApiDoc("set preferences")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void setPreferences(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "coursePreferences", new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject coursePreferences) {
                UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
                    @Override
                    public void handle(UserInfos user) {
                        if (user != null) {
                            coursePreferences.put("userId", user.getUserId());
                            moodleWebService.setPreferences(coursePreferences, defaultResponseHandler(request));
                        } else {
                            log.error("User not found in session.");
                            unauthorized(request);
                        }
                    }
                });
            }
        });
    }

    @Get("/share/json/:id")
    @ApiDoc("Lists rights for a given course.")
    @ResourceFilter(CanShareResoourceFilter.class)
    @SecuredAction(value = resource_read, type = ActionType.RESOURCE)
    public void share(final HttpServerRequest request) {
        final Handler<Either<String, JsonObject>> handler = defaultResponseHandler(request);
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(UserInfos user) {
                if (user != null) {
                    List<Future> listeFutures = new ArrayList<Future>();

                    Future<JsonObject> getShareInfosFuture = Future.future();
                    Handler<Either<String, JsonObject>> getShareInfosHandler = finalUsers -> {
                        if (finalUsers.isRight()) {
                            getShareInfosFuture.complete(finalUsers.right().getValue());
                        } else {
                            getShareInfosFuture.fail( "Share infos not found");
                        }
                    };
                    shareService.shareInfos(user.getUserId(), request.getParam("id"),
                            I18n.acceptLanguage(request), request.params().get("search"), getShareInfosHandler);
                    listeFutures.add(getShareInfosFuture);

                    final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx);
                    final AtomicBoolean responseIsSent = new AtomicBoolean(false);
                    Buffer wsResponse = new BufferImpl();
                    final String moodleUrl = (config.getString("address_moodle")+ config.getString("ws-path")) +
                            "?wstoken=" + config.getString("wsToken") +
                            "&wsfunction=" + WS_GET_SHARECOURSE +
                            "&parameters[courseid]=" + request.getParam("id") +
                            "&moodlewsrestformat=" + JSON;

                    Future<JsonArray> getUsersEnrolementsFuture = Future.future();
                    Handler<HttpClientResponse> getUsersEnrolementsHandler = response -> {
                        if (response.statusCode() == 200) {
                            response.handler(wsResponse::appendBuffer);
                            response.endHandler(new Handler<Void>() {
                                @Override
                                public void handle(Void end) {
                                    JsonArray finalGroups = new JsonArray(wsResponse);
                                    getUsersEnrolementsFuture.complete(finalGroups);
                                    if (!responseIsSent.getAndSet(true)) {
                                        httpClient.close();
                                    }
                                }
                            });
                        } else {
                            log.error("fail to call get share course right webservice" + response.statusMessage());
                            response.bodyHandler(new Handler<Buffer>() {
                                @Override
                                public void handle(Buffer event) {
                                    log.error("Returning body after GET CALL : " + moodleUrl + ", Returning body : " + event.toString("UTF-8"));
                                    getUsersEnrolementsFuture.fail(response.statusMessage());
                                    if (!responseIsSent.getAndSet(true)) {
                                        httpClient.close();
                                    }
                                }
                            });
                        }
                    };

                    final HttpClientRequest httpClientRequest = httpClient.getAbs(moodleUrl, getUsersEnrolementsHandler);
                    listeFutures.add(getUsersEnrolementsFuture);
                    httpClientRequest.headers().set("Content-Length", "0");
                    //Typically an unresolved Address, a timeout about connection or response
                    httpClientRequest.exceptionHandler(new Handler<Throwable>() {
                        @Override
                        public void handle(Throwable event) {
                            log.error(event.getMessage(), event);
                            getUsersEnrolementsFuture.fail( event.getMessage());
                            if (!responseIsSent.getAndSet(true)) {
                                renderError(request);
                                httpClient.close();
                            }
                        }
                    }).end();

                    CompositeFuture.all(listeFutures).setHandler(event -> {
                        if (event.succeeded()) {
                            JsonObject shareInfosFuture = getShareInfosFuture.result();
                            JsonArray usersEnrolmentsFuture = getUsersEnrolementsFuture.result();
                            if (usersEnrolmentsFuture != null && !usersEnrolmentsFuture.isEmpty() && shareInfosFuture != null && !shareInfosFuture.isEmpty()) {
                                JsonArray groupEnroled = usersEnrolmentsFuture.getJsonObject(0).getJsonArray("enrolments").getJsonObject(0).getJsonArray("groups");
                                JsonArray shareInfosGroups = shareInfosFuture.getJsonObject("groups").getJsonArray("visibles");
                                JsonArray usersEnroled = usersEnrolmentsFuture.getJsonObject(0).getJsonArray("enrolments").getJsonObject(0).getJsonArray("users");
                                JsonArray shareInfosUsers = shareInfosFuture.getJsonObject("users").getJsonArray("visibles");
                                List<String> usersEnroledId = usersEnroled.stream().map(obj -> ((JsonObject) obj).getString("id")).collect(Collectors.toList());
                                List<String> usersshareInfosId = shareInfosUsers.stream().map(obj -> ((JsonObject) obj).getString("id")).collect(Collectors.toList());
                                if(groupEnroled.size() > 0){
                                    List<String> groupsEnroledId = groupEnroled.stream().map(obj -> ((JsonObject)obj).getString("idnumber") ).collect(Collectors.toList());
                                    List<String> groupsshareInfosId = shareInfosGroups.stream().map(obj -> ((JsonObject)obj).getString("id") ).collect(Collectors.toList());
                                    for (String groupId: groupsEnroledId) {
                                        //groupIds.add(groupId.substring(3));
                                        if(!(groupsshareInfosId.contains(groupId))){
                                            JsonObject jsonobjctToAdd = usersEnrolmentsFuture.getJsonObject(0).getJsonArray("enrolments").getJsonObject(0).getJsonArray("groups").getJsonObject(groupsEnroledId.indexOf(groupId));
                                            String id = jsonobjctToAdd.getString("idnumber");
                                            jsonobjctToAdd.remove("idnumber");
                                            jsonobjctToAdd.put("id", id);
                                            //jsonobjctToAdd.put("groupDisplayName", null);
                                            //jsonobjctToAdd.put("structureName",null);
                                            UserUtils.groupDisplayName(jsonobjctToAdd, I18n.acceptLanguage(request));
                                            shareInfosFuture.getJsonObject("groups").getJsonArray("visibles").add(jsonobjctToAdd);
                                        }
                                    }

                                    for (Object group : groupEnroled) {
                                        JsonArray tabToAdd = new JsonArray().add(MOODLE_READ).add(MOODLE_CONTRIB).add(MOODLE_MANAGER);
                                        if(((JsonObject)group).getInteger("role").equals(config.getInteger("idStudent"))){
                                            tabToAdd.remove(2);
                                        }
                                        shareInfosFuture.getJsonObject("groups").getJsonObject("checked").put(((JsonObject) group).getString("id"),tabToAdd);
                                    }

                                    for(Object userEnroled : usersEnroled.copy()) {
                                        if (!(isNull(((JsonObject) userEnroled).getValue("idnumber")))) {
                                            usersEnrolmentsFuture.getJsonObject(0).getJsonArray("enrolments").getJsonObject(0).getJsonArray("users").remove(usersEnroledId.indexOf(((JsonObject) userEnroled).getString("id")));
                                            usersEnroled = usersEnrolmentsFuture.getJsonObject(0).getJsonArray("enrolments").getJsonObject(0).getJsonArray("users");
                                            usersEnroledId = usersEnroled.stream().map(obj -> ((JsonObject) obj).getString("id")).collect(Collectors.toList());

                                        }
                                    }
                                }

                                if (shareInfosFuture.getJsonArray("actions").size() == 3)
                                    shareInfosFuture.getJsonArray("actions").remove(1);
                                while (usersEnroledId.contains(user.getUserId())) {
                                    usersEnrolmentsFuture.getJsonObject(0).getJsonArray("enrolments").getJsonObject(0).getJsonArray("users").remove(usersEnroledId.indexOf(user.getUserId()));
                                    usersEnroled = usersEnrolmentsFuture.getJsonObject(0).getJsonArray("enrolments").getJsonObject(0).getJsonArray("users");
                                    usersEnroledId = usersEnroled.stream().map(obj -> ((JsonObject) obj).getString("id")).collect(Collectors.toList());
                                }

                                for (String userId : usersEnroledId) {
                                    if (!(usersshareInfosId.contains(userId))) {
                                        JsonObject jsonobjctToAdd = usersEnrolmentsFuture.getJsonObject(0).getJsonArray("enrolments").getJsonObject(0).getJsonArray("users").getJsonObject(usersEnroledId.indexOf(userId)).copy();
                                        String prenom = jsonobjctToAdd.getString("firstname");
                                        String nom = jsonobjctToAdd.getString("lastname");
                                        jsonobjctToAdd.remove("firstname");
                                        jsonobjctToAdd.remove("lastname");
                                        jsonobjctToAdd.put("firstName", prenom.charAt(0) + prenom.substring(1).toLowerCase());
                                        jsonobjctToAdd.put("lastName", nom);
                                        jsonobjctToAdd.put("login", prenom.toLowerCase() + "." + nom.toLowerCase());
                                        jsonobjctToAdd.put("username", nom + " " + prenom.charAt(0) + prenom.substring(1).toLowerCase());
                                        String profile = "Relative";
                                        if (jsonobjctToAdd.getInteger("role").equals(config.getInteger("idStudent"))) {
                                            profile = "Student";
                                        }
                                        jsonobjctToAdd.put("profile", profile);
                                        jsonobjctToAdd.remove("role");
                                        shareInfosFuture.getJsonObject("users").getJsonArray("visibles").add(jsonobjctToAdd);
                                    }
                                }

                                for (Object userEnroled : usersEnroled) {
                                    JsonArray tabToAdd = new JsonArray().add(MOODLE_READ).add(MOODLE_CONTRIB).add(MOODLE_MANAGER);
                                    if (((JsonObject) userEnroled).getInteger("role").equals(config.getInteger("idStudent"))) {
                                        tabToAdd.remove(2);
                                    }
                                    shareInfosFuture.getJsonObject("users").getJsonObject("checked").put(((JsonObject) userEnroled).getString("id"), tabToAdd);
                                }

                            handler.handle(new Either.Right<String, JsonObject>(shareInfosFuture));
                            }
                        } else {
                            badRequest(request, event.cause().getMessage());
                            //renderError(request);
                        }
                    });
                } else {
                    log.error("User or group not found.");
                    unauthorized(request);
                }
            }
        });
    }

    @Put("/contrib")
    @ApiDoc("Adds rights for a given course.")
    @ResourceFilter(CanShareResoourceFilter.class)
    @SecuredAction(value = resource_contrib, type = ActionType.RESOURCE)
    public void contrib(final HttpServerRequest request) {

    }

    @Put("/share/resource/:id")
    @ApiDoc("Adds rights for a given course.")
    @ResourceFilter(CanShareResoourceFilter.class)
    @SecuredAction(value = resource_manager, type = ActionType.RESOURCE)
    public void shareSubmit(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "share", new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject shareCourse) {
                UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
                    @Override
                    public void handle(UserInfos user) {
                        if (user != null) {
                            for (Object idGroup : shareCourse.copy().getJsonObject("groups").getMap().keySet().toArray() ) {
                                if(idGroup.toString().substring(0,3).equals("GR_")){
                                    shareCourse.getJsonObject("groups").put(idGroup.toString().substring(3),shareCourse.getJsonObject("groups").getValue(idGroup.toString()));
                                    shareCourse.getJsonObject("groups").remove(idGroup.toString());
                                }
                                if(idGroup.toString().substring(0,3).equals("SB_")){
                                    shareCourse.getJsonObject("bookmarks").put(idGroup.toString().substring(2),shareCourse.getJsonObject("groups").getValue(idGroup.toString()));
                                    shareCourse.getJsonObject("groups").remove(idGroup.toString());
                                }
                            }
                            List<Future> listeFutures = new ArrayList<>();
                            JsonObject IdFront = new JsonObject();
                            JsonObject keyShare = new JsonObject();
                            JsonObject share = new JsonObject();

                            Map<String, Object> idUsers = shareCourse.getJsonObject("users").getMap();
                            Map<String, Object> idGroups = shareCourse.getJsonObject("groups").getMap();
                            Map<String, Object> idBookmarks = shareCourse.getJsonObject("bookmarks").getMap();

                            JsonArray usersIds = new JsonArray(new ArrayList(idUsers.keySet()));
                            JsonArray groupsIds = new JsonArray(new ArrayList(idGroups.keySet()));
                            JsonArray bookmarksIds = new JsonArray(new ArrayList(idBookmarks.keySet()));

                            if (!shareCourse.getJsonObject("users").isEmpty() && shareCourse.getJsonObject("users").size() > 1) {
                                for (Map.Entry<String, Object> mapShareUsers : idUsers.entrySet()) {
                                    IdFront.put(mapShareUsers.getKey(), mapShareUsers.getValue());
                                    if (IdFront.getJsonArray(mapShareUsers.getKey()).size() == 2) {
                                        keyShare.put(mapShareUsers.getKey(), config.getInteger("idEditingTeacher"));
                                    }
                                    if (IdFront.getJsonArray(mapShareUsers.getKey()).size() == 1) {
                                        keyShare.put(mapShareUsers.getKey(), config.getInteger("idStudent"));
                                        Map<String, Object> mapInfo = keyShare.getMap();
                                    }
                                }
                            } else if (!shareCourse.getJsonObject("users").isEmpty() && shareCourse.getJsonObject("users").size() == 1) {
                                if (shareCourse.getJsonObject("users").getJsonArray(usersIds.getValue(0).toString()).size() == 2) {
                                    keyShare.put(usersIds.getString(0), config.getInteger("idEditingTeacher"));
                                }
                                if (shareCourse.getJsonObject("users").getJsonArray(usersIds.getValue(0).toString()).size() == 1) {
                                    keyShare.put(usersIds.getString(0), config.getInteger("idStudent"));
                                }
                            }
                            if (!shareCourse.getJsonObject("groups").isEmpty() && shareCourse.getJsonObject("groups").size() > 1) {
                                for (Map.Entry<String, Object> mapShareGroups : idGroups.entrySet()) {
                                    IdFront.put(mapShareGroups.getKey(), mapShareGroups.getValue());
                                    if (IdFront.getJsonArray(mapShareGroups.getKey()).size() == 2) {
                                        keyShare.put(mapShareGroups.getKey(), config.getInteger("idEditingTeacher"));
                                    }
                                    if (IdFront.getJsonArray(mapShareGroups.getKey()).size() == 1) {
                                        keyShare.put(mapShareGroups.getKey(), config.getInteger("idStudent"));
                                    }
                                }
                            } else if (!shareCourse.getJsonObject("groups").isEmpty() && shareCourse.getJsonObject("groups").size() == 1) {
                                if (shareCourse.getJsonObject("groups").getJsonArray(groupsIds.getValue(0).toString()).size() == 2) {
                                    keyShare.put(groupsIds.getString(0), config.getInteger("idEditingTeacher"));
                                }
                                if (shareCourse.getJsonObject("groups").getJsonArray(groupsIds.getValue(0).toString()).size() == 1) {
                                    keyShare.put(groupsIds.getString(0), config.getInteger("idStudent"));
                                }
                            }
                            if (!shareCourse.getJsonObject("bookmarks").isEmpty() && shareCourse.getJsonObject("bookmarks").size() > 1) {
                                for (Map.Entry<String, Object> mapShareGroups : idBookmarks.entrySet()) {
                                    IdFront.put(mapShareGroups.getKey(), mapShareGroups.getValue());
                                    if (IdFront.getJsonArray(mapShareGroups.getKey()).size() == 2) {
                                        keyShare.put(mapShareGroups.getKey(), config.getInteger("idEditingTeacher"));
                                    }
                                    if (IdFront.getJsonArray(mapShareGroups.getKey()).size() == 1) {
                                        keyShare.put(mapShareGroups.getKey(), config.getInteger("idStudent"));
                                    }
                                }
                            } else if (!shareCourse.getJsonObject("bookmarks").isEmpty() && shareCourse.getJsonObject("bookmarks").size() == 1) {
                                if (shareCourse.getJsonObject("bookmarks").getJsonArray(bookmarksIds.getValue(0).toString()).size() == 2) {
                                    keyShare.put(bookmarksIds.getString(0), config.getInteger("idEditingTeacher"));
                                }
                                if (shareCourse.getJsonObject("bookmarks").getJsonArray(bookmarksIds.getValue(0).toString()).size() == 1) {
                                    keyShare.put(bookmarksIds.getString(0), config.getInteger("idStudent"));
                                }
                            }
                            final Map<String, Object> mapInfo = keyShare.getMap();
                            mapInfo.put(user.getUserId(), config.getInteger("idEditingTeacher"));
                            share.put("courseid", request.params().entries().get(0).getValue());

                            Future<JsonArray> getUsersFuture = Future.future();
                            Handler<Either<String, JsonArray>> getUsersHandler = finalUsers -> {
                                if (finalUsers.isRight()) {
                                    getUsersFuture.complete(finalUsers.right().getValue());
                                } else {
                                    getUsersFuture.fail( "Users not found");
                                }
                            };

                            usersIds.add(user.getUserId());
                            if (!usersIds.isEmpty()) {
                                moodleWebService.getUsers(usersIds, getUsersHandler);
                                listeFutures.add(getUsersFuture);
                            }

                            Future<JsonArray> getGroupsFuture = Future.future();
                            Handler<Either<String, JsonArray>> getGroupsHandler = finalGroups -> {
                                if (finalGroups.isRight()) {
                                    getGroupsFuture.complete(finalGroups.right().getValue());
                                } else {
                                    getGroupsFuture.fail( "Groups not found");
                                }
                            };
                            if (!groupsIds.isEmpty()) {
                                moodleWebService.getGroups(groupsIds, getGroupsHandler);
                                listeFutures.add(getGroupsFuture);
                            }

                            Future<JsonArray> getShareGroupsFuture = Future.future();
                            Handler<Either<String, JsonArray>> getShareGroupsHandler = finalShareGroups -> {
                                if (finalShareGroups.isRight()) {
                                    getShareGroupsFuture.complete(finalShareGroups.right().getValue());
                                } else {
                                    getShareGroupsFuture.fail("Share groups problem");
                                }
                            };
                            if (!bookmarksIds.isEmpty()) {
                                listeFutures.add(getShareGroupsFuture);
                                moodleWebService.getSharedBookMark(bookmarksIds, getShareGroupsHandler);
                            }

                            CompositeFuture.all(listeFutures).setHandler(event -> {
                                if (event.succeeded()) {
                                    JsonArray usersFuture = getUsersFuture.result();
                                    JsonArray groupsFuture = getGroupsFuture.result();
                                    JsonArray shareGroups = getShareGroupsFuture.result();
                                    if (usersFuture != null && !usersFuture.isEmpty()) {
                                        share.put("users", usersFuture);
                                        for (Object userObj : usersFuture) {
                                            JsonObject userJson = ((JsonObject) userObj);
                                            if (userJson.getString("id").equals(user.getUserId())) {
                                                userJson.put("role", config.getInteger("idAuditeur"));
                                            } else {
                                                userJson.put("role", mapInfo.get(userJson.getString("id")));
                                            }
                                        }
                                    }
                                    if (groupsFuture != null && !groupsFuture.isEmpty()) {
                                        share.put("groups", groupsFuture);
                                        for (Object groupObj : groupsFuture) {
                                            JsonObject groupJson = ((JsonObject) groupObj);
                                            groupJson.put("role", mapInfo.get(groupJson.getString("id").substring(3)));
                                            UserUtils.groupDisplayName(groupJson, I18n.acceptLanguage(request));
                                        }
                                    }
                                    List<Future> listeUsersFutures = new ArrayList<>();
                                    List<Integer> listeRankGroup = new ArrayList<>();
                                    int i = 0;
                                    if (shareGroups != null && !shareGroups.isEmpty()) {
                                        for (Object shareGroup : shareGroups.getJsonObject(0).getJsonArray("sharedBookMarks")) {
                                            JsonArray shareJson = ((JsonObject) shareGroup).getJsonObject("group").getJsonArray("users");
                                            JsonArray groupsId = new JsonArray();
                                            for(Object userOrGroup : shareJson){
                                                JsonObject userJson = ((JsonObject) userOrGroup);
                                                if(isNull(userJson.getValue("email")))
                                                    groupsId.add(userJson.getValue("id"));
                                            }
                                            if(groupsId.size() > 0){
                                                Future<JsonArray> getUsersGroupsFuture = Future.future();
                                                Handler<Either<String, JsonArray>> getUsersGroupsHandler = finalUsersGroups -> {
                                                    if (finalUsersGroups.isRight()) {
                                                        getUsersGroupsFuture.complete(finalUsersGroups.right().getValue());
                                                    } else {
                                                        getUsersGroupsFuture.fail("Share groups problem");
                                                    }
                                                };
                                                listeUsersFutures.add(getUsersGroupsFuture);
                                                listeRankGroup.add(i);
                                                moodleWebService.getGroups(groupsId, getUsersGroupsHandler);
                                            }
                                            ((JsonObject) shareGroup).getJsonObject("group").put("role", mapInfo.get(((JsonObject) shareGroup).getJsonObject("group").getString("id").substring(2)));
                                            if (share.containsKey("groups"))
                                                share.getJsonArray("groups").add(((JsonObject) shareGroup).getJsonObject("group"));
                                            else {
                                                JsonArray jsonArrayToAdd = new JsonArray();
                                                jsonArrayToAdd.add(((JsonObject) shareGroup).getJsonObject("group"));
                                                share.put("groups", jsonArrayToAdd);
                                            }
                                            i++;
                                        }
                                    }
                                    if(listeUsersFutures.size() > 0) {
                                        CompositeFuture.all(listeUsersFutures).setHandler(finished -> {
                                            if (finished.succeeded()) {
                                                JsonArray usersBookMarkGroups = new JsonArray();
                                                for (int j = 0; j < listeUsersFutures.size(); j++) {
                                                    usersBookMarkGroups = (JsonArray) listeUsersFutures.get(j).result();
                                                    for (Object group : usersBookMarkGroups) {
                                                        share.getJsonArray("groups").getJsonObject(listeRankGroup.get(j)).getJsonArray("users").addAll(((JsonObject) group).getJsonArray("users"));
                                                        JsonArray duplicatesUsers = share.getJsonArray("groups").getJsonObject(listeRankGroup.get(j)).getJsonArray("users").copy();
                                                        share.getJsonArray("groups").getJsonObject(listeRankGroup.get(j)).remove("users");
                                                        JsonArray usersArray = new JsonArray();
                                                        for (Object userToShare : duplicatesUsers) {
                                                            boolean findDuplicate = false;
                                                            for(int k = 0; k < usersArray.size(); k++){
                                                                if (((JsonObject)userToShare).getValue("id").toString().equals(usersArray.getJsonObject(k).getValue("id").toString()))
                                                                    findDuplicate = true;
                                                            }
                                                            if(!findDuplicate){
                                                                if(!isNull(((JsonObject)userToShare).getValue("firstname")))
                                                                    usersArray.add(userToShare);
                                                            }
                                                        }
                                                        share.getJsonArray("groups").getJsonObject(listeRankGroup.get(j)).put("users",usersArray);
                                                    }
                                                }
                                                sendRightShare(share, request);
                                            } else {
                                                badRequest(request, event.cause().getMessage());
                                            }
                                        });
                                    } else {
                                        sendRightShare(share, request);
                                    }

                                } else {
                                    badRequest(request, event.cause().getMessage());
                                }
                            });
                        } else {
                            log.error("User not found.");
                            unauthorized(request);
                        }
                    }
                });
            }
        });
    }

    private void sendRightShare(JsonObject share, HttpServerRequest request){;
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(UserInfos user) {
                JsonArray zimbraEmail = new JsonArray();
                if (share.getJsonArray("users") != null) {
                    for (int i = 0; i < share.getJsonArray("users").size(); i++) {
                        zimbraEmail.add(share.getJsonArray("users").getJsonObject(i).getString("id"));
                    }
                }
                if (share.getJsonArray("groups") != null) {
                    for (int i = 0; i < share.getJsonArray("groups").size(); i++) {
                        for (int j = 0; j < share.getJsonArray("groups").getJsonObject(i).getJsonArray("users").size(); j++) {
                            zimbraEmail.add(share.getJsonArray("groups").getJsonObject(i).getJsonArray("users").getJsonObject(j).getString("id"));
                        }
                    }
                }
                moodleEventBus.getZimbraEmail(zimbraEmail, event -> {
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

                    JsonArray groupsUsers = share.getJsonArray("groups");
                    if(groupsUsers != null) {
                        for (int i = 0; i < groupsUsers.size(); i++) {
                            JsonArray usersInGroup = groupsUsers.getJsonObject(i).getJsonArray("users");
                            if(usersInGroup != null) {
                                for (int j = 0; j < usersInGroup.size(); j++) {
                                    JsonObject userInGroupe = usersInGroup.getJsonObject(j);
                                    userInGroupe.put("email", zimbraMap.get(userInGroupe.getString("id")));
                                }
                            }
                        }
                    }
                    JsonObject shareSend = new JsonObject();
                    shareSend.put("parameters", share)
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
                        log.error("Invalid moodle web service sending right share uri",e);
                    }
                    if (moodleUri != null) {
                        final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx);
                        final String moodleUrl = moodleUri.toString();
                        HttpClientHelper.webServiceMoodlePost(shareSend, moodleUrl, httpClient, responseIsSent, new Handler<Either<String, Buffer>>() {
                            @Override
                            public void handle(Either<String, Buffer> event) {
                                if (event.isRight()) {
                                    log.info("Cours partagé");
                                    request.response()
                                            .setStatusCode(200)
                                            .end();
                                } else {
                                    log.error("Share service didn't work");
                                    unauthorized(request);
                                }
                            }
                        }, true);
                    }
                });
            }
        });
    }

    @Post("/course/duplicate")
    @ApiDoc("Duplicate courses")
    @SecuredAction(workflow_duplicate)
    public void duplicate (final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "duplicate", new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject duplicateCourse) {
                UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
                    @Override
                    public void handle(UserInfos user) {
                        JsonArray courseId = duplicateCourse.getJsonArray("coursesId");
                        JsonObject courseToDuplicate = new JsonObject();
                        courseToDuplicate.put("folderid", duplicateCourse.getInteger("folderId"));
                        courseToDuplicate.put("status", WAITING);
                        courseToDuplicate.put("userId", user.getUserId());
                        for (int i = 0; i < courseId.size(); i++) {
                            courseToDuplicate.put("courseid", courseId.getValue(i));
                            moodleWebService.insertDuplicateTable(courseToDuplicate, new Handler<Either<String, JsonObject>>() {
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
                    }
                });
            }
        });
    }

    @Get("/duplicateCourses")
    @ApiDoc("Get duplicate courses")
    @SecuredAction(workflow_duplicate)
    public void getDuplicateCourses (final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(UserInfos user) {
                if (user != null) {
                    moodleWebService.deleteFinishedCoursesDuplicate(new Handler<Either<String, JsonObject>>() {
                        @Override
                        public void handle(Either<String, JsonObject> event) {
                            if (event.isRight()) {
                                moodleWebService.getCourseToDuplicate(user.getUserId(), arrayResponseHandler(request));
                            } else {
                                log.error("Problem to delete finished duplicate courses !");
                                renderError(request);
                            }
                        }
                    });
                } else {
                    log.error("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    @Delete("/courseDuplicate/:id")
    @ApiDoc("delete a duplicateFailed folder")
    public void deleteDuplicateCourse(final HttpServerRequest request) {
	    UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
	        @Override
            public void handle(UserInfos user) {
	            if (user != null) {
	                final String status = FINISHED;
                    Integer id = Integer.parseInt(request.params().get("id"));
                    moodleWebService.updateStatusCourseToDuplicate(status, id, 1, new Handler<Either<String, JsonObject>>() {
                        @Override
                        public void handle(Either<String, JsonObject> event) {
                            if (event.isRight()) {
                                moodleWebService.deleteFinishedCoursesDuplicate(new Handler<Either<String, JsonObject>>() {
                                    @Override
                                    public void handle(Either<String, JsonObject> event) {
                                        if (event.isRight()) {
                                            request.response()
                                                    .setStatusCode(200)
                                                    .end();
                                        } else {
                                            log.error("Problem to delete finished duplicate courses !");
                                            renderError(request);
                                        }
                                    }
                                });
                            } else {
                                log.error("Update Duplicate course database didn't work!");
                                renderError(request);
                            }
                        }
                    });
	            } else {
	                log.error("User not found in session.");
	                unauthorized(request);
	            }
	        }
	    });
    }

    @Post("/course/duplicate/response")
    @ApiDoc("Duplicate courses")
    public void getMoodleResponse (HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "duplicateResponse", new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject duplicateResponse) {
                UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
                    @Override
                    public void handle(UserInfos user) {
                        switch(duplicateResponse.getString("status")) {
                            case "pending":
                                moodleWebService.updateStatusCourseToDuplicate(PENDING,
                                        Integer.parseInt(duplicateResponse.getString("ident")), 1, new Handler<Either<String, JsonObject>>() {
                                            @Override
                                            public void handle(Either<String, JsonObject> event) {
                                                if (event.isRight()) {
                                                    request.response()
                                                            .setStatusCode(200)
                                                            .end();
                                                } else {
                                                    log.error("Cannot update the status of the course in duplication table");
                                                    unauthorized(request);
                                                }
                                            }
                                        });
                                break;
                            case "finished":
                                moodleWebService.updateStatusCourseToDuplicate(FINISHED,
                                        Integer.parseInt(duplicateResponse.getString("ident")), 1, new Handler<Either<String, JsonObject>>() {
                                            @Override
                                            public void handle(Either<String, JsonObject> event) {
                                                if (event.isRight()) {
                                                    moodleWebService.getCourseIdToDuplicate(FINISHED, new Handler<Either<String, JsonArray>>() {
                                                        @Override
                                                        public void handle(Either<String, JsonArray> event) {
                                                            JsonObject createCourseDuplicate = new JsonObject();
                                                            createCourseDuplicate.put("userid", event.right().getValue().getJsonObject(0).getString("id_users"));
                                                            createCourseDuplicate.put("folderid", event.right().getValue().getJsonObject(0).getInteger("id_folder"));
                                                            createCourseDuplicate.put("moodleid", Integer.parseInt(duplicateResponse.getString("courseid")));
                                                            moodleWebService.createCourse(createCourseDuplicate, new Handler<Either<String, JsonObject>>() {
                                                                @Override
                                                                public void handle(Either<String, JsonObject> event) {
                                                                    if (event.isRight()) {
                                                                        moodleWebService.deleteFinishedCoursesDuplicate(new Handler<Either<String, JsonObject>>() {
                                                                            @Override
                                                                            public void handle(Either<String, JsonObject> event) {
                                                                                if (event.isRight()) {
                                                                                    request.response()
                                                                                            .setStatusCode(200)
                                                                                            .end();
                                                                                } else {
                                                                                    log.error("Problem to delete finished duplicate courses !");
                                                                                    unauthorized(request);
                                                                                }
                                                                            }
                                                                        });
                                                                    } else {
                                                                        log.error("Problem to insert in course database !");
                                                                        unauthorized(request);
                                                                    }
                                                                }
                                                            });
                                                        }
                                                    });
                                                }
                                            }
                                        });
                                break;
                            case "busy":
                                log.debug("A duplication is already in progress");
                                break;
                            case "error":
                                moodleWebService.getCourseToDuplicate(duplicateResponse.getString("userid"),
                                        new Handler<Either<String, JsonArray>>() {
                                            @Override
                                            public void handle(Either<String, JsonArray> event) {
                                                if (event.isRight()) {
                                                    if (event.right().getValue().getInteger(6) == 3) {
                                                        moodleWebService.deleteFinishedCoursesDuplicate(new Handler<Either<String, JsonObject>>() {
                                                            @Override
                                                            public void handle(Either<String, JsonObject> event) {
                                                                if (event.isRight()) {
                                                                    request.response()
                                                                            .setStatusCode(200)
                                                                            .end();
                                                                } else {
                                                                    log.error("Problem to delete the duplicate course in database !");
                                                                    unauthorized(request);
                                                                }
                                                            }
                                                        });
                                                    } else if (event.right().getValue().getInteger(6) < 3) {
                                                        log.error("Duplication web-service failed !");
                                                        final String status = WAITING;
                                                        Integer id = duplicateResponse.getInteger("id");
                                                        Integer nbrTentatives =  duplicateResponse.getInteger("numberOfTentatives");
                                                        moodleWebService.updateStatusCourseToDuplicate(status, id, nbrTentatives, new Handler<Either<String, JsonObject>>() {
                                                            @Override
                                                            public void handle(Either<String, JsonObject> event) {
                                                                if (event.isRight()) {
                                                                    log.error("Duplication web-service failed");
                                                                    unauthorized(request);
                                                                } else {
                                                                    log.error("Failed to access database");
                                                                    unauthorized(request);
                                                                }
                                                            }
                                                        });
                                                    }
                                                }
                                            }
                                        });
                                break;
                            default:
                                log.error("Failed to read the Moodle response");
                        }
                    }
                });
            }
        });
    }

    public void synchronisationDuplication (final Handler<Either<String, JsonObject>> eitherHandler){
        String status = PENDING;
        moodleWebService.getCourseIdToDuplicate(status, new Handler<Either<String, JsonArray>>() {
            @Override
            public void handle(Either<String, JsonArray> event) {
                if (event.isRight()) {
                    if(event.right().getValue().size() < config.getInteger("numberOfMaxPendingDuplication")) {
                        String status = WAITING;
                        moodleWebService.getCourseIdToDuplicate(status, new Handler<Either<String, JsonArray>>() {
                            @Override
                            public void handle(Either<String, JsonArray> event) {
                                if (event.isRight()) {
                                    if (event.right().getValue().size() != 0) {
                                        JsonObject courseDuplicate = event.right().getValue().getJsonObject(0);
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
                                            log.error("Invalid moodle web service sending demand of duplication uri",e);
                                        }
                                        if (moodleUri != null) {
                                            final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx);
                                            final String moodleUrl = moodleUri.toString() +
                                                    "?wstoken=" + config.getString("wsToken") +
                                                    "&wsfunction=" + WS_POST_DUPLICATECOURSE +
                                                    "&parameters[idnumber]=" + courseToDuplicate.getString("userid") +
                                                    "&parameters[course][0][moodlecourseid]=" + courseToDuplicate.getInteger("courseid") +
                                                    "&parameters[course][0][ident]=" + courseDuplicate.getInteger("id") +
                                                    "&moodlewsrestformat=" + JSON;

                                            JsonObject shareSend = new JsonObject();
                                            shareSend = null;
                                            httpClientHelper.webServiceMoodlePost(shareSend, moodleUrl, httpClient, responseIsSent, new Handler<Either<String, Buffer>>() {
                                                @Override
                                                public void handle(Either<String, Buffer> event) {
                                                    if (event.isRight()) {
                                                        eitherHandler.handle(new Either.Right<>(event.right().getValue().toJsonArray().getJsonObject(0).getJsonArray("courses").getJsonObject(0)));
                                                    } else {
                                                        log.error("Failed to contact Moodle");
                                                        eitherHandler.handle(new Either.Left<>("Failed to contact Moodle"));
                                                    }
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
            }
        });
    }

    public LocalDateTime getDateString(String date){
        return LocalDateTime.parse(date.substring(0, 10) + "T" + date.substring(11));
    }
}
