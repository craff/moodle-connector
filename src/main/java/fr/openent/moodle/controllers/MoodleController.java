package fr.openent.moodle.controllers;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.helper.HttpClientHelper;
import fr.openent.moodle.service.MoodleEventBusService;
import fr.openent.moodle.service.MoodleWebService;
import fr.openent.moodle.service.impl.DefaultMoodleEventBusService;
import fr.openent.moodle.service.impl.DefaultMoodleWebService;
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
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

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
                        moodleEventBusService.getParams(action, new Handler<Either<String, JsonObject>>() {
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
                    final String service = (config.getString("address_moodle")+ config.getString("ws-path"));
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
                                moodleWebService.deleteCourse(course, defaultResponseHandler(request));
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

    public LocalDateTime getDateString(String date){
        return LocalDateTime.parse(date.substring(0, 10) + "T" + date.substring(11));
    }
}
