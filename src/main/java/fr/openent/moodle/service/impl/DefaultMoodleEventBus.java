package fr.openent.moodle.service.impl;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.service.moodleEventBus;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;

import static fr.openent.moodle.Moodle.moodleConfig;
import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;
import static org.entcore.common.neo4j.Neo4jResult.validResultHandler;


public class DefaultMoodleEventBus extends ControllerHelper implements moodleEventBus {

    private final EventBus eb;
    private final fr.openent.moodle.service.moduleSQLRequestService moduleSQLRequestService;


    public DefaultMoodleEventBus(EventBus eb) {
        super();
        this.eb = eb;
        this.moduleSQLRequestService = new DefaultModuleSQLRequestService(Moodle.moodleSchema, "course");
    }

    @Override
    public void getParams(final JsonObject action, final Handler<Either<String, JsonObject>> handler) {
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
        String WORKSPACE_BUS_ADDRESS = "org.entcore.workspace";
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

    @Override
    public void publishInMediacentre(JsonArray id, final Handler<Either<String, JsonObject>> handler) {
        moduleSQLRequestService.getPublicCourseData(id, getData -> {
            if (getData.isRight()) {
                JsonObject getDataObject = getData.right().getValue();
                log.info("Event Bus launched");
                JsonObject resource = new JsonObject();
                JsonArray authors = new JsonArray().add(getDataObject.getString("author"));
                resource.put("title", getDataObject.getString("fullname"))
                        .put("authors", authors)
                        .put("id", getDataObject.getInteger("course_id"))
                        .put("image", getDataObject.getString("imageurl"))
                        .put("document_types", new JsonArray().add("Parcours Moodle"))
                        .put("link", moodleConfig.getString("host") + "/moodle/course/share/BP/" + getDataObject.getInteger("course_id"))
                        .put("date", System.currentTimeMillis())
                        .put("favorite", false)
                        .put("source", "fr.openent.mediacentre.source.Moodle");

                if (getDataObject.getString("user_id").equals(getDataObject.getString("author_id"))) {
                    resource.put("editors", authors);
                } else {
                    JsonArray editors = new JsonArray().add(getDataObject.getString("username"));
                    resource.put("editors", editors);
                }

                JsonArray resourceLevels = new JsonArray();
                for (int i = 0; getDataObject.getJsonArray("level_label").size() > i; i++) {
                    resourceLevels.add(getDataObject.getJsonArray("level_label").getJsonArray(i).getString(1));
                }
                resource.put("levels", resourceLevels);

                JsonArray resourceDiscipline = new JsonArray();
                for (int i = 0; getDataObject.getJsonArray("discipline_label").size() > i; i++) {
                    resourceDiscipline.add(getDataObject.getJsonArray("discipline_label").getJsonArray(i).getString(1));
                }
                resource.put("disciplines", resourceDiscipline);

                JsonArray resourceKeyword = new JsonArray();
                for (int i = 0; getDataObject.getJsonArray("key_words").size() > i; i++) {
                    resourceKeyword.add(getDataObject.getJsonArray("key_words").getJsonArray(i).getString(1));
                }
                resource.put("key_words", resourceKeyword);

                resource.put("description", getDataObject.getString("summary"));

                eb.send(Moodle.MEDIACENTRE_CREATE, resource, handlerToAsyncHandler(event -> {
                    if ("ok".equals(event.body().getString("status"))) {
                        log.info("export succeeded");
                        handler.handle(new Either.Right<>(event.body().getJsonObject("result")));
                    } else {
                        log.error(event.body());
                        handler.handle(new Either.Left<>("Failed create public course"));
                    }
                }));
            }else {
                handler.handle(new Either.Left<>("Failed get table SQL publication"));
            }
        });
    }

    @Override
    public void updateInMediacentre(JsonObject updateCourse, final Handler<Either<String, JsonObject>> handler) {
        JsonObject query = new JsonObject()
                .put("doc", new JsonObject().put("title", updateCourse.getString("fullname")));

        query.getJsonObject("doc").put("image", updateCourse.getString("imageurl"));
        query.getJsonObject("doc").put("description", updateCourse.getString("summary"));

        JsonObject resources = new JsonObject();
        resources.put("query", query)
                .put("id", updateCourse.getInteger("courseid"));

        eb.send(Moodle.MEDIACENTRE_UPDATE, resources, handlerToAsyncHandler(event -> {
            if ("ok".equals(event.body().getString("status"))) {
                log.info("export succeeded");
                handler.handle(new Either.Right<>(event.body().getJsonObject("result")));
            } else {
                log.error(event.body());
                handler.handle(new Either.Left<>("Failed update public course"));
            }
        }));
    }

    public void deleteResourceInMediacentre (JsonObject deleteEvent, final Handler<Either<String, JsonObject>> handler) {
        JsonObject query = new JsonObject().put("query", new JsonObject().put("bool", new JsonObject().put("should", new JsonArray())));
        for (int i = 0; i < deleteEvent.getJsonArray("coursesId").size(); i++) {
            query.getJsonObject("query").getJsonObject("bool").getJsonArray("should").add(new JsonObject()
                    .put("term", new JsonObject().put("id", deleteEvent.getJsonArray("coursesId").getValue(i).toString())));
        }
        eb.send(Moodle.MEDIACENTRE_DELETE, query, handlerToAsyncHandler(deleteEventBus -> {
            if ("ok".equals(deleteEventBus.body().getString("status"))) {
                log.info("ElasticSearch course delete");
                JsonArray idsToDelete = new JsonArray();
                for (int i = 0; i < deleteEvent.getJsonArray("coursesId").size(); i++) {
                    idsToDelete.add(Integer.parseInt(deleteEvent.getJsonArray("coursesId").getValue(i).toString()));
                }
                moduleSQLRequestService.deletePublicCourse(idsToDelete, deleteSQL -> {
                    if (deleteSQL.isRight()) {
                        log.info("SQL deletion OK");
                        handler.handle(new Either.Right<>(deleteSQL.right().getValue()));
                    } else {
                        handler.handle(new Either.Left<>("Failed publication table SQL deletion"));
                    }
                });
            } else {
                log.error(deleteEventBus.body());
                handler.handle(new Either.Left<>("Failed delete public course in elastic search"));
            }
        }));
    }

    public void updateResourceInMediacentre (JsonObject updateMetadata, final Handler<Either<String, JsonObject>> handler) {
        JsonObject query = new JsonObject();

        JsonArray levelArray = new JsonArray();
        for (int i = 0; i < updateMetadata.getJsonArray("levels").size(); i++) {
            levelArray.add((updateMetadata.getJsonArray("levels").getJsonObject(i).getString("label")));
        }
        query.put("doc", new JsonObject().put("levels", levelArray));


        JsonArray disciplineArray = new JsonArray();
        for (int i = 0; i < updateMetadata.getJsonArray("disciplines").size(); i++) {
            disciplineArray.add((updateMetadata.getJsonArray("disciplines").getJsonObject(i).getString("label")));
        }
        query.getJsonObject("doc").put("disciplines", disciplineArray);

        JsonArray plainTextArray = new JsonArray();
        for (int i = 0; i < updateMetadata.getJsonArray("plain_text").size(); i++) {
            plainTextArray.add((updateMetadata.getJsonArray("plain_text").getJsonObject(i).getString("label")));
        }
        query.getJsonObject("doc").put("plain_text", plainTextArray);

        JsonObject resources = new JsonObject();
        resources.put("query", query)
                .put("id", updateMetadata.getJsonArray("coursesId").getInteger(0));

        eb.send(Moodle.MEDIACENTRE_UPDATE, resources, handlerToAsyncHandler(event -> {
            if ("ok".equals(event.body().getString("status"))) {
                log.info("export succeeded");
                handler.handle(new Either.Right<>(event.body()));
            } else {
                handler.handle(new Either.Left<>("Failed update metadata public course"));
            }
        }));
    }
}
