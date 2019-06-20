package fr.openent.moodle.service.impl;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.helper.HttpClientHelper;
import fr.openent.moodle.service.MoodleEventBus;
import fr.openent.moodle.service.MoodleWebService;
import fr.openent.moodle.utils.SyncCase;
import fr.openent.moodle.utils.Utils;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.neo4j.Neo4jResult;
import org.entcore.common.user.UserUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static fr.openent.moodle.Moodle.*;

public class DefaultSynchService {

    protected static final Logger log = LoggerFactory.getLogger(DefaultSynchService.class);
    private JsonObject config;
    private final Vertx vertx;
    protected EventBus eb;
    private final Neo4j neo4j = Neo4j.getInstance();

    private final MoodleWebService moodleWebService;
    private final MoodleEventBus moodleEventBus;

    private HttpClient httpClient;

    private String baseWsMoodleUrl;

    private Map<String, JsonObject> mapUsersMoodle;
    private Map<String, JsonObject> mapUsersFound;
    private Map<String, JsonObject>[] mapUsersNotFound;
    private JsonArray arrUsersToDelete = new JsonArray();
    private JsonArray arrUsersToEnroll = new JsonArray();

    private AtomicInteger compositeFuturEnded;

    public String acceptLanguage = "fr";

    public DefaultSynchService(EventBus eb, JsonObject config, Vertx vertx) {
        this.eb = eb;
        this.config = config;
        this.vertx = vertx;
        this.moodleWebService = new DefaultMoodleWebService(Moodle.moodleSchema, "course");
        this.moodleEventBus = new DefaultMoodleEventBus(Moodle.moodleSchema, "course", eb);
        baseWsMoodleUrl = baseWsMoodleUrl = (config.getString("address_moodle")+ config.getString("ws-path"));
    }

    public void initSyncUsers() {
        mapUsersMoodle = new HashMap<String, JsonObject>();
        mapUsersFound = new HashMap<String, JsonObject>();
        arrUsersToDelete = new JsonArray();
        arrUsersToEnroll = new JsonArray();
        mapUsersNotFound = new Map[]{new HashMap<String, JsonObject>()};
        compositeFuturEnded = new AtomicInteger(2);
        httpClient = HttpClientHelper.createHttpClient(vertx);
    }


    public void putUsersInMap(Scanner scUsers) {
        while (scUsers.hasNextLine()) {
            String userLine = scUsers.nextLine();
            log.info(userLine);

            String[] values = userLine.split(";");

            try {
                JsonObject jsonUser = new JsonObject();
                jsonUser.put("id", values[2]);
                jsonUser.put("firstname", values[3]);
                jsonUser.put("lastname", values[4]);
                jsonUser.put("email", values[5]);
                mapUsersMoodle.put(values[2], jsonUser);
            } catch (Throwable t) {
                log.warn("Error reading user : " + userLine);
                continue;
            }

        }
    }

    private void endSyncUsers (Handler<Either<String, JsonObject>> handler) {
        int nbcompositeFuturEnded = compositeFuturEnded.decrementAndGet();
        log.info("endSyncUsers : "+ nbcompositeFuturEnded);
        if (nbcompositeFuturEnded == 0) {
            httpClient.close();
            JsonObject result = new JsonObject();
            result.put("status", "ok");
            handler.handle(new Either.Right<>(result));
        }
    }

