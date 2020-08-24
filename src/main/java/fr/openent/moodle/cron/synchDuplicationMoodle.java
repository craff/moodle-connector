package fr.openent.moodle.cron;
import fr.openent.moodle.controllers.MoodleController;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;

public class synchDuplicationMoodle extends ControllerHelper implements Handler<Long> {

    MoodleController moodleController;

    public synchDuplicationMoodle(Vertx vertx, MoodleController moodleController) {
        this.moodleController = moodleController;
        this.vertx = vertx;
    }

    @Override
    public void handle(Long event) {
        log.debug("Moodle cron started");
        moodleController.synchronisationDuplication(event1 -> {
            if(event1.isRight())
                log.debug("Cron launch successful");
            else
                log.debug("Cron synchonisation not full");
        });
    }
}
