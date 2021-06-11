package fr.openent.moodle.service.impl;

import fr.openent.moodle.service.getShareProcessingService;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static fr.openent.moodle.Moodle.moodleConfig;
import static fr.openent.moodle.Moodle.resource_read;
import static java.util.Objects.isNull;

public class DefaultGetShareProcessingService extends ControllerHelper implements getShareProcessingService {

    public DefaultGetShareProcessingService() {
        super();
    }

    /**
     * Treatment to add groups in shareJsonInfosFinal
     *
     * @param userEnrolmentsArray JsonObject with Moodle users
     * @param shareJsonInfosFinal JsonObject to fill
     * @param request             Http request
     * @param rightToAdd          ENT Share rights
     * @param handler             function handler returning data
     */
    public void shareTreatmentForGroups(JsonObject userEnrolmentsArray, JsonObject shareJsonInfosFinal, HttpServerRequest request,
                                        JsonArray rightToAdd, Handler<Either<String, JsonObject>> handler) {

        JsonArray groupEnrolled = userEnrolmentsArray.getJsonArray("groups");
        JsonArray shareInfosGroups = shareJsonInfosFinal.getJsonObject("groups").getJsonArray("visibles");

        List<String> groupsEnrolledId = groupEnrolled.stream().map(obj -> ((JsonObject) obj).getString("idnumber")).collect(Collectors.toList());
        List<String> groupsShareInfosId = shareInfosGroups.stream().map(obj -> ((JsonObject) obj).getString("id")).collect(Collectors.toList());
        for (String groupId : groupsEnrolledId) {
            if (!(groupsShareInfosId.contains(groupId))) {
                JsonObject jsonObjectToAdd = userEnrolmentsArray.getJsonArray("groups").getJsonObject(groupsEnrolledId.indexOf(groupId));
                String id = jsonObjectToAdd.getString("idnumber");
                jsonObjectToAdd.remove("idnumber");
                jsonObjectToAdd.put("id", id);
                Pattern p = Pattern.compile("^GR_(.*)");
                Matcher m = p.matcher(jsonObjectToAdd.getString("id"));
                while (m.find()) {
                    jsonObjectToAdd.put("id", m.group(1));
                }
                UserUtils.groupDisplayName(jsonObjectToAdd, I18n.acceptLanguage(request));
                shareJsonInfosFinal.getJsonObject("groups").getJsonArray("visibles").add(jsonObjectToAdd);
            }
        }

        for (Object group : groupEnrolled) {
            JsonArray rightForLoop = rightToAdd.copy();
            if (((JsonObject) group).getInteger("role").equals(moodleConfig.getInteger("idStudent"))) {
                rightForLoop.remove(2);
            }
            shareJsonInfosFinal.getJsonObject("groups").getJsonObject("checked").put(((JsonObject) group).getString("id"), rightForLoop);
        }
        handler.handle(new Either.Right<>(shareJsonInfosFinal));
    }

