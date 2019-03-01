package fr.openent.moodle.controllers;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.helper.HttpClientHelper;
import fr.openent.moodle.service.MoodleEventBus;
import fr.openent.moodle.service.MoodleWebService;
import fr.openent.moodle.service.impl.DefaultMoodleEventBus;
import fr.openent.moodle.service.impl.DefaultMoodleWebService;
import fr.wseduc.rs.*;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.http.response.DefaultResponseHandler;
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
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static fr.openent.moodle.Moodle.*;
import static fr.wseduc.webutils.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class MoodleController extends ControllerHelper {

	private final MoodleWebService moodleWebService;
	private final MoodleEventBus moodleEventBus;
	private final HttpClientHelper httpClientHelper;

    public MoodleController(Vertx vertx, EventBus eb) {
		super();
        this.eb = eb;
		this.moodleWebService = new DefaultMoodleWebService(Moodle.moodleSchema, "course");
		this.moodleEventBus = new DefaultMoodleEventBus(Moodle.moodleSchema, "course", eb);
		this.httpClientHelper = new HttpClientHelper();
	}

	/**
	 * Displays the home view.
	 * @param request Client request
	 */
	@Get("")
	@SecuredAction("moodle.view")
	public void view(HttpServerRequest request) {
		renderView(request);
	}

    @Put("/folder/move")
    @ApiDoc("move a folder")
    //@SecuredAction("moodle.put")
    public void moveFolder(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "folder", new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject folder) {
                UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
                    @Override
                    public void handle(UserInfos user) {
                        if (user != null) {
                            moodleWebService.moveFolder(folder, defaultResponseHandler(request));
                        } else {
                            log.debug("User not found in session.");
                            unauthorized(request);
                        }
                    }
                });
            }
        });
    }

    @Delete("/folder")
    @ApiDoc("delete a folder")
    //@SecuredAction("moodle.delete")
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
                            log.debug("User not found in session.");
                            unauthorized(request);
                        }
                    }
                });
            }
        });
    }

    @Post("/folder")
    @ApiDoc("create a folder")
    //@SecuredAction("moodle.create")
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
                            log.debug("User not found in session.");
                            unauthorized(request);
                        }
                    }
                });
            }
        });
    }

	@Post("/course")
    @ApiDoc("create a course")
    @SecuredAction("moodle.create")
	public void create(final HttpServerRequest request) {
	    RequestUtils.bodyToJson(request, pathPrefix + "course", new Handler<JsonObject>() {
	        @Override
            public void handle(JsonObject course) {
	            if ("1".equals(course.getString("type"))) {
                    course.put("typeA", "");
                }
                if (course.getString("summary") == null) {
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
                        JsonObject action = new JsonObject();
                        action.put("action", "getUserInfos").put("userId", user.getUserId());
                        moodleEventBus.getParams(action, new Handler<Either<String, JsonObject>>() {
                            @Override
                            public void handle(Either<String, JsonObject> event) {
                                if (event.isRight()) {
                                    final AtomicBoolean responseIsSent = new AtomicBoolean(false);
                                    URI moodleUri = null;
                                    try {
                                        final String service = (config.getString("address_moodle")+ config.getString("ws-path"));
                                        final String urlSeparator = service.endsWith("") ? "" : "/";
                                        moodleUri = new URI(service + urlSeparator);
                                    } catch (URISyntaxException e) {
                                        log.debug("Invalid moodle web service uri", e);
                                    }
                                    if (moodleUri != null) {
                                        final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx);
                                        final String moodleUrl = moodleUri.toString() +
                                                "?wstoken=" + WSTOKEN +
                                                "&wsfunction=" + WS_CREATE_FUNCTION +
                                                "&parameters[username]=" + URLEncoder.encode(user.getLogin()) +
                                                "&parameters[idnumber]=" + URLEncoder.encode(user.getUserId()) +
                                                "&parameters[email]=" +URLEncoder.encode( event.right().getValue().getString("email")) +
                                                "&parameters[firstname]=" + URLEncoder.encode(user.getFirstName()) +
                                                "&parameters[lastname]=" + URLEncoder.encode(user.getLastName()) +
                                                "&parameters[fullname]=" + URLEncoder.encode(course.getString("fullname")) +
                                                "&parameters[shortname]=" + URLEncoder.encode(course.getString("shortname")) +
                                                "&parameters[categoryid]=" + URLEncoder.encode(""+course.getInteger("categoryid")) +
                                                "&parameters[summary]=" + URLEncoder.encode(course.getString("summary")) +
                                                "&parameters[imageurl]=" + URLEncoder.encode(course.getString("imageurl")) +
                                                "&parameters[coursetype]=" + URLEncoder.encode(course.getString("type")) +
                                                "&parameters[activity]=" + URLEncoder.encode(course.getString("typeA")) +
                                                "&moodlewsrestformat=" + JSON;
                                        httpClientHelper.webServiceMoodlePost(moodleUrl, httpClient, responseIsSent, new Handler<Either<String, Buffer>>() {
                                            @Override
                                            public void handle(Either<String, Buffer> event) {
                                                if (event.isRight()) {
                                                    JsonObject object = event.right().getValue().toJsonArray().getJsonObject(0);
                                                    course.put("moodleid", object.getValue("courseid"));
                                                    course.put("userid", user.getUserId());
                                                    moodleWebService.createCourse(course, defaultResponseHandler(request));
                                                } else {
                                                    log.debug("Post service failed");
                                                }
                                            }
                                        });
                                    }
                                } else {
                                    handle(new Either.Left<>("Failed to gets the http params"));
                                }
                            }
                        });
                    }
                });
            }
        });
	}

    @Get("/folder/countsFolders/:id")
    @ApiDoc("Get count fodlers in course by id")
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
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    @Get("/folder/countsCourses/:id")
    @ApiDoc("Get count courses in folder by id")
    //@SecuredAction("moodle.list")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getCountsItemCoursesInFolder(final HttpServerRequest request){
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    long id_folder =Long.parseLong(request.params().get("id"));
                    moodleWebService.countCoursesItemInfolder(id_folder, user.getUserId(), DefaultResponseHandler.defaultResponseHandler(request));
                }
                else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    @Get("/folders")
    @ApiDoc("Get folder in database")
    //@SecuredAction("moodle.list")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void listFoldersAndShared(final HttpServerRequest request){
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    moodleWebService.getFoldersInEnt(user.getUserId(), arrayResponseHandler(request));
                }
                else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }



    @Get("/users/courses")
    @ApiDoc("Get cours by user in database")
    //@SecuredAction("moodle.list")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void listCouresByuser(final HttpServerRequest request){
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(UserInfos user) {
                if(user!=null){
                    Handler<Either<String, JsonArray>> handler = arrayResponseHandler(request);
                    moodleWebService.getCoursesByUserInEnt(user.getUserId(), new Handler<Either<String, JsonArray>>() {
                        @Override
                        public void handle(Either<String, JsonArray> sqlCours) {
                            if(sqlCours.isRight()){
                                final JsonArray sqlCoursArray = sqlCours.right().getValue();
                                final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx);
                                final String moodleUrl = (config.getString("address_moodle")+ config.getString("ws-path")) +
                                        "?wstoken=" + WSTOKEN +
                                        "&wsfunction=" + WS_GET_USERCOURSES +
                                        "&parameters[userid]=" + user.getUserId() +
                                        "&moodlewsrestformat=" + JSON;
                                final AtomicBoolean responseIsSent = new AtomicBoolean(false);
                                Buffer wsResponse = new BufferImpl();
                                final HttpClientRequest httpClientRequest = httpClient.getAbs(moodleUrl, new Handler<HttpClientResponse>() {
                                    @Override
                                    public void handle(HttpClientResponse response) {
                                        if (response.statusCode() == 200) {
                                            response.handler(wsResponse::appendBuffer);
                                            response.endHandler(new Handler<Void>() {
                                                @Override
                                                public void handle(Void end) {
                                                    JsonArray mydata = new JsonArray();
                                                    JsonArray object = new JsonArray(wsResponse);
                                                    JsonArray coursArray = object.getJsonObject(0).getJsonArray("enrolments");

                                                    /*for(int i = 0; i < coursArray.size(); i++){
                                                        JsonObject cours = coursArray.getJsonObject(i);
                                                        if(moodleWebService.getValueMoodleIdinEnt(cours.getInteger("courseid"),sqlCoursArray)){
                                                            mydata.add(cours);
                                                        }
                                                    }*/


                                                    //moodleWebService.getPreferences(user.getUserId(), DefaultResponseHandler.defaultResponseHandler(request));


                                                    Renders.renderJson(request, coursArray);
                                                    handle(end);
                                                    if (!responseIsSent.getAndSet(true)) {
                                                        httpClient.close();
                                                    }
                                                }
                                            });
                                        } else {
                                            log.debug(response.statusMessage());
                                            response.bodyHandler(new Handler<Buffer>() {
                                                @Override
                                                public void handle(Buffer event) {
                                                    log.error("Returning body after PT CALL : " +  moodleUrl + ", Returning body : " + event.toString("UTF-8"));
                                                    if (!responseIsSent.getAndSet(true)) {
                                                        httpClient.close();
                                                    }
                                                }
                                            });
                                            handle(response);
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
                                            handle(event);
                                            httpClient.close();
                                        }
                                    }
                                }).end();
                            } else {
                                handle(new Either.Left<>("Get list in Ent Base failed"));
                            }
                        }
                    });
                } else {
                    unauthorized(request);
                }
            }
        });
    }


	@Delete("/course")
    @ApiDoc("Delete a course")
    @SecuredAction("moodle.delete")
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
                    log.debug("Invalid moodle web service uri", e);
                }
                if (moodleDeleteUri != null) {
                    final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx);
                    final String moodleDeleteUrl = moodleDeleteUri.toString() +
                            "?wstoken=" + WSTOKEN +
                            "&wsfunction=" + WS_DELETE_FUNCTION +
                            idsDeletes +
                            "&moodlewsrestformat=" + JSON;
                    httpClientHelper.webServiceMoodlePost(moodleDeleteUrl, httpClient, responseIsSent, new Handler<Either<String, Buffer>>() {
                        @Override
                        public void handle(Either<String, Buffer> event) {
                            if (event.isRight()) {
                                moodleWebService.deleteCourse(courses, defaultResponseHandler(request));
                            } else {
                                log.debug("Post service failed");
                            }
                        }
                    });
                }
            }
        });
	}

	@Get("/course/:id")
    @ApiDoc("Redirect to Moodle")
    public void redirectToMoodle (HttpServerRequest request){
	    String scope = request.params().contains("scope") ? request.getParam("scope") : "view";
	    redirect(request, config.getString("address_moodle"), "/course/" + scope + ".php?id=" + request.getParam("id"));
    }

    @Get("/course/preferences/:id")
    @ApiDoc("get a course preferences (masked & favorites)")
    public void getPreferences (final HttpServerRequest request) {
                    long id_course =Long.parseLong(request.params().get("id"));
                    moodleWebService.getPreferences(id_course, DefaultResponseHandler.defaultResponseHandler(request));
                }

    @Get("/choices")
    @ApiDoc("get a choice")
    //@SecuredAction("moodle.list")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getChoices (final HttpServerRequest request) {
	    UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
	        @Override
            public void handle(UserInfos user) {
	            if (user != null) {
	                String userId = user.getUserId();
	                moodleWebService.getChoices(userId, arrayResponseHandler(request));
	            } else {
	                log.debug("User not found in session.");
	                unauthorized(request);
	            }
	        }
	    });
	}

    @Put("/choices/:view")
    @ApiDoc("set a choice")
    //@SecuredAction("moodle.list")
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
                            log.debug("User not found in session.");
                            unauthorized(request);
                        }
                    }
                });
            }
        });
    }

    @Put("/course")
    @ApiDoc("Share a course")
    @SecuredAction("moodle.share")
    public void share(final HttpServerRequest request) {
	    RequestUtils.bodyToJson(request, pathPrefix + "share", new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject shareCourse) {
                UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
                    @Override
                    public void handle(UserInfos user) {
                        if (user != null) {
                            Map idUsers = shareCourse.getJsonObject("users").getMap();
                            Map idGroups = shareCourse.getJsonObject("groups").getMap();
                            Map idBookmarks = shareCourse.getJsonObject("bookmarks").getMap();
                            JsonArray usersIds = new JsonArray(new ArrayList(idUsers.keySet()));
                            JsonArray groupsIds = new JsonArray(new ArrayList(idGroups.keySet()));
                            JsonArray bookmarksIds = new JsonArray(new ArrayList(idBookmarks.keySet()));
                            JsonObject share = new JsonObject();
                            moodleWebService.getUsers(usersIds, new Handler<Either<String, JsonArray>>() {
                                @Override
                                public void handle(Either<String, JsonArray> eventUsers) {
                                    if (eventUsers.isRight()) {
                                        share.put("users", eventUsers.right().getValue());
                                        moodleWebService.getGroups(groupsIds, new Handler<Either<String, JsonArray>>() {
                                            @Override
                                            public void handle(Either<String, JsonArray> eventGroups) {
                                                if (eventGroups.isRight()) {
                                                    share.put("groups", eventGroups.right().getValue());
                                                    if(bookmarksIds.size() > 0){
                                                        for (int i = 0; i < bookmarksIds.size(); i++) {
                                                            String sharedBookMarkId = bookmarksIds.getString(i);
                                                            JsonArray usersParamsId = new JsonArray().add(user.getUserId());
                                                            moodleWebService.getSharedBookMark(usersParamsId, sharedBookMarkId, new Handler<Either<String, JsonArray>>(){
                                                                @Override
                                                                public void handle(Either<String, JsonArray> eventBookmarks){
                                                                    if(eventBookmarks.isRight()){
                                                                        share.getJsonArray("groups").add(eventBookmarks.right().getValue().getJsonObject(0).getJsonObject("sharedBookMark").getJsonObject("group"));
                                                                    } else {
                                                                        log.debug("sharedBookMark " + sharedBookMarkId + " not found in Neo4J.");
                                                                    }
                                                                }
                                                            });
                                                        }
                                                    }
                                                }
                                            }
                                        });
                                    }
                                }
                            });
                        } else {
                            log.debug("User or group not found.");
                            unauthorized(request);
                        }
                    }
                });
            }
        });
    }
    @Get("/sharedBookMark")
    @ApiDoc("get a sharedBookMark")
    //@SecuredAction("moodle.list")
    public void getSharedBookMark (final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(UserInfos user) {

                if (user != null) {

                    //.put("userId", user.getUserId());

                } else {
                    log.debug("User " + user + " not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    public LocalDateTime getDateString(String date){
        return LocalDateTime.parse(date.substring(0, 10) + "T" + date.substring(11));
    }
}
