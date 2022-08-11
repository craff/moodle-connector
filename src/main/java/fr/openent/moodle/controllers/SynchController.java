package fr.openent.moodle.controllers;

import fr.openent.moodle.security.SynchroRight;
import fr.openent.moodle.service.impl.DefaultSynchService;
import fr.wseduc.rs.Post;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Scanner;

import static fr.openent.moodle.Moodle.moodleMultiClient;
import static fr.openent.moodle.Moodle.workflow_synchro;

public class SynchController extends ControllerHelper {

    private final DefaultSynchService defaultSynchService;
    protected static final Logger log = LoggerFactory.getLogger(SynchController.class);

    public SynchController(EventBus eb, Vertx vertx) {
        this.defaultSynchService = new DefaultSynchService(eb, vertx);
    }

    @Post("/synch/users")
    @SecuredAction(workflow_synchro)
    public void syncUsers(final HttpServerRequest request){
        log.info("--START syncUsers --");
        request.setExpectMultipart(true);
        final Buffer buff = Buffer.buffer();
        request.uploadHandler(upload -> {
            upload.handler(buff::appendBuffer);
            upload.endHandler(end -> {
                log.info("File received  : " + upload.filename());
                InputStream stream = new ByteArrayInputStream(buff.getBytes());
                Scanner sc = new Scanner(stream);
                // skip header
                if(sc.hasNextLine()) {
                    sc.nextLine();
                } else {
                    log.info("Empty user file");
                    return;
                }

                JsonObject moodleClient = moodleMultiClient.getJsonObject(request.host());

                defaultSynchService.syncUsers(sc, moodleClient, syncUsersEither -> {
                    if(syncUsersEither.isLeft()) {
                        log.error("--END syncUsers FAIL--");
                        badRequest(request);
                    } else {
                        log.info("--END syncUsers SUCCESS--");
                        request.response().setStatusCode(200).end();
                    }
                });
            });
        });
    }

    @Post("/synch/groups")
    @ResourceFilter(SynchroRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void syncGroups(final HttpServerRequest request){
        log.info("--START syncGroups --");
        RequestUtils.bodyToJson(request, cohorts -> {
            JsonArray jsonArrayCohorts = cohorts.getJsonArray("cohorts");
            if(jsonArrayCohorts != null && jsonArrayCohorts.size() > 0) {

                JsonObject moodleClient = moodleMultiClient.getJsonObject(request.host());

                defaultSynchService.acceptLanguage = I18n.acceptLanguage(request);
                defaultSynchService.syncGroups(jsonArrayCohorts, moodleClient, syncGroupsEither -> {
                    if(syncGroupsEither.isLeft()) {
                        log.error("--END syncGroups FAIL--");
                        badRequest(request);
                    } else {
                        log.info("--END syncGroups SUCCESS--");
                        request.response().setStatusCode(200).end();
                    }
                });
            } else {
                log.info("No cohort to sync");
                log.info("--END syncGroups --");
                request.response().setStatusCode(200).end();
            }
        });
    }
}