    /**
     * Treatment to add users in shareJsonInfosFinal
     *
     * @param userEnrolmentsArray JsonObject with Moodle Users
     * @param shareJsonInfosFinal JsonObject to fill
     * @param user                User
     * @param rightToAdd          ENT Share right
     * @param handler             function handler returning data
     */
    public void shareTreatmentForUsers(JsonObject userEnrolmentsArray, JsonObject shareJsonInfosFinal, UserInfos user,
                                       JsonArray rightToAdd, Handler<Either<String, JsonObject>> handler) {

        JsonArray usersEnrolled = userEnrolmentsArray.getJsonArray("users");
        for (int i = 0; i < usersEnrolled.size(); i++) {
            if (usersEnrolled.getJsonObject(i).getMap().get("role").equals(moodleConfig.getInteger("idAuditeur"))) {
                String idAuditeurToDelete = usersEnrolled.getJsonObject(i).getString("id");
                for (int j = 0; j < usersEnrolled.size(); j++) {
                    if (usersEnrolled.getJsonObject(j).getMap().get("role").equals(moodleConfig.getInteger("idEditingTeacher"))) {
                        if (usersEnrolled.getJsonObject(j).getString("id").equals(idAuditeurToDelete)) {
                            usersEnrolled.remove(j);
                        }
                    } else if (usersEnrolled.getJsonObject(j).getMap().get("role").equals(moodleConfig.getInteger("idStudent"))) {
                        if (usersEnrolled.getJsonObject(j).getString("id").equals(idAuditeurToDelete)) {
                            usersEnrolled.remove(j);
                        }
                    }
                }
            } else if (usersEnrolled.getJsonObject(i).getMap().get("role").equals(moodleConfig.getInteger("idEditingTeacher"))) {
                String idTeacherToDelete = usersEnrolled.getJsonObject(i).getString("id");
                for (int k = 0; k < usersEnrolled.size(); k++) {
                    if (usersEnrolled.getJsonObject(k).getMap().get("role").equals(moodleConfig.getInteger("idStudent"))) {
                        if (usersEnrolled.getJsonObject(k).getString("id").equals(idTeacherToDelete)) {
                            usersEnrolled.remove(k);
                        }
                    }
                }
            }
        }
        JsonArray shareInfosUsers = shareJsonInfosFinal.getJsonObject("users").getJsonArray("visibles");
        List<String> usersEnrolledId = usersEnrolled.stream().map(obj -> ((JsonObject) obj).getString("id")).collect(Collectors.toList());
        List<String> usersShareInfosId = shareInfosUsers.stream().map(obj -> ((JsonObject) obj).getString("id")).collect(Collectors.toList());
        for (Object userEnrolled : usersEnrolled.copy()) {
            if (!(isNull(((JsonObject) userEnrolled).getValue("idnumber")))) {
                userEnrolmentsArray.getJsonArray("users").remove(usersEnrolledId.indexOf(((JsonObject) userEnrolled).getString("id")));
                usersEnrolled = userEnrolmentsArray.getJsonArray("users");
                usersEnrolledId = usersEnrolled.stream().map(obj -> ((JsonObject) obj).getString("id")).collect(Collectors.toList());
            }
        }

        if (shareJsonInfosFinal.getJsonArray("actions").size() == 3){
            int indexToRemove = 0;
            for(int i = 0; i < shareJsonInfosFinal.getJsonArray("actions").size(); i++){
                JsonObject action = shareJsonInfosFinal.getJsonArray("actions").getJsonObject(i);
                if(action.getString("displayName").equals(resource_read)){
                    indexToRemove = i;
                }
            }
            shareJsonInfosFinal.getJsonArray("actions").remove(indexToRemove);
        }

        while (usersEnrolledId.contains(user.getUserId())) {
            userEnrolmentsArray.getJsonArray("users").remove(usersEnrolledId.indexOf(user.getUserId()));
            usersEnrolled = userEnrolmentsArray.getJsonArray("users");
            usersEnrolledId = usersEnrolled.stream().map(obj -> ((JsonObject) obj).getString("id")).collect(Collectors.toList());
        }

        String profile = "";
        for (String userId : usersEnrolledId) {
            if (!(usersShareInfosId.contains(userId))) {
                JsonObject jsonObjectToAdd = userEnrolmentsArray.getJsonArray("users").getJsonObject(usersEnrolledId.indexOf(userId)).copy();
                String firstName = jsonObjectToAdd.getString("firstname");
                String name = jsonObjectToAdd.getString("lastname");
                jsonObjectToAdd.remove("firstname");
                jsonObjectToAdd.remove("lastname");
                jsonObjectToAdd.put("firstName", firstName.charAt(0) + firstName.substring(1).toLowerCase());
                jsonObjectToAdd.put("lastName", name);
                jsonObjectToAdd.put("login", firstName.toLowerCase() + "." + name.toLowerCase());
                jsonObjectToAdd.put("username", name + " " + firstName.charAt(0) + firstName.substring(1).toLowerCase());
                if (jsonObjectToAdd.getInteger("role").equals(moodleConfig.getInteger("idStudent"))) {
                    profile = "Student";
                } else if (jsonObjectToAdd.getInteger("role").equals(moodleConfig.getInteger("idEditingTeacher"))) {
                    profile = "Teacher";
                } else if (jsonObjectToAdd.getInteger("role").equals(moodleConfig.getInteger("idAuditeur"))) {
                    profile = "Teacher";
                }
                jsonObjectToAdd.put("profile", profile);
                if (jsonObjectToAdd.getInteger("role").equals(moodleConfig.getInteger("idAuditeur"))) {
                    shareJsonInfosFinal.getJsonObject("users").getJsonObject("checkedInherited").put(jsonObjectToAdd.getString("id"), rightToAdd);
                }
                shareJsonInfosFinal.getJsonObject("users").getJsonArray("visibles").add(jsonObjectToAdd);
            }
        }

        for (Object userEnrolled : usersEnrolled) {
            JsonArray rightForLoop = rightToAdd.copy();
            if (((JsonObject) userEnrolled).getInteger("role").equals(moodleConfig.getInteger("idStudent"))) {
                rightForLoop.remove(2);
            }
            if (!((JsonObject) userEnrolled).getInteger("role").equals(moodleConfig.getInteger("idAuditeur"))) {
                shareJsonInfosFinal.getJsonObject("users").getJsonObject("checked").put(((JsonObject) userEnrolled).getString("id"), rightForLoop);
            }
        }

        handler.handle(new Either.Right<>(shareJsonInfosFinal));
    }
}
