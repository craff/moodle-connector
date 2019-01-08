package fr.openent.moodle.controllers;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.service.MoodleService;
import fr.openent.moodle.service.impl.DefaultMoodleService;
import fr.wseduc.rs.*;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;

import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class MoodleController extends ControllerHelper {

	private final MoodleService moodleService;

	public MoodleController(Vertx vertx) {
		super();
		this.moodleService = new DefaultMoodleService(Moodle.moodleSchema, "course");
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

	@Post("/courses")
	@SecuredAction("moodle.create")
	public void create(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "course", new Handler<JsonObject>() {
            @Override
            public void handle(final JsonObject course) {
                moodleService.create(course, defaultResponseHandler(request));
            }
        });
	}

	@Delete("/courses/:id")
    @SecuredAction("moodle.delete")
    public void delete (final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(UserInfos user) {
                String id_moodle = request.params().get("id_moodle");
                if (user != null) {
                    moodleService.delete(id_moodle, defaultResponseHandler(request));
                } else {
                    unauthorized(request);
                }
            }
        });
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

                // 2) Apres le resultat de 1)
                // si statut OK, enregistrer dans notre BDD SQL
                //TODO récupérer les infos du user et du cours depuis la request

                // TODO appeler service pour sauvegarder dans la table cours
                // sinon afficher message erreur

    }
