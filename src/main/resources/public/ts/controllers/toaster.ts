import {model, ng, template} from "entcore";
import {Course} from "../model";
import {Utils} from "../utils/Utils";

export const toasterController = ng.controller('toasterController', ['$scope',
    ($scope) => {
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

        $scope.ableToPublish = () => {
            if ($scope.courses.allCourses.filter(course => course.select).length > 0 &&
                $scope.courses.allCourses.filter(course => course.select)[0].categoryid != $scope.courses.publicBankCategoryId) {
                let courses = $scope.getSelectedCourses();
                return courses && courses.length === 1 && courses[0].owner.userId == model.me.userId;
            }
        };

        $scope.openPublishLightBox = () => {
            template.open('lightboxContainer', 'courses/lightbox/publish/publishPopUp');
            $scope.show.lightboxes = true;
            Utils.safeApply($scope);
        };

        $scope.openPopUpMetadataChange = () => {
            template.open('lightboxContainer', 'courses/lightbox/publish/changeMetadataPopUp');
            $scope.show.lightboxes = true;
            Utils.safeApply($scope);
        };

        /**
         * rename folder
         */
        $scope.openPopUpFolderRename = function () {
            template.open('lightboxContainer', 'folders/lightbox/renameFolderLightbox');
            $scope.show.lightboxes = true;
            if($scope.folder && $scope.folder.toRename){
                $scope.folder.toRename = $scope.folders.all.filter(folder => folder.select)[0];
            }
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
