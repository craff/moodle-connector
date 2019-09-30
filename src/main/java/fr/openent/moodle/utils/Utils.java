package fr.openent.moodle.utils;

import fr.openent.moodle.Moodle;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class Utils {

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
        request.response().setStatusCode(400).end(textSend);
    }

}
