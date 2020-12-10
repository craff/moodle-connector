package fr.openent.moodle.service;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Map;

public interface moduleSQLRequestService {

    /**
     * delete folders
     *
     * @param folder  folder to delete
     * @param handler function handler returning data
     */
    void deleteFolders(JsonObject folder, Handler<Either<String, JsonObject>> handler);


    /**
     * Create a folder
     * @param folder folder to create
     * @param handler function handler returning data
     */
    void createFolder (JsonObject folder, Handler<Either<String, JsonObject>> handler);

    /**
     * Rename a folder
     * @param folder folder to rename
     * @param handler function handler returning data
     */
    void renameFolder (JsonObject folder, Handler<Either<String, JsonObject>> handler);

    /**
     * move a folder
     * @param folders folders to move
     * @param handler function handler returning data
     */
    void moveFolder(JsonObject folders, Handler<Either<String, JsonObject>> handler);

    /**
     * Create a course
     *
     * @param course  course to create
     * @param handler function handler returning data
     */
    void createCourse(JsonObject course, Handler<Either<String, JsonObject>> handler);

    /**
     * Check if course(s) is in relation table
     *
     * @param courses courses id to check
     * @param handler function handler returning data
     */
    void checkIfCoursesInRelationTable(JsonObject courses, Handler<Either<String, Boolean>> handler);

    /**
     * Insert course(s) in relation table
     *
     * @param courses courses to move
     * @param handler function handler returning data
     */
    void insertCourseInRelationTable(final JsonObject courses, Handler<Either<String, JsonObject>> handler);

    /**
     * Update the folder id for course(s) in relation table
     *
     * @param courses courses to move
     * @param handler function handler returning data
     */
    void updateCourseIdInRelationTable(JsonObject courses, Handler<Either<String, JsonObject>> handler);

    /**
     * Delete course(s) in relation table
     *
     * @param courses courses to delete
     * @param handler function handler returning data
     */
    void deleteCourseInRelationTable(final JsonObject courses, final Handler<Either<String, JsonObject>> handler);

    /**
     * Delete courses
     *
     * @param course  course to delete
     * @param handler function handler returning data
     */
    void deleteCourse(JsonObject course, Handler<Either<String, JsonObject>> handler);

    /**
     * get course preferences of user
     *
     * @param id_user user id
     * @param handler function handler returning data
     */
    void getPreferences(String id_user, Handler<Either<String, JsonArray>> handler);

    /**
     * set course preferences of user
     * @param course course where preferences was changed
     * @param handler function handler returning data
     */
    void setPreferences(JsonObject course, Handler<Either<String, JsonObject>> handler);

    /**
     * get list folders by user
     * @param id_user user id
     * @param handler function handler returning data
     */
    void getFoldersInEnt(String id_user,Handler<Either<String, JsonArray>> handler);

    /**
     * count Item folders In folder
     *
     * @param id_folder              id folder
     * @param userId                 user id
     * @param defaultResponseHandler function handler returning data
     */
    void countItemInFolder(long id_folder, String userId, Handler<Either<String, JsonObject>> defaultResponseHandler);

    /**
     * get courses and shared by users
     * @param userId user id
     * @param eitherHandler function handler returning data
     */
    void getCoursesByUserInEnt(String userId, Handler<Either<String, JsonArray>> eitherHandler);

    /**
     * get choice of user in a specific view
     * @param userId user id
     * @param eitherHandler function handler returning data
     */
    void getChoices(String userId, Handler<Either<String, JsonArray>> eitherHandler);

    /**
     * set choice of user in a specific view in the DataBase
     *
     * @param courses where the choice is stock
     * @param view    view choose by the user
     * @param handler function handler returning data
     */
    void setChoice(JsonObject courses, String view, Handler<Either<String, JsonObject>> handler);

    /**
     * @param bookmarksIds ids bookmarks
     * @param addPrefix    boolean
     * @param handler      function handler returning data
     */
    void getDistinctSharedBookMarkUsers(JsonArray bookmarksIds, boolean addPrefix, Handler<Either<String, Map<String, JsonObject>>> handler);

    /**
     * insert duplication table
     *
     * @param courseToDuplicate course to duplicate
     * @param handler           function handler returning data
     */
    void insertDuplicateTable(JsonObject courseToDuplicate, Handler<Either<String, JsonObject>> handler);

    /**
     * get courseid to duplicate
     * @param status state of the course to duplicate
     * @param handler function handler returning data
     */
    void getCourseIdToDuplicate (String status, Handler<Either<String, JsonArray>> handler);

    /**
     * get course of the user to duplicate
     * @param userId user id
     * @param handler function handler returning data
     */
    void getUserCourseToDuplicate (String userId, final Handler<Either<String, JsonArray>> handler);

    /**
     * get a course to duplicate
     * @param ident sql id of the course to duplicate
     * @param handler function handler returning data
     */
    void getCourseToDuplicate (Integer ident, final Handler<Either<String, JsonObject>> handler);

    /**
     * get courseid to duplicate
     * @param status state of the course to duplicate
     * @param id sql id of the course to duplicate
     * @param attemptsNumber in order to increment the number of duplication when the processus failed
     * @param handler function handler returning data
     */
    void updateStatusCourseToDuplicate (String status, Integer id, Integer attemptsNumber, Handler<Either<String, JsonObject>> handler);

    /**
     * Delete finished duplication
     * @param handler function handler returning data
     */
    void deleteFinishedCoursesDuplicate (Handler<Either<String, JsonObject>> handler);

    /**
     * Insert published course metadata
     * @param courseToPublish publish course to insert
     * @param handler function handler returning data
     */
    void insertPublishedCourseMetadata (JsonObject courseToPublish, Handler<Either<String, JsonObject>> handler);

    /**
     * Update publication table with the new course id
     * @param createCourseDuplicate JsonObject with the course id to put in publication table
     * @param handler function handler returning data
     */
    void updatePublishedCourseId (JsonObject createCourseDuplicate, Handler<Either<String, JsonObject>> handler);

    /**
     * Update metadata publication table with the new course id
     * @param course_id Integer of the course id to change
     * @param newMetadata JsonObject with the new metadata label
     * @param handler function handler returning data
     */
    void updatePublicCourseMetadata(Integer course_id, JsonObject newMetadata, Handler<Either<String, JsonObject>> handler);

    /**
     * Update course publication table with the new course id
     * @param newCourse JsonObject with the new course infos
     * @param handler function handler returning data
     */
    void updatePublicCourse( JsonObject newCourse, Handler<Either<String, JsonObject>> handler);

    /**
     * Delete published course
     * @param idsToDelete JsonArray with course id to delete
     * @param handler function handler returning data
     */
    void deletePublicCourse(JsonArray idsToDelete, Handler<Either<String, JsonObject>> handler);

    /**
     * Get duplication id to update publication table
     * @param publicationId JsonArray publication foreign key in duplication table
     * @param handler function handler returning data
     */
    void getDuplicationId(JsonArray publicationId, Handler<Either<String, JsonObject>> handler);

    /**
     * Delete published course
     * @param id JsonArray with the id of the course to get data from
     * @param handler function handler returning data
     */
    void getPublicCourseData(JsonArray id, Handler<Either<String, JsonObject>> handler);
}
