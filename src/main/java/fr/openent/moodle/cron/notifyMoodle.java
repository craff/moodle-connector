package fr.openent.moodle.cron;

import fr.openent.moodle.helper.HttpClientHelper;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.notification.TimelineHelper;
import org.entcore.common.user.UserInfos;

import java.io.UnsupportedEncodingException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static fr.openent.moodle.Moodle.*;

public class notifyMoodle extends ControllerHelper implements Handler<Long> {

    private final TimelineHelper timelineHelper;
    private Timestamp startDate;

    private final SimpleDateFormat myDate;
    private final JsonObject moodleClient;

    public notifyMoodle(Vertx vertx, JsonObject moodleClientToApply, TimelineHelper timelineHelper) {
        this.vertx = vertx;
        this.timelineHelper = timelineHelper;
        myDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        moodleClient = moodleClientToApply;
    }

    @Override
    public void handle(Long event) {
        if(startDate == null){
            startDate = Timestamp.valueOf(myDate.format(new Date()));
        } else {
            log.debug("Moodle Notifications cron started");
            try {
                launchNotifies(event1 -> {
                    if (event1.isRight())
                        log.debug("Cron notifications end successfully");
                    else
                        log.debug("Cron notification not full");
                });
            } catch (UnsupportedEncodingException e) {
                log.error("UnsupportedEncodingException",e);
                log.error("Cron notification not full");
            }
        }
    }

    private void checkNotification(Handler<Either<String, Buffer>> handlerUpdateUser) throws UnsupportedEncodingException {
        Timestamp endDate = Timestamp.valueOf(myDate.format(new Date()));
        String url = moodleClient.getString("address_moodle") +
                moodleClient.getString("ws-path") + "?wstoken=" + moodleClient.getString("wsToken") +
                "&wsfunction=" + WS_CHECK_NOTIFICATIONS +
                "&parameters[startdate]=" + startDate.getTime()/1000 +
                "&parameters[enddate]=" + endDate.getTime()/1000 +
                "&moodlewsrestformat=" + JSON;
        //change date for next time we get notifications
        startDate = endDate;
        HttpClientHelper.webServiceMoodlePost(null, url, vertx, moodleClient, handlerUpdateUser);
    }

    private void launchNotifies(final Handler<Either<String, JsonObject>> eitherHandler) throws UnsupportedEncodingException {
        checkNotification(event -> {
            if (event.isRight()) {
                JsonArray notifications = event.right().getValue().toJsonObject().getJsonArray("notifications");
                if(notifications != null) {
                    for (Object notify : notifications) {
                        JsonObject notification = (JsonObject) notify;
                        UserInfos user = new UserInfos();
                        user.setUserId(notification.getString("useridfrom"));
                        user.setUsername(notification.getString("firstname") + " " + notification.getString("lastname"));
                        String timelineSender = user.getUsername() != null ? user.getUsername() : null;
                        String subject = notification.getString("subject");
                        if(!subject.endsWith(".")){subject += ".";}
                        final JsonObject params = new JsonObject()
                                .put("subject", subject)
                                .put("activityUri", notification.getString("contexturl"))
                                .put("disableAntiFlood", true);
                        params.put("username", timelineSender).put("uri", "/userbook/annuaire#" + user.getUserId());
                        List<String> recipients = new ArrayList<>();
                        recipients.add(notification.getString("useridto"));
                        params.put("pushNotif", new JsonObject().put("title", "push.notif.moodle").put("body", ""));
                        timelineHelper.notifyTimeline(null, "moodle.notification", user, recipients,
                                notification.getInteger("id").toString(), params);
                    }
                }
                eitherHandler.handle(new Either.Right<>(new JsonObject()));
            } else {
                log.error("Error getting notifications", event.left());
                eitherHandler.handle(new Either.Left<>(event.left().getValue()));
            }
        });
    }
}
