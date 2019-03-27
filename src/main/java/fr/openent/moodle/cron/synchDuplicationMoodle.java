package fr.openent.moodle.cron;
import io.vertx.core.Handler;

public class synchDuplicationMoodle implements Handler<Long> {

    public synchDuplicationMoodle() {}

    @Override
    public void handle(Long event) {
        //Lancer WS si pas plus de x copies déjà lancé
        //Maj de la BDD
    }
}