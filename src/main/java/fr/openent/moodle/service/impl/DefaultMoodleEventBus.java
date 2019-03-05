package fr.openent.moodle.service.impl;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.service.MoodleEventBus;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.entcore.common.service.impl.SqlCrudService;

import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;

public class DefaultMoodleEventBus extends SqlCrudService implements MoodleEventBus {

    private EventBus eb;
    private final String WORKSPACE_BUS_ADDRESS = "org.entcore.workspace";

    public DefaultMoodleEventBus(String schema, String table, EventBus eb) {
        super(schema, table);
        this.eb = eb;
    }

    @Override
    public void getParams (JsonObject action, Handler<Either<String, JsonObject>> handler) {
        eb.send(Moodle.DIRECTORY_BUS_ADDRESS, action, handlerToAsyncHandler(new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> message){
                JsonObject body = message.body();
                JsonObject results = body.getJsonObject("result");
                String email = results.getString("email");
                JsonObject info = new JsonObject();
                info.put("email", email);
                handler.handle(new Either.Right<>(info));
            }
        }));
    }

    @Override
    public void getImage(String idImage, Handler<Either<String, JsonObject>> handler) {
        JsonObject action = new JsonObject()
                .put("action", "getDocument")
                .put("id", idImage);
        eb.send(WORKSPACE_BUS_ADDRESS, action, handlerToAsyncHandler(message -> {
            JsonObject body = message.body().getJsonObject("result");
            if (idImage.equals("")) {
                handler.handle(new Either.Left<>("[DefaultDocumentService@get] An error id image"));
            } else {
                handler.handle(new Either.Right<>(message.body().getJsonObject("result")));
            }
        }));
    }
}
