package fr.openent.moodle;

import fr.openent.moodle.controllers.*;
import fr.openent.moodle.cron.NotifyMoodle;
import fr.openent.moodle.cron.SynchDuplicationMoodle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.http.BaseServer;
import org.entcore.common.notification.TimelineHelper;
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.share.impl.SqlShareService;
import org.entcore.common.sql.SqlConf;
import org.entcore.common.sql.SqlConfs;
import org.entcore.common.storage.Storage;
import org.entcore.common.storage.StorageFactory;
import fr.wseduc.cron.CronTrigger;

import java.text.ParseException;
import java.util.Iterator;
import java.util.Map;

public class Moodle extends BaseServer {
	public enum MoodleEvent { ACCESS, CREATE }

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
	public static String WS_CHECK_NOTIFICATIONS = "local_entcgi_services_notifications";

	public static Integer ROLE_AUDITEUR;
	public static Integer ROLE_EDITEUR;
	public static Integer ROLE_APPRENANT;

	public static String JSON = "json";

	public static String MOODLE_READ = "fr-openent-moodle-controllers-ShareController|read";
	public static String MOODLE_CONTRIB = "fr-openent-moodle-controllers-ShareController|contrib";
	public static String MOODLE_MANAGER = "fr-openent-moodle-controllers-ShareController|shareSubmit";

	public static String WAITING = "en attente";
	public static String PENDING = "en cours";
	public static String FINISHED = "finis";
	public static String ERROR = "echec";

	public static String MEDIACENTRE_CREATE = "fr.openent.mediacentre.source.Moodle|create";
	public static String MEDIACENTRE_DELETE = "fr.openent.mediacentre.source.Moodle|delete";
	public static String MEDIACENTRE_UPDATE = "fr.openent.mediacentre.source.Moodle|update";

	//Permissions
	public static final String workflow_view = "moodle.view";
	public static final String workflow_createCourse = "moodle.createCourse";
	public static final String workflow_deleteCourse = "moodle.deleteCourse";
	public static final String workflow_moveCourse = "moodle.moveCourse";
	public static final String workflow_publish = "moodle.publish";
	public static final String workflow_duplicate = "moodle.duplicate";
	public static final String workflow_createFolder = "moodle.createFolder";
	public static final String workflow_deleteFolder = "moodle.deleteFolder";
	public static final String workflow_moveFolder = "moodle.moveFolder";
	public static final String workflow_rename = "moodle.rename";
	public static final String resource_read = "moodle.read";
	public static final String resource_contrib = "moodle.contrib";
	public static final String resource_manager = "moodle.manager";
	public static final String workflow_accessPublicCourse = "moodle.accessPublicCourse";
	public static final String workflow_synchro = "moodle.synchro";
	public static final String workflow_convert = "moodle.convert";


	public static String moodleSchema;
    public static JsonObject moodleConfig;
	public static JsonObject moodleMultiClient;

	@Override
	public void start() throws Exception {
		super.start();

		ROLE_AUDITEUR = config.getInteger("idAuditeur");
		ROLE_EDITEUR = config.getInteger("idEditingTeacher");
		ROLE_APPRENANT = config.getInteger("idStudent");

		moodleSchema = config.getString("dbSchema");
        moodleConfig = config;
		EventBus eb = getEventBus(vertx);
		EventStore eventStore = EventStoreFactory.getFactory().getEventStore(Moodle.class.getSimpleName());

		JsonObject monoClient = new JsonObject().put(
				moodleConfig.getString("host").replace("http://","").replace("https://",""),
				new JsonObject()
						.put("address_moodle", moodleConfig.getString("address_moodle"))
						.put("ws-path", moodleConfig.getString("ws-path"))
						.put("wsToken", moodleConfig.getString("wsToken"))
		);

		moodleMultiClient = moodleConfig.getJsonObject("multiClient", monoClient);
		if(moodleMultiClient.isEmpty()) {
			moodleMultiClient = monoClient;
		}

		final Storage storage = new StorageFactory(vertx, config, /*new ExercizerStorage()*/ null).getStorage();

		SqlConf courseConf = SqlConfs.createConf(MoodleController.class.getName());
		courseConf.setSchema(moodleConfig.getString("dbSchema"));
		courseConf.setTable("course");
		courseConf.setShareTable("course_shares");

		TimelineHelper timelineHelper = new TimelineHelper(vertx, eb, config);

		MoodleController moodleController = new MoodleController(eventStore, storage, eb, vertx);
        PublishedController publishedController = new PublishedController(eb);
		CourseController courseController = new CourseController(eventStore, eb);
		DuplicateController duplicateController = new DuplicateController(eb);
		FolderController folderController = new FolderController(eb);
		ShareController shareController = new ShareController(eb,timelineHelper);
		shareController.setShareService(new SqlShareService(moodleSchema, "course_shares", eb, securedActions, null));
		moodleController.setCrudService(new SqlCrudService(moodleSchema, "course"));
		SynchController synchController = new SynchController(eb, vertx);


		try {
			new CronTrigger(vertx, config.getString("timeSecondSynchCron")).schedule(
					new SynchDuplicationMoodle(vertx)
			);
		} catch (ParseException e) {
			log.fatal("Invalid timeSecondSynchCron cron expression.", e);
		}

		try {
			for (Iterator<Map.Entry<String, Object>> it = moodleMultiClient.stream().iterator(); it.hasNext(); ) {
				JsonObject moodleClient = (JsonObject) it.next().getValue();
				new CronTrigger(vertx, config.getString("timeCheckNotifs")).schedule(
						new NotifyMoodle(vertx, moodleClient, timelineHelper)
				);
			}
		} catch (ParseException e) {
			log.fatal("Invalid timeCheckNotifs cron expression.", e);
		}

		addController(moodleController);
		addController(synchController);
		addController(publishedController);
		addController(courseController);
		addController(duplicateController);
		addController(folderController);
		addController(shareController);
	}
}
