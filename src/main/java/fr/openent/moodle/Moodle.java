package fr.openent.moodle;

import fr.openent.moodle.controllers.MoodleController;
import org.entcore.common.http.BaseServer;

public class Moodle extends BaseServer {

	@Override
	public void start() throws Exception {
		super.start();
		addController(new MoodleController());
	}

}
