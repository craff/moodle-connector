package fr.openent.moodle.controllers;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.service.MoodleService;
import fr.openent.moodle.service.impl.DefaultMoodleService;
import fr.openent.moodle.service.impl.DefaultSynchService;
import fr.wseduc.rs.Post;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerFileUpload;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.storage.Storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SynchController extends ControllerHelper {

    private final Storage storage;
    private final MoodleService moodleService;
    private final DefaultSynchService defaultSynchService;
    protected static final Logger log = LoggerFactory.getLogger(SynchController.class);

    public SynchController(Storage storage, EventBus eb, Vertx vertx, JsonObject config) {
        this.moodleService = new DefaultMoodleService(Moodle.moodleSchema, "course");
        this.defaultSynchService = new DefaultSynchService(eb, config, vertx);
        this.storage = storage;
    }

    @Post("/synch/users")
    //@SecuredAction(value ="", type = ActionType.AUTHENTICATED)
    public void syncUsers(final HttpServerRequest request){
        log.info("--START syncUsers --");
        request.setExpectMultipart(true);
        final Buffer buff = Buffer.buffer();
        request.uploadHandler(new Handler<HttpServerFileUpload>() {
            @Override
            public void handle(final HttpServerFileUpload upload) {
                upload.handler(new Handler<Buffer>() {
                    @Override
                    public void handle(Buffer buffer) {
                        buff.appendBuffer(buffer);
                    }
                });

                upload.endHandler(new Handler<Void>() {
                    @Override
                    public void handle(Void end) {
                        try {
                            log.info("Unzip  : " + upload.filename());
                            ZipInputStream zipStream = new ZipInputStream(new ByteArrayInputStream(buff.getBytes()));
                            ZipEntry userFileZipEntry = zipStream.getNextEntry();

                            log.info("Reading : " + userFileZipEntry.getName());
                            Scanner sc = new Scanner(zipStream);
                            // skip header
                            if(sc.hasNextLine()) {
                                sc.nextLine();
                            } else {
                                log.info("Empty user file");
                                return;
                            }

                            defaultSynchService.syncUsers(sc, new Handler<Either<String, JsonObject>>() {
                                @Override
                                public void handle(Either<String, JsonObject> syncUsersEither) {
                                    if(syncUsersEither.isLeft()) {
                                        log.error("--END syncUsers FAIL--");
                                        badRequest(request);
                                    } else {
                                        log.info("--END syncUsers SUCCESS--");
                                        request.response().setStatusCode(200).end();
                                    }
                                }
                            });

                        } catch (IOException e) {
                            log.error("Error reading zip", e);
                        } finally {
                            log.info("--END syncUsers --");
                        }
                    }
                });
            }
        });


    }



    @Post("/synch/groups")
    //@SecuredAction(value ="", type = ActionType.AUTHENTICATED)
    public void syncGroups(final HttpServerRequest request){
        log.info("--START syncGroups --");
        RequestUtils.bodyToJson(request, new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject cohorts) {
                JsonArray jsonArrayCohorts = cohorts.getJsonArray("cohorts");
                if(jsonArrayCohorts != null && jsonArrayCohorts.size() > 0) {
                    defaultSynchService.acceptLanguage = I18n.acceptLanguage(request);
                    defaultSynchService.syncGroups(jsonArrayCohorts, new Handler<Either<String, JsonObject>>() {
                        @Override
                        public void handle(Either<String, JsonObject> syncGroupsEither) {
                            if(syncGroupsEither.isLeft()) {
                                log.error("--END syncGroups FAIL--");
                                badRequest(request);
                            } else {
                                log.info("--END syncGroups SUCCESS--");
                                request.response().setStatusCode(200).end();
                            }
                        }
                    });
                } else {
                    log.info("No cohort to sync");
                    request.response().setStatusCode(200).end();
                }

            }
        });




        log.info("--END syncGroups --");
    }

}
