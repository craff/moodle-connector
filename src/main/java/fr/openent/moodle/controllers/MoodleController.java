package fr.openent.moodle.controllers;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.helper.HttpClientHelper;
import fr.openent.moodle.service.MoodleEventBusService;
import fr.openent.moodle.service.MoodleWebService;
import fr.openent.moodle.service.impl.DefaultMoodleEventBusService;
import fr.openent.moodle.service.impl.DefaultMoodleWebService;
import fr.wseduc.rs.*;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class MoodleController extends ControllerHelper {

	private final MoodleWebService moodleWebService;
	private final MoodleEventBusService moodleEventBusService;

    public MoodleController(Vertx vertx, EventBus eb) {
		super();
        this.eb = eb;
		this.moodleWebService = new DefaultMoodleWebService(Moodle.moodleSchema, "course");
		this.moodleEventBusService = new DefaultMoodleEventBusService(Moodle.moodleSchema, "course", eb);
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
                JsonObject action = new JsonObject();
                action.put("action", "getUserInfos").put("userId", course.getString("idnumber"));
                moodleEventBusService.getEmail(action, new Handler<Either<String, JsonObject>>() {
                    @Override
                    public void handle(Either<String, JsonObject> email) {
                        if (email.isRight()){
                            course.put("email", email.right().getValue().getString("email"));
                            course.put("wstoken", "df92b3978e2b958e0335b2f4df505977");
                            course.put("wsfunction", "local_entcgi_services_createcourse");
                            course.put("address", "https://moodle-dev.preprod-ent.fr/webservice/rest/server.php");
                            final AtomicBoolean responseIsSent = new AtomicBoolean(false);

                            URI moodleUri = null;
                            try {
                                final String service = course.getString("address", "");
                                final String urlSeparator = service.endsWith("/")  ? "" : "/";
                                moodleUri = new URI(service + urlSeparator);
                            } catch (URISyntaxException e) {
                                log.debug("Invalid moodle web service uri", e);
                            }
                            if (moodleUri != null) {
                                final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx);
                                final String moodleUrl = moodleUri.toString() +
                                        "?wstoken=" + course.getString("wstoken") +
                                        "&wsfunction" + course.getString("wsfunction") +
                                        "&parameters[username]=" + course.getString("username") +
                                        "&parameters[idnumber]=" + course.getString("idnumber") +
                                        "&parameters[email]=" + course.getString("email") +
                                        "&parameters[firstname]=" + course.getString("firstname") +
                                        "&parameters[lastname]=" + course.getString("lastname") +
                                        "&parameters[fullname]=" + course.getString("fullname") +
                                        "&parameters[shortname]=" + course.getString("shortname") +
                                        "&parameters[categoryid]=" + course.getInteger("categoryid") +
                                        "&moodlewsrestformat=" + "json";

                                final HttpClientRequest httpClientRequest = httpClient.post(moodleUrl, new Handler<HttpClientResponse>() {
                                    @Override
                                    public void handle(HttpClientResponse response) {
                                        System.out.println("essai");
                                        if (response.statusCode() == 200) {
                                            final Buffer buff = Buffer.buffer();
                                            response.handler(new Handler<Buffer>() {
                                                @Override
                                                public void handle(Buffer event) {
                                                    moodleWebService.create(course, defaultResponseHandler(request));
                                                    buff.appendBuffer(event);
                                                }
                                            });
                                            response.endHandler(new Handler<Void>() {
                                                @Override
                                                public void handle(Void end) {
                                                    final String json = buff.toString();
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
                                        System.out.println("essai");
                                        if (!responseIsSent.getAndSet(true)) {
                                            handle(event);
                                            httpClient.close();
                                        }
                                    }
                                }).end();
                            }
                        } else {
                            handle(new Either.Left<>("email recuperation failed"));
                        }
                    }
                });
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
                String id = request.params().get("id");
                if (user != null) {
                    moodleWebService.delete(id, defaultResponseHandler(request));
                } else {
                    unauthorized(request);
                }
            }
        });
    }

    private HttpClient generateHttpClient(URI uri) {
        HttpClientOptions options = new HttpClientOptions()
                .setDefaultHost(uri.getHost())
                .setDefaultPort((uri.getPort() > 0) ? uri.getPort() : ("https".equals(uri.getScheme()) ? 443 : 80))
                .setVerifyHost(false)
                .setTrustAll(true)
                .setSsl("https".equals(uri.getScheme()))
                .setKeepAlive(false);
        return vertx.createHttpClient(options);
    }

//	    1)Appel WS eNovation de creation

//		'username' => new external_value(core_user::get_property_type('username'), 'Username policy is defined in Moodle security config.'),
//		'idnumber' =>new external_value(core_user::get_property_type('idnumber'),'An arbitrary ID code number perhaps from the ENT institution', VALUE_DEFAULT, ''),
//		'email' => new external_value(core_user::get_property_type('email'), 'A valid and unique email address'),
//		'firstname' => new external_value(core_user::get_property_type('firstname'), 'The first name(s) of the user'),
//		'lastname' => new external_value(core_user::get_property_type('lastname'), 'The family name of the user'),
//		'fullname' => new external_value(PARAM_TEXT, 'course full name'),
//		'shortname' => new external_value(PARAM_TEXT, 'course short name'),
//		'categoryid' => new external_value(PARAM_INT, 'category id'),
//      'courseidnumber' => new external_value(PARAM_RAW, 'ENT course id number', VALUE_OPTIONAL),
//      'summary' => new external_value(PARAM_RAW, 'summary description', VALUE_OPTIONAL),
//      'imagebase64' => new external_value(PARAM_URL, 'imagebase64', VALUE_OPTIONAL),

                // 2) Apres le resultat de 1)
                // si statut OK, enregistrer dans notre BDD SQL
                //TODO récupérer les infos du user et du cours depuis la request

                // TODO appeler service pour sauvegarder dans la table cours
                // sinon afficher message erreur

}