    public void syncUsers(Scanner scUsers, Handler<Either<String, JsonObject>> handler) {
        log.info("START syncUsers");

        List<Future> listGetFuture = new ArrayList<Future>();
        initSyncUsers();
        putUsersInMap(scUsers);

        // ---------- HANDLERS --------------

        // ---------- delete users --------------

        Handler<Either<String, Buffer>> handlerDeleteUsers = new Handler<Either<String, Buffer>>() {
            @Override
            public void handle(Either<String, Buffer> event) {
                if (event.isRight()) {
                    log.info("END deleting users");
                    endSyncUsers(handler);
                } else {
                    httpClient.close();
                    handler.handle(new Either.Left<>(event.left().getValue()));
                    log.error("Error deleting users", event.left());
                }
            }
        };
        // ---------- END delete users --------------

        // ---------- enrolls users --------------
        Handler<Either<String, Buffer>> handlerEnrollUsers = new Handler<Either<String, Buffer>>() {
            @Override
            public void handle(Either<String, Buffer> event) {
                if (event.isRight()) {
                    log.info("END enrolling user individually");
                    if (arrUsersToDelete.isEmpty()) {
                        String message = "Aucune suppression necessaire";
                        log.info(message);
                        endSyncUsers(handler);
                    } else {
                        // Un fois inscriptions individuelles ok, lancer les suppressions
                        deleteUsers(arrUsersToDelete, handlerDeleteUsers);
                    }
                } else {
                    httpClient.close();
                    handler.handle(new Either.Left<>(event.left().getValue()));
                    log.error("Error enrolling user individually", event.left());
                }

            }
        };
        // ---------- END enrolls users --------------

        // ---------- getCourses for each user deleted --------------
        Handler<AsyncResult<CompositeFuture>> handlerGetCourses = new Handler<AsyncResult<CompositeFuture>>() {
            @Override
            public void handle(AsyncResult<CompositeFuture> eventFuture) {

                if (eventFuture.succeeded()) {
                    log.info("END getting courses");
                    for (Map.Entry<String, JsonObject> entryUser : mapUsersMoodle.entrySet()) {
                        JsonObject jsonUser = entryUser.getValue();
                        log.info(jsonUser.toString());
                        log.info("-------");
                    }

                    // Utilisateurs non retrouvés (supp physiquement)
                    for (Map.Entry<String, JsonObject> entryUser : mapUsersNotFound[0].entrySet()) {
                        JsonObject jsonUser = entryUser.getValue();
                        JsonArray jsonArrayCourses = mapUsersMoodle.get(jsonUser.getString("id")).getJsonArray("courses");

                        identifyUserToDeleteAndEnroll(arrUsersToDelete, arrUsersToEnroll, jsonArrayCourses,
                                jsonUser.getString("id"), SyncCase.SYNC_USER_NOT_FOUND);

                    }

                    // Utilisateurs en instance de suppresion
                    for (Map.Entry<String, JsonObject> entryUser : mapUsersFound.entrySet()) {
                        JsonObject jsonUser = entryUser.getValue();
                        if(jsonUser.getValue("deleteDate") == null) {
                            continue;
                        }
                        JsonArray jsonArrayCourses = mapUsersMoodle.get(jsonUser.getString("id")).getJsonArray("courses");

                        identifyUserToDeleteAndEnroll(arrUsersToDelete, arrUsersToEnroll, jsonArrayCourses,
                                jsonUser.getString("id"), SyncCase.SYNC_USER_FOUND);
                    }

                    if (arrUsersToEnroll.isEmpty()) {
                        String message = "Aucune inscription individuelle necessaire";
                        log.info(message);
                        endSyncUsers(handler);
                    } else {
                        enrollUsersIndivudually(arrUsersToEnroll, handlerEnrollUsers);
                    }
                } else {
                    httpClient.close();
                    handler.handle(new Either.Left<>("Error getting users Future"));
                    log.error("Error getting users Future", eventFuture.cause());
                }

            }
        };
        // ---------- END getCourses for each user deleted --------------


        // ---------- getUsers --------------
        final Handler<Either<String, JsonArray>> getUsersHandler = new Handler<Either<String, JsonArray>>() {
            @Override
            public void handle(Either<String, JsonArray> resultUsers) {
                try {
                    if(resultUsers.isLeft()) {
                        httpClient.close();
                        handler.handle(new Either.Left<>("Error getting users Future"));
                        log.error("Error getting users Future", resultUsers.left());
                    } else {
                        log.info("End getting users");
                        JsonArray usersFromNeo = resultUsers.right().getValue();
                        for (Object userFromNeo: usersFromNeo) {
                            JsonObject jsonUserFromNeo = (JsonObject)userFromNeo;
                            mapUsersFound.put(jsonUserFromNeo.getString("id"), jsonUserFromNeo);
                        }
                        mapUsersNotFound[0] = new HashMap<String, JsonObject>(mapUsersMoodle);
                        mapUsersNotFound[0].keySet().removeAll(mapUsersFound.keySet());

                        for (Map.Entry<String, JsonObject> entryUser : mapUsersFound.entrySet()) {
                            JsonObject jsonUser = entryUser.getValue();
                            if(jsonUser.getValue("deleteDate") != null) {
                                Future getCoursesFuture = Future.future();
                                listGetFuture.add(getCoursesFuture);
                                getCourses(jsonUser, getCoursesFuture);
                            }
                        }

                        JsonArray arrUsersToUpdate = new JsonArray();
                        for (Map.Entry<String, JsonObject> entryUser : mapUsersFound.entrySet()) {
                            JsonObject jsonUserFromNeo = entryUser.getValue();
                            if (!areUsersEquals(jsonUserFromNeo, mapUsersMoodle.get(jsonUserFromNeo.getString("id")))) {
                                arrUsersToUpdate.add(jsonUserFromNeo);
                            }
                        }

                        if (arrUsersToUpdate.isEmpty()) {
                            String message = "Aucune mise à jour necessaire";
                            log.info(message);
                            endSyncUsers(handler);
                        } else {
                            updateUsers(arrUsersToUpdate, new Handler<Either<String, Buffer>>() {
                                @Override
                                public void handle(Either<String, Buffer> event) {
                                    if (event.isRight()) {
                                        log.info("END updating users");
                                        endSyncUsers(handler);
                                    } else {
                                        httpClient.close();
                                        handler.handle(new Either.Left<>(event.left().getValue()));
                                        log.error("Error updating users", event.left());
                                    }

                                }
                            });
                        }

                        if(listGetFuture.isEmpty()) {
                            String message = "Aucun utilisateur supprimé avec des cours";
                            log.info(message);
                            endSyncUsers(handler);
                        } else {
                            CompositeFuture.all(listGetFuture).setHandler(handlerGetCourses);
                        }
                    }
                }catch (Throwable t) {
                    log.error("Erreur getUsersHandler : ", t);
                }
            }
        };
        // ---------- END getUsers --------------

        // ---------- END HANDLERS --------------

        getUsers (mapUsersMoodle.keySet().toArray(), getUsersHandler);

    }


