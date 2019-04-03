package fr.openent.moodle;

import fr.openent.moodle.controllers.MoodleController;
import fr.openent.moodle.cron.synchDuplicationMoodle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import org.entcore.common.http.BaseServer;
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.share.impl.SqlShareService;
import org.entcore.common.sql.SqlConf;
import org.entcore.common.sql.SqlConfs;
import org.entcore.common.storage.Storage;
import org.entcore.common.storage.StorageFactory;
import fr.wseduc.cron.CronTrigger;

import java.text.ParseException;

public class Moodle extends BaseServer {

	public static String DIRECTORY_BUS_ADDRESS = "directory";
	public static String WSTOKEN = "df92b3978e2b958e0335b2f4df505977";
	public static String WS_CREATE_FUNCTION = "local_entcgi_services_createcourse";
	public static String WS_DELETE_FUNCTION = "core_course_delete_courses";
	public static String WS_GET_USERCOURSES = "local_entcgi_services_usercourses";
	public static String WS_CREATE_SHARECOURSE = "local_entcgi_services_shareenrolment";
	public static String WS_GET_SHARECOURSE = "local_entcgi_services_getcourseenrolment";
	public static String WS_POST_DUPLICATECOURSE = "local_entcgi_services_duplicatecourse";
	public static String JSON = "json";

	public static String WAITING = "en attente";
	public static String PENDING = "en cours";
	public static String FINISHED = "terminé";

	public static Integer manager = 1;
	public static Integer coursecreator = 2;
	public static Integer editingteacher = 3;
	public static Integer teacher = 4;
	public static Integer student = 5;
	public static Integer guest = 6;
	public static Integer user = 7;
	public static Integer frontpage = 8;
	public static Integer entcgi = 9;

	public static String moodleSchema;
    public static JsonObject moodleConfig;
    @Override
	public void start() throws Exception {
		super.start();

		moodleSchema = config.getString("db-schema");
        moodleConfig = config;
		EventBus eb = getEventBus(vertx);

		final Storage storage = new StorageFactory(vertx, config, /*new ExercizerStorage()*/ null).getStorage();


		SqlConf courseConf = SqlConfs.createConf(MoodleController.class.getName());
		courseConf.setSchema("moodle");
		courseConf.setTable("course");
		courseConf.setShareTable("course_shares");

//		final String cronExpression = config().getString("$yourProperty$Cron", "0 */"+config.getString("timeMinuteSynchCron")+" * * * ? *");
		final String cronExpression = config().getString("$yourProperty$Cron", "*/30 * * * * ? *");
		try {
			new CronTrigger(vertx, cronExpression).schedule(
					new synchDuplicationMoodle(vertx)
			);
		} catch (ParseException e) {
			log.fatal("Invalid cron expression.", e);
		}

		MoodleController moodleController = new MoodleController(storage, eb);
		moodleController.setShareService(new SqlShareService(moodleSchema, "course_shares", eb, securedActions, null));
		moodleController.setCrudService(new SqlCrudService(moodleSchema, "course"));

		addController(moodleController);
	}
}
