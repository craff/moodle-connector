package fr.openent.moodle.controllers;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.security.canShareResourceFilter;
import fr.openent.moodle.helper.HttpClientHelper;
import fr.openent.moodle.service.getShareProcessingService;
import fr.openent.moodle.service.impl.DefaultGetShareProcessingService;
import fr.openent.moodle.service.impl.DefaultMoodleService;
import fr.openent.moodle.service.impl.DefaultPostShareProcessingService;
import fr.openent.moodle.service.moodleService;
import fr.openent.moodle.service.postShareProcessingService;
import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.http.Renders;
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
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.notification.TimelineHelper;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.http.RouteMatcher;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static fr.openent.moodle.Moodle.*;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class ShareController extends ControllerHelper {

    private final getShareProcessingService getShareProcessingService;
    private final postShareProcessingService postShareProcessingService;
    private final moodleService moodleService;

    private final String userMail;

    private final TimelineHelper timelineHelper;


    @Override
    public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
                     Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);
    }

    public ShareController(EventBus eb, TimelineHelper timelineHelper) {
        super();
        this.eb = eb;

        this.getShareProcessingService = new DefaultGetShareProcessingService();
        this.postShareProcessingService = new DefaultPostShareProcessingService();
        this.moodleService = new DefaultMoodleService();

        //todo remove mail constant and add mail from zimbra, ent ...
        this.userMail = Moodle.moodleConfig.getString("userMail");

        this.timelineHelper = timelineHelper;
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
        shareService.shareInfos(user.getUserId(), request.getParam("id"), I18n.acceptLanguage(request),
                request.params().get("search"), event -> {
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
    private void generateShareJson(HttpServerRequest request, Handler<Either<String, JsonObject>> handler, UserInfos user,
                                   JsonObject shareJsonInfosFinal, JsonArray usersEnrolments) {
        JsonObject checkedInherited = new JsonObject();
        shareJsonInfosFinal.getJsonObject("users").put("checkedInherited", checkedInherited);
        JsonObject userEnrolmentsArray = usersEnrolments.getJsonObject(0).getJsonArray("enrolments").getJsonObject(0);
        JsonArray rightToAdd = new JsonArray().add(MOODLE_READ).add(MOODLE_CONTRIB).add(MOODLE_MANAGER);

        if (!usersEnrolments.isEmpty() && !shareJsonInfosFinal.isEmpty()) {
            getShareProcessingService.shareTreatmentForGroups(userEnrolmentsArray, shareJsonInfosFinal, request, rightToAdd,
                    groupsTreatmentEvent -> {
                if (groupsTreatmentEvent.isRight()) {
                    log.info("Groups treatment OK");
                } else {
                    log.error("Groups treatment KO");
                }
            });

            getShareProcessingService.shareTreatmentForUsers(userEnrolmentsArray, shareJsonInfosFinal, user, rightToAdd,
                    usersTreatmentEvent -> {
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
                            enrolNotify(event.right().getValue().toJsonArray().getJsonObject(0)
                                    .getJsonArray("response").getJsonObject(0), user);
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
}