    private void identifyUserToDelete (JsonArray arrUsersToDelete, String idUser, Boolean remove) {
        JsonObject userToDelete = new JsonObject();
        userToDelete.put("id", idUser);
        userToDelete.put("remove", remove);
        arrUsersToDelete.add(userToDelete);
    }

    private void identifyUserToEnroll (JsonArray arrCoursesUserToEnroll, String coursId, String role) {
        // Inscription en tant qu'apprenant individuel au cours :
        JsonObject courseToEnroll = new JsonObject();
        courseToEnroll.put("id", coursId);
        courseToEnroll.put("role", role);
        arrCoursesUserToEnroll.add(courseToEnroll);
    }

    private void identifyUserToDeleteAndEnroll(JsonArray arrUsersToDelete, JsonArray arrUsersToEnroll, JsonArray jsonArrayCourses,
                                               String idUser, SyncCase syncCase) {


        if (jsonArrayCourses != null) {
            JsonObject userToEnroll = new JsonObject();
            userToEnroll.put("id", idUser);

            JsonArray arrCoursesUserToEnroll = new JsonArray();


            if(syncCase.equals(SyncCase.SYNC_USER_FOUND)) {
                if (jsonArrayCourses != null && !jsonArrayCourses.isEmpty()) {
                    for (Object cours : jsonArrayCourses) {
                        JsonObject jsonCours = ((JsonObject) cours);

                        // si professeur (propriétaire ou éditeur) il doit toujours avoir acces à ses cours
                        if (jsonCours.getString("role").equals(ROLE_EDITEUR.toString())) {
                            // Inscription en tant qu'apprenant individuel au cours :
                            identifyUserToEnroll(arrCoursesUserToEnroll, jsonCours.getInteger("courseid").toString(), ROLE_EDITEUR.toString());
                        }

                        // si eleve (apprenant)
                        if (jsonCours.getString("role").equals(ROLE_APPRENANT.toString())) {
                            // Inscription en tant qu'apprenant individuel au cours :
                            identifyUserToEnroll(arrCoursesUserToEnroll, jsonCours.getInteger("courseid").toString(), ROLE_APPRENANT.toString());

                            // Détachement de l’utilisateur de ses cohortes sans le supprimer physiquement
                            identifyUserToDelete(arrUsersToDelete, idUser, false);
                        }
                    }
                }
            } else if(syncCase.equals(SyncCase.SYNC_USER_NOT_FOUND)) {

                if (jsonArrayCourses != null && !jsonArrayCourses.isEmpty()) {
                    for (Object cours : jsonArrayCourses) {
                        JsonObject jsonCours = ((JsonObject) cours);

                        // si auteur
                        if (jsonCours.getString("role").equals(ROLE_AUDITEUR.toString())) {

                            // TODO réattribuer le cours à un admin étab CGI (A préciser)
                            //    récupérer l'ADML dans l'ENT / le créer dans Moodle si besoin
                            //    utiliser web service Moodle  local_entcgi_services_enrolluserscourses pour inscrire l'ADML en tant que propriétaire du cours à la place de l'ancien

                            // Suppression de l’utilisateur (ou rendre inactif si trop coûteux en perf et purge à posteriori) + détachement de ses cohortes :
                            identifyUserToDelete(arrUsersToDelete, idUser, false);
                        }

                        // si editeur ou apprenant
                        if (jsonCours.getString("role").equals(ROLE_EDITEUR.toString())
                                || jsonCours.getString("role").equals(ROLE_APPRENANT.toString())) {

                            // Détachement de l’utilisateur de ses cohortes + supprimer physiquement ?
                            // --> suppression soft dans un premier temps, et purge par la suite
                            identifyUserToDelete(arrUsersToDelete, idUser, false);

                            // TODO gérer cas dernier éditeur : idem que propriétaire
                        }
                    }
                }

            } else if(syncCase.equals(SyncCase.SYNC_GROUP)) {
                if (jsonArrayCourses != null && !jsonArrayCourses.isEmpty()) {
                    for (Object cours : jsonArrayCourses) {
                        JsonObject jsonCours = ((JsonObject) cours);
                        // Inscription en tant qu'apprenant individuel au cours :
                        identifyUserToEnroll(arrCoursesUserToEnroll, jsonCours.getInteger("courseid").toString(),
                                jsonCours.getString("role"));
                    }
                }
            }

            userToEnroll.put("courses", arrCoursesUserToEnroll);
            arrUsersToEnroll.add(userToEnroll);
        }
    }

