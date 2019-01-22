package fr.openent.moodle;

import fr.openent.moodle.controllers.MoodleController;
import io.vertx.core.eventbus.EventBus;
import org.entcore.common.http.BaseServer;

public class Moodle extends BaseServer {

	public static String DIRECTORY_BUS_ADDRESS = "directory";

	public static String moodleSchema;
	@Override
	public void start() throws Exception {
		super.start();

		moodleSchema = config.getString("db-schema");
		EventBus eb = getEventBus(vertx);

		addController(new MoodleController(vertx, eb));
	}
}
