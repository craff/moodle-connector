package fr.openent.moodle.utils;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.controllers.SynchController;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import static fr.wseduc.webutils.http.Renders.badRequest;

public class Utils {
    protected static final Logger log = LoggerFactory.getLogger(SynchController.class);

    public static JsonArray removeDuplicateCourses(JsonArray duplicatesCours) {
        JsonArray coursesUniq = new JsonArray();
        for (Object course : duplicatesCours)
        {
            boolean findDuplicates = false;
            for (int i = 0; i < coursesUniq.size(); i++) {
                if (((JsonObject) course).getValue("courseid").toString().equals(coursesUniq.getJsonObject(i).getValue("courseid").toString())) {
                    findDuplicates = true;

                    if(isStrongerRole(Integer.parseInt(((JsonObject) course).getValue("role").toString()),
                            Integer.parseInt(coursesUniq.getJsonObject(i).getValue("role").toString()))) {
                        coursesUniq.remove(i);
                        coursesUniq.add(course);
                    }

                }
            }
            if (!findDuplicates) {
                coursesUniq.add(course);
            }
        }
        return coursesUniq;
    }


    public static boolean isStrongerRole(Integer role1, Integer role2) {
        return role1.equals(Moodle.ROLE_AUDITEUR) ||
                (role1.equals(Moodle.ROLE_EDITEUR) && role1.equals(Moodle.ROLE_APPRENANT));

    }

    public static void sendErrorRequest(HttpServerRequest request, String textSend){
        log.error(textSend);
        badRequest(request, textSend);
    }
}