    private void deleteUsers(JsonArray arrUsersToDelete, Handler<Either<String, Buffer>> handlerDeleteUserse) {
        log.info("START deleting users");
        JsonObject body = new JsonObject();
        body.put("parameters", arrUsersToDelete)
                .put("wstoken", config.getString("wsToken"))
                .put("wsfunction", WS_POST_DELETE_USER)
                .put("moodlewsrestformat", JSON);
        final AtomicBoolean responseIsSent = new AtomicBoolean(false);
        HttpClientHelper.webServiceMoodlePost(body, baseWsMoodleUrl, httpClient, responseIsSent, handlerDeleteUserse, false);
    }

    private void updateUsers(JsonArray arrUsersToUpdate, Handler<Either<String, Buffer>> handlerUpdateUser) {
        log.info("START updating users");
        JsonObject body = new JsonObject();
        body.put("parameters", arrUsersToUpdate)
                .put("wstoken", config.getString("wsToken"))
                .put("wsfunction", WS_POST_CREATE_OR_UPDATE_USER)
                .put("moodlewsrestformat", JSON);
        final AtomicBoolean responseIsSent = new AtomicBoolean(false);
        HttpClientHelper.webServiceMoodlePost(body, baseWsMoodleUrl, httpClient, responseIsSent, handlerUpdateUser, false);
    }

    private void enrollUsersIndivudually(JsonArray arrUsersToEnroll, Handler<Either<String, Buffer>> handlerEnrollUsers) {
        log.info("START enrolling users individually");
        JsonObject body = new JsonObject();
        body.put("parameters", arrUsersToEnroll)
                .put("wstoken", config.getString("wsToken"))
                .put("wsfunction", WS_POST_ENROLL_USERS_COURSES)
                .put("moodlewsrestformat", JSON);
        final AtomicBoolean responseIsSent = new AtomicBoolean(false);
        HttpClientHelper.webServiceMoodlePost(body, baseWsMoodleUrl, httpClient, responseIsSent, handlerEnrollUsers, false);
    }

    private void getCourses(JsonObject jsonUser, Future getCoursesFuture) {
        final String moodleUrl = baseWsMoodleUrl + "?wstoken=" + config.getString("wsToken") +
                "&wsfunction=" + WS_GET_USERCOURSES +
                "&parameters[userid]=" + jsonUser.getString("id") +
                "&moodlewsrestformat=" + JSON;
        final AtomicBoolean responseIsSent = new AtomicBoolean(false);
        Buffer wsResponse = new BufferImpl();
        log.info("Start retrieving courses for user "+jsonUser.getString("id"));
        final HttpClientRequest httpClientRequest = httpClient.getAbs(moodleUrl, new Handler<HttpClientResponse>() {
            @Override
            public void handle(HttpClientResponse response) {
                if (response.statusCode() == 200) {

                    response.handler(wsResponse::appendBuffer);
                    response.endHandler(new Handler<Void>() {
                        @Override
                        public void handle(Void end) {
                            JsonArray object = new JsonArray(wsResponse);
                            JsonArray userCoursesDuplicate = object.getJsonObject(0).getJsonArray("enrolments");
                            JsonArray userCourses = Utils.removeDuplicateCourses(userCoursesDuplicate);

                            jsonUser.put("courses", userCourses);
                            mapUsersMoodle.put(jsonUser.getString("id"), jsonUser);
                            log.info("End retrieving courses for user "+jsonUser.getString("id"));
                            getCoursesFuture.complete();
                        }
                    });
                } else {
                    log.error("Error retrieving courses for user "+jsonUser.getString("id"));
                    log.error("response.statusCode() = "+response.statusCode());
                    getCoursesFuture.complete();
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
                    //renderError(request);
                }
            }
        }).end();

    }

