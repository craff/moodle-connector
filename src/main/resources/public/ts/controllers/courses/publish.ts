import {_, ng, notify} from "entcore";
import {Labels, Label} from "../../model";
import {Utils} from "../../utils/Utils";
import {TIME_TO_REFRESH_DUPLICATION} from "../../constantes";

export const publishController = ng.controller('publishController', ['$scope', '$timeout',
    ($scope, $timeout) => {
        $scope.levels = new Labels();
        $scope.levels.sync("levels");
        $scope.disciplines = new Labels();
        $scope.disciplines.sync("disciplines");
        $scope.query = {plain_text: ""};

        $scope.removeLevelFromCourse = (level: Label) => {
            $scope.filterChoice.levels = _.without($scope.filterChoice.levels , level);
            $scope.courseToPublish.levels = _.without($scope.courseToPublish.levels, level);
        };

        $scope.removeDisciplineFromCourse = (discipline: Label) => {
            $scope.filterChoice.disciplines = _.without($scope.filterChoice.disciplines , discipline);
            $scope.courseToPublish.disciplines = _.without($scope.courseToPublish.disciplines, discipline);
        };

        $scope.addKeyWord = (event) => {
            if (event.keyCode == 59 || event.key == "Enter") {
                if ($scope.query.plain_text.trim()!= ""){
                    if (!!!$scope.courseToPublish.plain_text) {
                        $scope.courseToPublish.plain_text = new Labels();
                        $scope.filterChoice.plain_text = new Labels();
                    }
                    $scope.courseToPublish.plain_text.all.push(new Label(null, $scope.query.plain_text.trim()));
                    $scope.filterChoice.plain_text.push(new Label(null, $scope.query.plain_text.trim()));
                    $scope.query.plain_text = "";
                    Utils.safeApply($scope);
                }
            }
        };

        $scope.removeWordFromCourse = (word: Label) => {
            $scope.filterChoice.plain_text = _.without($scope.filterChoice.plain_text , word);
            $scope.courseToPublish.plain_text = _.without($scope.courseToPublish.plain_text, word);
            if($scope.courseToPublish.plain_text.length == 0) {
                $scope.courseToPublish.plain_text = new Labels();
                $scope.courseToPublish.plain_text.all = [];
            }
        };

        $scope.publishCourse = async () => {
            $scope.resetSelect();
            $scope.closePopUp();
            $scope.show.toaster = false;
            await $scope.courseToPublish.publish()
                .then(async (): Promise<void> => {
                    await $scope.initFolders();
                    await $scope.initCoursesByUser();
                    $timeout((): void =>
                        $scope.updateCourse(), TIME_TO_REFRESH_DUPLICATION);
                    $scope.isPrintMenuCourses("published");
                    notify.success('moodle.info.publishTextConfirmSuccess');
                });
        };

        $scope.modifyCourse = async () => {
            $scope.courseToPublish.levels = $scope.filterChoice.levels;
            $scope.courseToPublish.disciplines = $scope.filterChoice.disciplines;
            $scope.courseToPublish.plain_text.all = $scope.filterChoice.plain_text;
            await $scope.courseToPublish.modify()
                .then(async (): Promise<void> => {
                    await $scope.initFolders();
                    await $scope.initCoursesByUser();
                    $timeout((): void =>
                        $scope.updateCourse(), TIME_TO_REFRESH_DUPLICATION);
                    $scope.isPrintMenuCourses("published");
                    notify.success('moodle.info.modifyTextConfirmSuccess');
                });
            $scope.resetSelect();
            $scope.closePopUp();
            $scope.show.toaster = false;
        };

        $scope.resetPublicationPopUp = async () => {
            $scope.deselectAll();
            $scope.disciplines = undefined;
            $scope._plain_text = undefined;
        };

    }]);
