package fr.openent.moodle;

import fr.openent.moodle.controllers.MoodleController;
import fr.openent.moodle.controllers.SynchController;
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
	public static String ZIMBRA_BUS_ADDRESS = "fr.openent.zimbra";
	public static String WS_CREATE_FUNCTION = "local_entcgi_services_createcourse";
	public static String WS_DELETE_FUNCTION = "local_entcgi_services_movecourse";
	public static String WS_GET_USERCOURSES = "local_entcgi_services_usercourses";
	public static String WS_CREATE_SHARECOURSE = "local_entcgi_services_shareenrolment";
	public static String WS_GET_SHARECOURSE = "local_entcgi_services_getcourseenrolment";
	public static String WS_POST_DUPLICATECOURSE = "local_entcgi_services_duplicatecourse";
	public static String WS_POST_CREATE_OR_UPDATE_USER = "local_entcgi_services_createuser";
	public static String WS_POST_DELETE_USER = "local_entcgi_services_deleteuser";
	public static String WS_POST_ENROLL_USERS_COURSES = "local_entcgi_services_enrolluserscourses";
	public static String WS_POST_UPDATE_COHORTS = "local_entcgi_services_updatecohort";
	public static String WS_POST_DELETE_COHORTS = "local_entcgi_services_deletecohortes";

	public static Integer ROLE_AUDITEUR;
	public static Integer ROLE_EDITEUR;
	public static Integer ROLE_APPRENANT;

	public static String JSON = "json";

	public static String MOODLE_READ = "fr-openent-moodle-controllers-MoodleController|read";
	public static String MOODLE_CONTRIB = "fr-openent-moodle-controllers-MoodleController|contrib";
	public static String MOODLE_MANAGER = "fr-openent-moodle-controllers-MoodleController|shareSubmit";

	public static String WAITING = "en attente";
	public static String PENDING = "en cours";
	public static String FINISHED = "finis";
	public static String ERROR = "echec";

	public static String moodleSchema;
    public static JsonObject moodleConfig;
    @Override
	public void start() throws Exception {
		super.start();

		ROLE_AUDITEUR = config.getInteger("idAuditeur");
		ROLE_EDITEUR = config.getInteger("idEditingTeacher");
		ROLE_APPRENANT = config.getInteger("idStudent");

		moodleSchema = config.getString("db-schema");
        moodleConfig = config;
		EventBus eb = getEventBus(vertx);

		final Storage storage = new StorageFactory(vertx, config, /*new ExercizerStorage()*/ null).getStorage();

		SqlConf courseConf = SqlConfs.createConf(MoodleController.class.getName());
		courseConf.setSchema("moodle");
		courseConf.setTable("course");
		courseConf.setShareTable("course_shares");

		MoodleController moodleController = new MoodleController(storage, eb);
		moodleController.setShareService(new SqlShareService(moodleSchema, "course_shares", eb, securedActions, null));
		moodleController.setCrudService(new SqlCrudService(moodleSchema, "course"));
		SynchController synchController = new SynchController(storage, eb, vertx, config);


		try {
			new CronTrigger(vertx, config.getString("timeSecondSynchCron")).schedule(
					new synchDuplicationMoodle(vertx, moodleController)
			);
		} catch (ParseException e) {
			log.fatal("Invalid cron expression.", e);
		}

		addController(moodleController);
		addController(synchController);
	}
}