    private boolean areUsersEquals(JsonObject jsonUserFromNeo, JsonObject jsonUserFromMoodle) {
        return jsonUserFromMoodle.getString("id").equals(jsonUserFromNeo.getString("id")) &&
                jsonUserFromMoodle.getString("firstname").equals(jsonUserFromNeo.getString("firstName")) &&
                jsonUserFromMoodle.getString("lastname").equals(jsonUserFromNeo.getString("lastName")) /*&&
                jsonUserFromMoodle.getString("id").equals(jsonUserFromNeo.getString("email"))*/;
    }


    public void getUsers(Object[] idUsers, Handler<Either<String, JsonArray>> handler){

        StringBuilder query = new StringBuilder();

        String RETURNING = " RETURN  u.id as id, u.firstName as firstName, u.lastName as lastName, u.deleteDate as deleteDate ORDER BY lastName, firstName ";

        query.append(" MATCH (u:User) WHERE u.id IN {idUsers} ")
                .append(RETURNING);

        JsonObject params = new JsonObject();
        params.put("idUsers", new fr.wseduc.webutils.collections.JsonArray(Arrays.asList(idUsers)));

        neo4j.execute(query.toString(),params, Neo4jResult.validResultHandler(handler));

    }


    // --------------------------------------- GROUPS ---------------------------------------------------
    private void updateCohorts(JsonArray arrCohortsToUpdate, Handler<Either<String, Buffer>> handlerCohort) {
        log.info("START updating cohorts");
        JsonObject body = new JsonObject();
        body.put("parameters", arrCohortsToUpdate)
                .put("wstoken", config.getString("wsToken"))
                .put("wsfunction", WS_POST_UPDATE_COHORTS)
                .put("moodlewsrestformat", JSON);
        final AtomicBoolean responseIsSent = new AtomicBoolean(false);
        HttpClientHelper.webServiceMoodlePost(body, baseWsMoodleUrl, httpClient, responseIsSent, handlerCohort, false);
    }

    private void deleteCohorts(JsonArray arrCohortsToDelete, Handler<Either<String, Buffer>> handlerCohort) {
        log.info("START deleting cohorts");
        JsonObject body = new JsonObject();
        body.put("parameters", arrCohortsToDelete)
                .put("wstoken", config.getString("wsToken"))
                .put("wsfunction", WS_POST_DELETE_COHORTS)
                .put("moodlewsrestformat", JSON);
        final AtomicBoolean responseIsSent = new AtomicBoolean(false);
        HttpClientHelper.webServiceMoodlePost(body, baseWsMoodleUrl, httpClient, responseIsSent, handlerCohort, false);
    }



    private Map<String, JsonObject> mapCohortsMoodle;
    private Map<String, JsonObject> mapCohortsFound;
    private Map<String, JsonObject> mapCohortsNotFound;
    private JsonArray arrCohortsToDelete = new JsonArray();
    private JsonArray arrCohortsToUpdate = new JsonArray();

    public void initSyncGroups() {
        mapCohortsMoodle = new HashMap<String, JsonObject>();
        mapCohortsFound = new HashMap<String, JsonObject>();
        mapCohortsNotFound = new HashMap<String, JsonObject>();

        mapUsersMoodle = new HashMap<String, JsonObject>();

        arrUsersToDelete = new JsonArray();
        arrUsersToEnroll = new JsonArray();

        JsonArray arrCohortsToDelete = new JsonArray();
        JsonArray arrCohortsToUpdate = new JsonArray();

        compositeFuturEnded = new AtomicInteger(3);
        httpClient = HttpClientHelper.createHttpClient(vertx);
    }


    public void putCohortsInMap(JsonArray jsonArrayCohorts) {
        for (Object objChorts : jsonArrayCohorts) {
            JsonObject jsonCohort = ((JsonObject)objChorts);
            log.info(jsonCohort);

            try {

                // suppression prefixe
                String idCohort = jsonCohort.getString("idnumber");
                idCohort = idCohort.replace("GR_","");
                idCohort = idCohort.replace("SB","");

                // stockage par id (id ENT classe/groupe/sharebookmark)
                mapCohortsMoodle.put(idCohort, jsonCohort);
            } catch (Throwable t) {
                log.warn("Error reading cohort : " + jsonCohort, t);
                continue;
            }

        }
    }


