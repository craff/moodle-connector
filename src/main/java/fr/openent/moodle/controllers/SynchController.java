package fr.openent.moodle.controllers;

import fr.openent.moodle.service.impl.DefaultSynchService;
import fr.wseduc.rs.Post;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.controller.ControllerHelper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SynchController extends ControllerHelper {

    private final DefaultSynchService defaultSynchService;
    protected static final Logger log = LoggerFactory.getLogger(SynchController.class);

    public SynchController(EventBus eb, Vertx vertx) {
        this.defaultSynchService = new DefaultSynchService(eb, vertx);
    }

    @Post("/synch/users")
    //@SecuredAction(value ="", type = ActionType.AUTHENTICATED)
    public void syncUsers(final HttpServerRequest request){
        log.info("--START syncUsers --");
        request.setExpectMultipart(true);
        final Buffer buff = Buffer.buffer();
        request.uploadHandler(upload -> {
            upload.handler(buff::appendBuffer);
            upload.endHandler(end -> {
                try {
                    log.info("Unzip  : " + upload.filename());
                    ZipInputStream zipStream = new ZipInputStream(new ByteArrayInputStream(buff.getBytes()));
                    ZipEntry userFileZipEntry = zipStream.getNextEntry();
                    assert userFileZipEntry != null;
                    log.info("Reading : " + userFileZipEntry.getName());
                    Scanner sc = new Scanner(zipStream);
                    // skip header
                    if(sc.hasNextLine()) {
                        sc.nextLine();
                    } else {
                        log.info("Empty user file");
                        return;
                    }
                    defaultSynchService.syncUsers(sc, syncUsersEither -> {
                        if(syncUsersEither.isLeft()) {
                            log.error("--END syncUsers FAIL--");
                            badRequest(request);
                        } else {
                            log.info("--END syncUsers SUCCESS--");
                            request.response().setStatusCode(200).end();
                        }
                    });
                } catch (IOException e) {
                    log.error("Error reading zip", e);
                } finally {
                    log.info("--END syncUsers --");
                }
            });
        });
    }

    @Post("/synch/groups")
    //@SecuredAction(value ="", type = ActionType.AUTHENTICATED)
    public void syncGroups(final HttpServerRequest request){
        log.info("--START syncGroups --");
        RequestUtils.bodyToJson(request, cohorts -> {
            JsonArray jsonArrayCohorts = cohorts.getJsonArray("cohorts");
            if(jsonArrayCohorts != null && jsonArrayCohorts.size() > 0) {
                defaultSynchService.acceptLanguage = I18n.acceptLanguage(request);
                defaultSynchService.syncGroups(jsonArrayCohorts, syncGroupsEither -> {
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
