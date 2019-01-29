package fr.openent.moodle;

import fr.openent.moodle.controllers.MoodleController;
import io.vertx.core.eventbus.EventBus;
import org.entcore.common.http.BaseServer;

public class Moodle extends BaseServer {

	public static String DIRECTORY_BUS_ADDRESS = "directory";
	public static String WSTOKEN = "df92b3978e2b958e0335b2f4df505977";
	public static String WS_CREATE_FUNCTION = "local_entcgi_services_createcourse";
	public static String WS_DELETE_FUNCTION = "core_course_delete_courses";
	public static String JSON = "json";

	public static String moodleSchema;
	@Override
	public void start() throws Exception {
		super.start();

		moodleSchema = config.getString("db-schema");
		EventBus eb = getEventBus(vertx);

		addController(new MoodleController(vertx, eb));
	}
}