    private void endSyncGroups (Handler<Either<String, JsonObject>> handler) {
        log.info("endSyncGroups : ");
        httpClient.close();
        JsonObject result = new JsonObject();
        result.put("status", "ok");
        handler.handle(new Either.Right<>(result));
    }

    private Handler<Either<String, Buffer>> handlerEnrollGroups;
    private Handler<AsyncResult<CompositeFuture>> handlerUpdateAndDeleteCohorts;

    public void syncGroups(JsonArray jsonArrayCohorts, Handler<Either<String, JsonObject>> handler) {
        log.info("START syncGroups");
        initSyncGroups();
        putCohortsInMap(jsonArrayCohorts);

        //---HANDLERS--
        Future getGroupsFuture = Future.future();
        Handler<Either<String, JsonArray>> getGroupsHandler = new Handler<Either<String, JsonArray>>() {
            @Override
            public void handle(Either<String, JsonArray> resultGroups) {
                try {
                    if(resultGroups.isLeft()) {
                        httpClient.close();
                        handler.handle(new Either.Left<>("Error getting groups"));
                        log.error("Error getting groups", resultGroups.left());
                        getGroupsFuture.fail("Error getting groups");
                    } else {
                        log.info("End getting groups");
                        JsonArray groupsFromNeo = resultGroups.right().getValue();
                        for (Object objGroup : groupsFromNeo) {
                            JsonObject jsonGroup = ((JsonObject)objGroup);

                            // suppression prefix
                            String idGroup = jsonGroup.getString("id").replace("GR_","");
                            jsonGroup.put("id",idGroup);
                            mapCohortsFound.put(idGroup, jsonGroup);
                        }
                        getGroupsFuture.complete();
                    }
                }catch (Throwable t) {
                    log.error("Erreur getGroupsHandler : ", t);
                    getGroupsFuture.fail("Error getting groups");
                }
            }
        };

        Future getSharedBookMarkFuture = Future.future();
        Handler<Either<String, Map<String, JsonObject>>> getSharedBookMarkHandler = new Handler<Either<String, Map<String, JsonObject>>>() {
            @Override
            public void handle(Either<String, Map<String, JsonObject>> resultSharedBookMark) {
                try {
                    if(resultSharedBookMark.isLeft()) {
                        httpClient.close();
                        handler.handle(new Either.Left<>("Error getting sharedBookMarks"));
                        log.error("Error getting groups", resultSharedBookMark.left());
                        getSharedBookMarkFuture.fail("Error getting sharedBookMarks");
                    } else {
                        log.info("End getting sharedBookMarks");
                        Map<String, JsonObject> mapShareBookMarks = resultSharedBookMark.right().getValue();
                        if(mapShareBookMarks != null && !mapShareBookMarks.isEmpty()) {
                            mapCohortsFound.putAll(mapShareBookMarks);
                        }
                        getSharedBookMarkFuture.complete();
                    }
                }catch (Throwable t) {
                    log.error("Erreur getSharedBookMarkHandler : ", t);
                    getSharedBookMarkFuture.fail("Error getting sharedBookMarks");
                }
            }
        };

        Handler<AsyncResult<CompositeFuture>> handlerGetAllGroups = new Handler<AsyncResult<CompositeFuture>>() {
            @Override
            public void handle(AsyncResult<CompositeFuture> compositeFutureAsyncResult) {
                if (compositeFutureAsyncResult.failed()) {
                    httpClient.close();
                    handler.handle(new Either.Left<>("Error getting all groups Future"));
                    log.error("Error getting all groups Future", compositeFutureAsyncResult.cause());
                } else {

                    // groupes / sharebook non retrouvés dans l'ENT
                    mapCohortsNotFound = new HashMap<String, JsonObject>(mapCohortsMoodle);
                    mapCohortsNotFound.keySet().removeAll(mapCohortsFound.keySet());

                    JsonArray usersToEnrrollIndivually = new JsonArray();

                    // Cohortes NON retrouvées ->
                    for (Map.Entry<String, JsonObject> entryCohort : mapCohortsNotFound.entrySet()) {
                        JsonObject jsonCohort = entryCohort.getValue();
                        JsonObject jsonMoodleCohort = mapCohortsMoodle.get(jsonCohort.getString("idnumber"));

                        usersToEnrrollIndivually.addAll(jsonMoodleCohort.getJsonArray("users"));

                        JsonObject cohortToDelete = new JsonObject().put("id", jsonCohort.getString("idnumber"));
                        arrCohortsToDelete.add(cohortToDelete);
                    }


                    // Cohortes retrouvées ->
                    for (Map.Entry<String, JsonObject> entryCohort : mapCohortsFound.entrySet()) {
                        JsonObject jsonCohortNeo = entryCohort.getValue();
                        JsonArray arrUsersNeo = jsonCohortNeo.getJsonArray("users");

                        JsonObject jsonMoodleCohort = mapCohortsMoodle.get(jsonCohortNeo.getString("id"));
                        JsonArray arrUsersMoodle = jsonMoodleCohort.getJsonArray("users");

                        // si plus aucun membre : suppression de la cohorte
                        if (arrUsersNeo == null || arrUsersNeo.isEmpty()) {

                            usersToEnrrollIndivually.addAll(jsonMoodleCohort.getJsonArray("users"));

                            JsonObject cohortToDelete = new JsonObject().put("id", jsonCohortNeo.getString("id"));
                            arrCohortsToDelete.add(cohortToDelete);
                        } else {

                            // identification changements
                            UserUtils.groupDisplayName(jsonCohortNeo, acceptLanguage);
                            boolean hasChangeName = !jsonCohortNeo.getString("name").equals(jsonMoodleCohort.getString("name"));
                            JsonObject jsonCohorteWithUpdate = null;
                            if (hasChangeName) {
                                jsonCohorteWithUpdate = new JsonObject();
                                jsonCohorteWithUpdate.put("id", jsonCohortNeo.getString("id"));
                                jsonCohorteWithUpdate.put("newname", jsonCohortNeo.getString("name"));
                            }

                            // identification des nouveaux utilisateurs dans la cohorte
                            for (Object objUserNeo : arrUsersNeo) {
                                JsonObject jsonUserNeo = ((JsonObject) objUserNeo);
                                boolean exist = arrUsersMoodle.stream().filter(u -> u.equals(jsonUserNeo.getString("id"))).count() > 0;
                                if(!exist) {
                                    if(jsonCohorteWithUpdate == null) {
                                        jsonCohorteWithUpdate = new JsonObject();

                                        JsonArray useradded = jsonCohorteWithUpdate.getJsonArray("useradded");

                                        if(useradded == null) {
                                            useradded = new JsonArray();
                                        }

                                        useradded.add(jsonUserNeo);
                                        jsonCohorteWithUpdate.put("useradded", useradded);
                                    }
                                }
                            }

                            // identification des utilisateurs supprimés de la cohorte
                            // pour ces utilisateurs on les inscriras individuellement à leurs cours dans la suite
                            //de l'algo
                            for (Object objUserMoodle : arrUsersMoodle) {
                                String idUserMoodle = ((String) objUserMoodle);
                                boolean exist = arrUsersNeo.stream().filter(u -> ((JsonObject)u).getString("id").equals(idUserMoodle)).count() > 0;
                                if(!exist) {
                                    if(jsonCohorteWithUpdate == null) {
                                        jsonCohorteWithUpdate = new JsonObject();

                                        JsonArray userdeleted = jsonCohorteWithUpdate.getJsonArray("userdeleted");

                                        if(userdeleted == null) {
                                            userdeleted = new JsonArray();
                                        }

                                        JsonObject jsonUserMoodle = new JsonObject();
                                        jsonUserMoodle.put("id", idUserMoodle);
                                        userdeleted.add(jsonUserMoodle);
                                        jsonCohorteWithUpdate.put("userdeleted", userdeleted);

                                        usersToEnrrollIndivually.add(jsonUserMoodle);
                                    }
                                }
                            }

                            if(jsonCohorteWithUpdate != null) {
                                arrCohortsToUpdate.add(jsonCohorteWithUpdate);
                            }
                        }
                    }

                    // Inscription individuel de tous les utilisateurs à leurs cours
                    // s'ils sont identifiés comme sortant d'une cohorte
                    getUsersCoursesAndEnroll(usersToEnrrollIndivually, handler);
                }
            }
        };

        handlerEnrollGroups = new Handler<Either<String, Buffer>>() {
            @Override
            public void handle(Either<String, Buffer> event) {
                if (event.isRight()) {
                    log.info("END enrolling users individually");
                    if (arrCohortsToUpdate.isEmpty() && arrCohortsToDelete.isEmpty()) {
                        String message = "Aucune cohorte à update/delete";
                        log.info(message);
                        endSyncGroups(handler);
                    } else {
                        Future updateCohortsFuture = Future.future();
                        Future deleteCohortsFuture = Future.future();
                        List<Future> listFuture = new ArrayList<>();

                        if(!arrCohortsToUpdate.isEmpty()) {
                            listFuture.add(updateCohortsFuture);
                        }

                        if(!arrCohortsToDelete.isEmpty()) {
                            listFuture.add(deleteCohortsFuture);
                        }


                        CompositeFuture.all(listFuture).setHandler(handlerUpdateAndDeleteCohorts);

                        if(!arrCohortsToUpdate.isEmpty()) {
                            updateCohorts(arrCohortsToUpdate, resultUpdate -> {
                                if (resultUpdate.isLeft()) {
                                    httpClient.close();
                                    handler.handle(new Either.Left<>("Error updating cohorts"));
                                    log.error("Error updating cohorts", resultUpdate.left());
                                    updateCohortsFuture.fail("Error updating cohorts");
                                } else {
                                    log.info("End updating cohorts");
                                    updateCohortsFuture.complete();
                                }
                            });
                        }

                        if(!arrCohortsToDelete.isEmpty()) {
                            deleteCohorts(arrCohortsToDelete, resultDelete -> {
                                if (resultDelete.isLeft()) {
                                    httpClient.close();
                                    handler.handle(new Either.Left<>("Error deleting cohorts"));
                                    log.error("Error deleting cohorts", resultDelete.left());
                                    deleteCohortsFuture.fail("Error deleting cohorts");
                                } else {
                                    log.info("End deleting cohorts");
                                    deleteCohortsFuture.complete();
                                }
                            });
                        }
                    }
                } else {
                    httpClient.close();
                    handler.handle(new Either.Left<>(event.left().getValue()));
                    log.error("Error enrolling users individually", event.left());
                }

            }
        };


        handlerUpdateAndDeleteCohorts = new Handler<AsyncResult<CompositeFuture>>() {
            @Override
            public void handle(AsyncResult<CompositeFuture> resultUpdateAndDelete) {
                if(resultUpdateAndDelete.failed()) {
                    httpClient.close();
                    handler.handle(new Either.Left<>("Error update/delete cohort Future"));
                    log.error("Error update/delete cohort Future", resultUpdateAndDelete.cause());
                } else {
                    endSyncGroups(handler);
                }

            }
        };
        //---FIN HANDLERS--


        CompositeFuture.all(getGroupsFuture, getSharedBookMarkFuture).setHandler(handlerGetAllGroups);

        JsonArray groupsIds = new JsonArray(Arrays.asList(mapCohortsMoodle.keySet().toArray()));
        // TODO virer prefix
        moodleWebService.getGroups(groupsIds, getGroupsHandler);
        moodleWebService.getDistinctSharedBookMarkUsers(groupsIds, false, getSharedBookMarkHandler);
    }

