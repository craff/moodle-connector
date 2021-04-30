package fr.openent.moodle.controllers;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.service.PublishedCourseService;
import fr.openent.moodle.service.impl.DefaultPublishedCourseService;
import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import io.vertx.core.http.HttpServerRequest;
import org.entcore.common.controller.ControllerHelper;

import static fr.wseduc.webutils.http.response.DefaultResponseHandler.arrayResponseHandler;

public class PublishedCourseController extends ControllerHelper {

    private final PublishedCourseService publishedCourseService;
    public PublishedCourseController() {
        super();
        this.publishedCourseService = new DefaultPublishedCourseService(Moodle.moodleSchema, "");
    }


    @Get("/levels")
    @ApiDoc("get all levels")
    public void getLevels (HttpServerRequest request) {
        publishedCourseService.getLevels(arrayResponseHandler(request));
    }


    @Get("/disciplines")
    @ApiDoc("get all disciplines")
    public void getDisciplines (HttpServerRequest request) {
        publishedCourseService.getDisciplines(arrayResponseHandler(request));
    }
}
