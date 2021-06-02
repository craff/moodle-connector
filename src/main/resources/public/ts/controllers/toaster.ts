import {_, model, ng, template} from "entcore";
import {Mix} from 'entcore-toolkit';
import {Course, Folder, PublicCourse} from "../model";
import {Utils} from "../utils/Utils";

export const toasterController = ng.controller('toasterController', ['$scope', '$timeout',
    ($scope, $timeout) => {
        /**
         * refresh after a Share
         */
        $scope.openSharePopUp = () => {
            $scope.show.lightboxes = true;
            template.open('lightboxContainer', 'courses/lightbox/shareLightbox');
            Utils.safeApply($scope);
        };

        /**
         * duplicate elements
         */
        $scope.openPopUpDuplicate = (): void => {
            $scope.courses.allCourses = $scope.courses.allCourses.map((course: Course): Course => {
                course.selectConfirm = course.select;
                return course
            });
            template.open('lightboxContainer', 'courses/lightbox/duplicateLightbox');
            $scope.show.lightboxes = true;
        };

        const getSelectedCourses = function () {
            return _.where([...$scope.courses.coursesByUser, ...$scope.courses.coursesShared, ...$scope.courses.coursesPublished], {select: true});
        };

        $scope.ableToPublish = () => {
            if ($scope.courses.allCourses.filter(course => course.select).length > 0 &&
                $scope.courses.allCourses.filter(course => course.select)[0].categoryid != $scope.courses.publicBankCategoryId) {
                let courses = getSelectedCourses();
                return courses.length === 1 && courses[0].owner.userId == model.me.userId;
            }
        };

        $scope.openPublishLightBox = () => {
            $scope.courseToPublish = getSelectedCourses()[0];
            $scope.courseToPublish.myRights = {};
            $scope.courseToPublish = Mix.castAs(PublicCourse, $scope.courseToPublish) ;
            template.open('lightboxContainer', 'courses/lightbox/publish/publishPopUp');
            $scope.show.lightboxes = true;
            Utils.safeApply($scope);
        };

        $scope.openPopUpMetadataChange = () => {
            template.open('lightboxContainer', 'courses/lightbox/publish/changeMetadataPopUp');
            $scope.show.lightboxes = true;
            Utils.safeApply($scope);
            $scope.courseToPublish = getSelectedCourses()[0];
            $scope.courseToPublish.myRights = {};
            $scope.courseToPublish = Mix.castAs(PublicCourse, $scope.courseToPublish);
            $scope.courseToPublish.plain_text.all = $scope.courseToPublish.plain_text;
            $scope.filterChoice.levels = [];
            $scope.filterChoice.disciplines = [];
            $scope.filterChoice.plain_text = [];
            $timeout(() =>
                {
                    $scope.courseToPublish.levels.forEach(level => {
                        $scope.filterChoice.levels.push($scope.levels.all.find(level_bis => level.label == level_bis.label));
                    });
                    $scope.courseToPublish.disciplines.forEach(discipline => {
                        $scope.filterChoice.disciplines.push($scope.disciplines.all.find(discipline_bis => discipline.label == discipline_bis.label));
                    });
                    $scope.courseToPublish.plain_text.forEach(word => {
                        $scope.filterChoice.plain_text.push(word);
                    });
                    Utils.safeApply($scope);}
                , 100);
        };

        /**
         * rename folder
         */
        $scope.openPopUpFolderRename = function () {
            $scope.folder = new Folder();
            $scope.folder = $scope.folders.all.filter(folder => folder.select)[0];
            template.open('lightboxContainer', 'folders/lightbox/renameFolderLightbox');
            $scope.show.lightboxes = true;
        };

        /**
         * move folders & courses
         */
        $scope.openPopUpMove = function () {
            template.open('lightboxContainer', 'lightbox/moveElementLightbox');
            $scope.show.lightboxes = true;
        };

        /**
         * delete elements
         */
        $scope.openPopUpDelete = function () {
            template.open('lightboxContainer', 'lightbox/deleteLightbox');
            $scope.folders.all.filter(folder => folder.select).map(folder => folder.selectConfirm = true);
            $scope.courses.allCourses.filter(course => course.select).map(course => course.selectConfirm = true);
            $scope.confirmDeleteSend();
            $scope.show.lightboxes = true;
        };

    }]);