    private void getUsersCoursesAndEnroll(JsonArray usersToEnrrollIndivually, Handler<Either<String, JsonObject>> handler) {

        if(usersToEnrrollIndivually.isEmpty()) {
            String message = "No user to enrroll";
            log.info(message);
            endSyncGroups(handler);
        } else {

            // Recupération des cours de tous ces utilisateurs
            List<Future> listGetFuture = new ArrayList<Future>();
            for (Object objUser: usersToEnrrollIndivually) {
                JsonObject jsonUser = ((JsonObject)objUser);
                Future getCoursesFuture = Future.future();
                listGetFuture.add(getCoursesFuture);
                getCourses(jsonUser, getCoursesFuture);
            }
            CompositeFuture.all(listGetFuture).setHandler(eventFuture -> {

                if (eventFuture.succeeded()) {
                    log.info("END getting courses");

                    for (Map.Entry<String, JsonObject> entryUser : mapUsersMoodle.entrySet()) {
                        JsonObject jsonUser = entryUser.getValue();
                        JsonArray jsonArrayCourses = mapUsersMoodle.get(jsonUser.getString("id")).getJsonArray("courses");

                        // Inscription individuelle a ses cours
                        identifyUserToDeleteAndEnroll(arrUsersToDelete, arrUsersToEnroll, jsonArrayCourses,
                                jsonUser.getString("id"), SyncCase.SYNC_USER_FOUND);
                    }


                    // Inscription individuel des utilisateurs à leurs cours
                    if (arrUsersToEnroll.isEmpty()) {
                        String message = "Aucune inscription individuelle necessaire";
                        log.info(message);
                        endSyncGroups(handler);
                    } else {
                        enrollUsersIndivudually(arrUsersToEnroll, handlerEnrollGroups);
                    }


                } else {
                    httpClient.close();
                    handler.handle(new Either.Left<>("Error getting all courses in sync groups"));
                    log.error("Error getting all courses in sync groups", eventFuture.cause());
                }
            });
        }

    }


}
