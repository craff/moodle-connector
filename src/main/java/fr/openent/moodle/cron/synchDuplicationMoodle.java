package fr.openent.moodle.cron;
import fr.openent.moodle.Moodle;
import fr.openent.moodle.helper.HttpClientHelper;
import fr.openent.moodle.controllers.MoodleController;
import fr.openent.moodle.service.MoodleEventBus;
import fr.openent.moodle.service.MoodleWebService;
import fr.openent.moodle.service.impl.DefaultMoodleWebService;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.response.DefaultResponseHandler;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.storage.Storage;
import org.entcore.common.storage.StorageFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicBoolean;

import static fr.openent.moodle.Moodle.*;

public class synchDuplicationMoodle extends ControllerHelper implements Handler<Long> {

    MoodleController moodleController;


    public synchDuplicationMoodle(Vertx vertx, MoodleController moodleController) {
        this.moodleController = moodleController;
        this.vertx = vertx;
    }

    @Override
    public void handle(Long event) {
        log.info("Moodle cron started ");
        moodleController.synchronisationDuplication(new Handler<Either<String, JsonObject>>() {
            @Override
            public void handle(Either<String, JsonObject> event) {
                if(event.isRight())
                    log.info("Cron launch successful");
                else
                    log.error("Cron synchonisation not full");
            }
        });
    }
}