package fr.openent.moodle.service.impl;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.service.moodleEventBus;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.service.impl.SqlCrudService;

import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;
import static org.entcore.common.neo4j.Neo4jResult.validResultHandler;


public class DefaultMoodleEventBus extends SqlCrudService implements moodleEventBus {

    private EventBus eb;
    private final String WORKSPACE_BUS_ADDRESS = "org.entcore.workspace";

    public DefaultMoodleEventBus(String schema, String table, EventBus eb) {
        super(schema, table);
        this.eb = eb;
    }

    @Override
    public void getParams(JsonObject action, Handler<Either<String, JsonObject>> handler) {
        eb.send(Moodle.DIRECTORY_BUS_ADDRESS, action, handlerToAsyncHandler(message -> {
            JsonObject results = message.body().getJsonObject("result");
            String email = results.getString("email");
            JsonObject info = new JsonObject();
            info.put("email", email);
            handler.handle(new Either.Right<>(info));
        }));
    }

    @Override
    public void getImage(String idImage, Handler<Either<String, JsonObject>> handler) {
        JsonObject action = new JsonObject()
                .put("action", "getDocument")
                .put("id", idImage);
        eb.send(WORKSPACE_BUS_ADDRESS, action, handlerToAsyncHandler(message -> {
            if (idImage.equals("")) {
                handler.handle(new Either.Left<>("[DefaultDocumentService@get] An error id image"));
            } else {
                handler.handle(new Either.Right<>(message.body().getJsonObject("result")));
            }
        }));
    }

    @Override
    public void getUsers(final JsonArray groupIds, final Handler<Either<String, JsonArray>> handler) {
        JsonObject action = new JsonObject()
                .put("action", "list-users")
                .put("groupIds", groupIds);

        eb.send(Moodle.DIRECTORY_BUS_ADDRESS, action, handlerToAsyncHandler(validResultHandler(handler)));
    }

    @Override
    public void getZimbraEmail(final JsonArray zimbraEmail, final Handler<Either<String, JsonObject>> handler) {
        JsonObject action = new JsonObject()
                .put("action", "getMailUser")
                .put("idList", zimbraEmail);
        eb.send(Moodle.ZIMBRA_BUS_ADDRESS, action, handlerToAsyncHandler(response -> {
            if ("ok".equals(response.body().getString("status"))) {
                handler.handle(new Either.Right<>(response.body().getJsonObject("message")));
            } else {
                handler.handle(new Either.Left<>("Error getting zimbra mail during bus call"));
            }
        }));
        /*JsonObject res = new fr.wseduc.webutils.collections.JsonObject();
        JsonObject jsonMails = new JsonObject();

        for (Object id : zimbraEmail) {
            JsonObject userInfos = new JsonObject();
            userInfos.put("email", "test"+id+"@cgi.com");
            userInfos.put("displayName", "test");
            jsonMails.put((String) id, userInfos);
        }

        res.put("status", "ok").put("message",jsonMails);
        handler.handle(new Either.Right<>(res.getJsonObject("message")));*/
    }
}
