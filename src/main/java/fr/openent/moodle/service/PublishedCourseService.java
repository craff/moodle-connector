package fr.openent.moodle.service;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;

public interface PublishedCourseService {

    /**
     * get All Disciplines
     * @param handler
     */
   void getDisciplines (Handler<Either<String, JsonArray>> handler);

    /**
     * get All Levels
     * @param handler
     */
   void getLevels (Handler<Either<String, JsonArray>> handler) ;
}
