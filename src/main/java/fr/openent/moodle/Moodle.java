package fr.openent.moodle;

import fr.openent.moodle.controllers.MoodleController;
import fr.openent.moodle.service.MoodleWebService;
import io.vertx.core.eventbus.EventBus;
import org.entcore.common.http.BaseServer;
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.share.impl.SqlShareService;
import org.entcore.common.sql.SqlConf;
import org.entcore.common.sql.SqlConfs;
import org.entcore.common.storage.Storage;
import org.entcore.common.storage.StorageFactory;

public class Moodle extends BaseServer {

	public static String DIRECTORY_BUS_ADDRESS = "directory";
	public static String WSTOKEN = "df92b3978e2b958e0335b2f4df505977";
	public static String WS_CREATE_FUNCTION = "local_entcgi_services_createcourse";
	public static String WS_DELETE_FUNCTION = "core_course_delete_courses";
	public static String WS_GET_USERCOURSES = "local_entcgi_services_usercourses";
	public static String WS_CREATE_SHARECOURSE = "local_entcgi_services_shareenrolment";
	public static String JSON = "json";

	public static String moodleSchema;
	@Override
	public void start() throws Exception {
		super.start();

		moodleSchema = config.getString("db-schema");
		EventBus eb = getEventBus(vertx);

		final Storage storage = new StorageFactory(vertx, config, /*new ExercizerStorage()*/ null).getStorage();


		SqlConf courseConf = SqlConfs.createConf(MoodleController.class.getName());
		courseConf.setSchema("moodle");
		courseConf.setTable("course");
		courseConf.setShareTable("course_shares");


		MoodleController moodleController = new MoodleController(storage, eb);
		moodleController.setShareService(new SqlShareService(moodleSchema, "course_shares", eb, securedActions, null));
		moodleController.setCrudService(new SqlCrudService(moodleSchema, "course"));

		addController(moodleController);
	}
}
