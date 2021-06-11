package fr.openent.moodle.controllers;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.security.CreateFolderRight;
import fr.openent.moodle.service.impl.DefaultModuleSQLRequestService;
import fr.openent.moodle.service.moduleSQLRequestService;
import fr.wseduc.rs.*;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.response.DefaultResponseHandler;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.http.RouteMatcher;

import java.util.Map;

import static fr.openent.moodle.Moodle.*;
import static fr.wseduc.webutils.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class FolderController extends ControllerHelper {

    private final moduleSQLRequestService moduleSQLRequestService;


    @Override
    public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
                     Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);
    }

    public FolderController(EventBus eb) {
        super();
        this.eb = eb;
        this.moduleSQLRequestService = new DefaultModuleSQLRequestService(Moodle.moodleSchema, "course");
    }

    @Put("/folders/move")
    @ApiDoc("move a folder")
    @SecuredAction(workflow_moveFolder)
    public void moveFolder(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "folder", folders ->
                UserUtils.getUserInfos(eb, request, user -> {
                    if (user != null) {
                        moduleSQLRequestService.moveFolder(folders, defaultResponseHandler(request));
                    } else {
                        log.error("User not found in session.");
                        unauthorized(request);
                    }
                })
        );
    }

    @Delete("/folder")
    @ApiDoc("delete a folder")
    @SecuredAction(workflow_deleteFolder)
    public void deleteFolder(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "folder", folder ->
                UserUtils.getUserInfos(eb, request, user -> {
                    if (user != null) {
                        moduleSQLRequestService.deleteFolders(folder, defaultResponseHandler(request));
                    } else {
                        log.error("User not found in session.");
                        unauthorized(request);
                    }
                }));
    }

    @Post("/folder")
    @ApiDoc("create a folder")
    @SecuredAction(workflow_createFolder)
    public void createFolder(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "folder", folder ->
                UserUtils.getUserInfos(eb, request, user -> {
                    if (user != null) {
                        folder.put("userId", user.getUserId());
                        folder.put("structureId", user.getStructures().get(0));
                        moduleSQLRequestService.createFolder(folder, defaultResponseHandler(request));
                    } else {
                        log.error("User not found in session.");
                        unauthorized(request);
                    }
                }));
    }

    @Put("/folder/rename")
    @ApiDoc("rename a folder")
    @SecuredAction(workflow_rename)
    public void renameFolder(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "folder", folder ->
                moduleSQLRequestService.renameFolder(folder, defaultResponseHandler(request)));
    }

    @Get("/folder/countsFolders/:id")
    @ApiDoc("Get course in database by folder id")
    @ResourceFilter(CreateFolderRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void getCountsItemInFolder(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                long id_folder = Long.parseLong(request.params().get("id"));
                moduleSQLRequestService.countItemInFolder(id_folder, user.getUserId(), DefaultResponseHandler.defaultResponseHandler(request));
            } else {
                log.error("User not found in session.");
                unauthorized(request);
            }
        });
    }

    @Get("/folders")
    @ApiDoc("Get folder in database")
    @ResourceFilter(CreateFolderRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void getFolder(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                moduleSQLRequestService.getFoldersInEnt(user.getUserId(), arrayResponseHandler(request));
            } else {
                log.error("User not found in session.");
                unauthorized(request);
            }
        });
    }
}
