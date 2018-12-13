package fr.openent.moodle.controllers;

import fr.wseduc.rs.*;
import fr.wseduc.security.SecuredAction;
import io.vertx.core.http.HttpServerRequest;
import org.entcore.common.controller.ControllerHelper;

/**
 * Vert.x backend controller for the application using Mongodb.
 */
public class MoodleController extends ControllerHelper {

	/**
	 * Displays the home view.
	 * @param request Client request
	 */
	@Get("")
	@SecuredAction("moodle.view")
	public void view(HttpServerRequest request) {
		renderView(request);
	}

}
