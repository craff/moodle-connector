package fr.openent.moodle.controllers;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.helper.HttpClientHelper;
import fr.openent.moodle.service.MoodleEventBusService;
import fr.openent.moodle.service.MoodleWebService;
import fr.openent.moodle.service.impl.DefaultMoodleEventBusService;
import fr.openent.moodle.service.impl.DefaultMoodleWebService;
import fr.wseduc.rs.*;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.http.response.DefaultResponseHandler;
import fr.wseduc.webutils.request.RequestUtils;
import fr.wseduc.webutils.security.XssSecuredHttpServerRequest;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static fr.openent.moodle.Moodle.*;
import static fr.wseduc.webutils.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class MoodleController extends ControllerHelper {

	private final MoodleWebService moodleWebService;
	private final MoodleEventBusService moodleEventBusService;
	private final HttpClientHelper httpClientHelper;

    public MoodleController(Vertx vertx, EventBus eb) {
		super();
        this.eb = eb;
		this.moodleWebService = new DefaultMoodleWebService(Moodle.moodleSchema, "course");
		this.moodleEventBusService = new DefaultMoodleEventBusService(Moodle.moodleSchema, "course", eb);
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

    @Post("/folder")
    @ApiDoc("create a folder")
    @SecuredAction("moodle.create")
    public void createFolder(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "folder", new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject folder) {
                UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
                    @Override
                    public void handle(UserInfos user){
                        if (user != null) {
                            folder.put("userId", user.getUserId());
                            folder.put("etabId", user.getStructures().toString().substring(1, 36));
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
	            if (course.getBoolean("type") == true) {
	                course.put("typeNumber", 1);
                } else {
	                course.put("typeNumber", 2);
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
                        moodleEventBusService.getParams(action, new Handler<Either<String, JsonObject>>() {
                            @Override
                            public void handle(Either<String, JsonObject> event) {
                                if (event.isRight()) {
                                    final AtomicBoolean responseIsSent = new AtomicBoolean(false);

                                    URI moodleUri = null;
                                    try {
                                        final String service = config.getString("address_moodle");
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
                                                "&parameters[username]=" + user.getLogin() +
                                                "&parameters[idnumber]=" + user.getUserId() +
                                                "&parameters[email]=" + ((JsonObject) ((Either.Right) event).getValue()).getString("email") +
                                                "&parameters[firstname]=" + user.getFirstName() +
                                                "&parameters[lastname]=" + user.getLastName() +
                                                "&parameters[fullname]=" + course.getString("fullname") +
                                                "&parameters[shortname]=" + course.getString("shortname") +
                                                "&parameters[categoryid]=" + course.getInteger("categoryid") +
//                                                "&parameters[courseidnumber]=" + course.getInteger("courseidnumber") +
                                                "&parameters[sumamry]=" + course.getString("description") +
                                                "&parameters[imageurl]=" + "https://medias.liberation.fr/photo/552903--.jpg" +
                                                "&parameters[coursetype]=" + course.getInteger("typeNumber") +
                                                "&parameters[activity]=" + course.getString("typeA") +
                                                "&moodlewsrestformat=" + JSON;
                                        httpClientHelper.webServiceMoodlePost(moodleUrl, httpClient, responseIsSent, new Handler<Either<String, Buffer>>() {
                                            @Override
                                            public void handle(Either<String, Buffer> event) {
                                                if (event.isRight()) {
                                                    JsonObject object = new JsonObject(event.right().getValue().toString().substring(1, event.right().toString().length() + 11));
                                                    course.put("moodleid", object.getValue("courseid"));
                                                    course.put("userid", user.getUserId());
                                                    moodleWebService.create(course, defaultResponseHandler(request));
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
                    log.debug("User not found in session.");
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
    @ApiDoc("Get cours in database by folder id")
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

    @Get("/courses/:id")
    @ApiDoc("Get cours in database by folder id")
    //@SecuredAction("moodle.list")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void listCouresByFolder(final HttpServerRequest request){
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(UserInfos user) {
                if(user!=null){
                    long id_folder =Long.parseLong(request.params().get("id"));
                    Handler<Either<String, JsonArray>> handler = arrayResponseHandler(request);
                    moodleWebService.getCoursInEnt(id_folder, user.getUserId(), new Handler<Either<String, JsonArray>>() {
                        @Override
                        public void handle(Either<String, JsonArray> stringJsonArrayEither) {
                            if(stringJsonArrayEither.isRight()){
                                final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx);
                                final String moodleUrl = config.getString("address_moodle") +
                                        "?wstoken=" + WSTOKEN +
                                        "&wsfunction=" + WS_GET_USERCOURSES +
                                        "&parameters[userid]=" + user.getUserId() +
                                        "&moodlewsrestformat=" + JSON;
                                final AtomicBoolean responseIsSent = new AtomicBoolean(false);
                                final HttpClientRequest httpClientRequest = httpClient.getAbs(moodleUrl, new Handler<HttpClientResponse>() {
                                    @Override
                                    public void handle(HttpClientResponse response) {
                                        if (response.statusCode() == 200) {
                                            final Buffer buff = Buffer.buffer();
                                            response.handler(new Handler<Buffer>() {
                                                @Override
                                                public void handle(Buffer event) {
                                                    buff.appendBuffer(event);
                                                    JsonArray mydata=new JsonArray();
                                                    JsonArray object = new JsonArray(buff.toString().substring(15, buff.toString().length()-21));

                                                    for(int i=0;i<object.size();i++){
                                                        JsonObject  o=object.getJsonObject(i);
                                                        if(moodleWebService.getValueMoodleIdinEnt(o.getInteger("courseid"),stringJsonArrayEither.right().getValue())){
                                                            mydata.add(o);
                                                        }
                                                    }
                                                    Renders.renderJson(request, mydata);
                                                }
                                            });
                                            response.endHandler(new Handler<Void>() {
                                                @Override
                                                public void handle(Void end) {
                                                    handle(end);
                                                    if (!responseIsSent.getAndSet(true)) {
                                                        httpClient.close();
                                                    }
                                                }
                                            });

                                        }else{
                                            log.debug(response.statusMessage());
                                            response.bodyHandler(new Handler<Buffer>() {
                                                @Override
                                                public void handle(Buffer event) {
                                                    log.debug("Returning body after PT CALL : " +  moodleUrl + ", Returning body : " + event.toString("UTF-8"));
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
                                httpClientRequest.setTimeout(20001);
                                //Typically an unresolved Address, a timeout about connection or response
                                httpClientRequest.exceptionHandler(new Handler<Throwable>() {
                                    @Override
                                    public void handle(Throwable event) {
                                        log.debug(event.getMessage(), event);
                                        if (!responseIsSent.getAndSet(true)) {
                                            handle(event);
                                            httpClient.close();
                                        }
                                    }
                                }).end();
                            }else{
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
                        public void handle(Either<String, JsonArray> stringJsonArrayEither) {
                            if(stringJsonArrayEither.isRight()){
                                final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx);
                                final String moodleUrl = config.getString("address_moodle") +
                                        "?wstoken=" + WSTOKEN +
                                        "&wsfunction=" + WS_GET_USERCOURSES +
                                        "&parameters[userid]=" + user.getUserId() +
                                        "&moodlewsrestformat=" + JSON;
                                final AtomicBoolean responseIsSent = new AtomicBoolean(false);
                                final HttpClientRequest httpClientRequest = httpClient.getAbs(moodleUrl, new Handler<HttpClientResponse>() {
                                    @Override
                                    public void handle(HttpClientResponse response) {
                                        if (response.statusCode() == 200) {
                                            final Buffer buff = Buffer.buffer();
                                            response.handler(new Handler<Buffer>() {
                                                @Override
                                                public void handle(Buffer event) {
                                                    buff.appendBuffer(event);
                                                    JsonArray mydata=new JsonArray();
                                                    JsonArray object = new JsonArray(buff.toString().substring(15, buff.toString().length()-21));
                                                    for(int i=0;i<object.size();i++){
                                                        JsonObject  o=object.getJsonObject(i);
                                                        if(moodleWebService.getValueMoodleIdinEnt(o.getInteger("courseid"),stringJsonArrayEither.right().getValue())){
                                                                mydata.add(o);
                                                        }

                                                    }
                                                    List<JsonObject> listObject=mydata.getList();
                                                    Collections.sort(listObject, new Comparator<JsonObject>() {
                                                        public int compare(JsonObject ob, JsonObject ob1) {
                                                            LocalDateTime ldt1=getDateString(ob.getString("date"));
                                                            LocalDateTime ldt2=getDateString(ob1.getString("date"));
                                                            return (ldt1.compareTo(ldt2)>=0) ? 1:-1;
                                                        }
                                                    });
                                                    Collections.reverse(listObject);
                                                    //listObject.stream().skip(0).limit(4).collect(Collectors.toList()).toString()
                                                    List<JsonObject> ob=new ArrayList<>();
                                                    for(int i=0;i<4;i++){
                                                        ob.add(listObject.get(i));
                                                    }
                                                    mydata=new JsonArray(ob.toString());
                                                    Renders.renderJson(request, mydata);
                                                }
                                            });
                                            response.endHandler(new Handler<Void>() {
                                                @Override
                                                public void handle(Void end) {
                                                    handle(end);
                                                    if (!responseIsSent.getAndSet(true)) {
                                                        httpClient.close();
                                                    }
                                                }
                                            });

                                        }else{
                                            log.debug(response.statusMessage());
                                            response.bodyHandler(new Handler<Buffer>() {
                                                @Override
                                                public void handle(Buffer event) {
                                                    log.debug("Returning body after PT CALL : " +  moodleUrl + ", Returning body : " + event.toString("UTF-8"));
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
                                httpClientRequest.setTimeout(20001);
                                //Typically an unresolved Address, a timeout about connection or response
                                httpClientRequest.exceptionHandler(new Handler<Throwable>() {
                                    @Override
                                    public void handle(Throwable event) {
                                        log.debug(event.getMessage(), event);
                                        if (!responseIsSent.getAndSet(true)) {
                                            handle(event);
                                            httpClient.close();
                                        }
                                    }
                                }).end();
                            }else{
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
    @Get("/users/coursesAndShared")
    @ApiDoc("Get cours in database by folder id")
    //@SecuredAction("moodle.list")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void listCouresSharedByuser(final HttpServerRequest request){
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(UserInfos user) {
                if(user!=null){
                    Handler<Either<String, JsonArray>> handler = arrayResponseHandler(request);
                    moodleWebService.getCoursesByUserInEnt(user.getUserId(), new Handler<Either<String, JsonArray>>() {
                        @Override
                        public void handle(Either<String, JsonArray> stringJsonArrayEither) {
                            if(stringJsonArrayEither.isRight()){
                                final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx);
                                final String moodleUrl = config.getString("address_moodle") +
                                        "?wstoken=" + WSTOKEN +
                                        "&wsfunction=" + WS_GET_USERCOURSES +
                                        "&parameters[userid]=" + user.getUserId() +
                                        "&moodlewsrestformat=" + JSON;
                                final AtomicBoolean responseIsSent = new AtomicBoolean(false);
                                final HttpClientRequest httpClientRequest = httpClient.getAbs(moodleUrl, new Handler<HttpClientResponse>() {
                                    @Override
                                    public void handle(HttpClientResponse response) {
                                        if (response.statusCode() == 200) {
                                            final Buffer buff = Buffer.buffer();
                                            response.handler(new Handler<Buffer>() {
                                                @Override
                                                public void handle(Buffer event) {
                                                    buff.appendBuffer(event);
                                                    JsonArray mydata=new JsonArray();
                                                    JsonArray object = new JsonArray(buff.toString().substring(15, buff.toString().length()-21));

                                                    for(int i=0;i<object.size();i++){
                                                        JsonObject  o=object.getJsonObject(i);
                                                        if(!moodleWebService.getValueMoodleIdinEnt(o.getInteger("courseid"),stringJsonArrayEither.right().getValue())){
                                                            JsonArray obj=o.getJsonArray("auteur");
                                                            if(!obj.getJsonObject(0).getString("entidnumber").equals(user.getUserId())){
                                                                mydata.add(o);
                                                            }
                                                        }
                                                    }
                                                    Renders.renderJson(request, mydata);
                                                }
                                            });
                                            response.endHandler(new Handler<Void>() {
                                                @Override
                                                public void handle(Void end) {
                                                    handle(end);
                                                    if (!responseIsSent.getAndSet(true)) {
                                                        httpClient.close();
                                                    }
                                                }
                                            });

                                        }else{
                                            log.debug(response.statusMessage());
                                            response.bodyHandler(new Handler<Buffer>() {
                                                @Override
                                                public void handle(Buffer event) {
                                                    log.debug("Returning body after PT CALL : " +  moodleUrl + ", Returning body : " + event.toString("UTF-8"));
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
                                httpClientRequest.setTimeout(20001);
                                //Typically an unresolved Address, a timeout about connection or response
                                httpClientRequest.exceptionHandler(new Handler<Throwable>() {
                                    @Override
                                    public void handle(Throwable event) {
                                        log.debug(event.getMessage(), event);
                                        if (!responseIsSent.getAndSet(true)) {
                                            handle(event);
                                            httpClient.close();
                                        }
                                    }
                                }).end();
                            }else{
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

	@Delete("/course/:id")
    @ApiDoc("Delete a course")
    @SecuredAction("moodle.delete")
    public void delete (final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(UserInfos user) {
                JsonObject course = new JsonObject();
                course.put("courseids", 111);
                final AtomicBoolean responseIsSent = new AtomicBoolean(false);
                URI moodleDeleteUri = null;
                try {
                    final String service = config.getString("address_moodle");
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
                            "&courseids[0]=" + course.getInteger("courseids") +
                            "&moodlewsrestformat=" + JSON;
                    httpClientHelper.webServiceMoodlePost(moodleDeleteUrl, httpClient, responseIsSent, new Handler<Either<String, Buffer>> () {
                        @Override
                        public void handle(Either<String, Buffer> event) {
                            if (event.isRight()) {
                                moodleWebService.delete(course, defaultResponseHandler(request));
                            } else {
                                log.debug("Post service failed");
                            }
                        }
                    });
                }
            }
        });
	}
    public LocalDateTime getDateString(String date){
        return LocalDateTime.parse(date.substring(0,10)+"T"+date.substring(11));
    }
}

