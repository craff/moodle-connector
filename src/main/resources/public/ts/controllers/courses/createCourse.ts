import {model, ng, notify, idiom as i18n} from "entcore";
import {Course} from "../../model";
import {Utils} from "../../utils/Utils";

export const createCourseController = ng.controller('createCourseController', ['$scope', '$timeout',
    ($scope, $timeout) => {
        $scope.typeActivity = {
            availableOptions: [
                {id: 'quiz', name: 'Quizz'},
                {id: 'resource', name: 'Fichier'},
                {id: 'page', name: 'Page'},
                {id: 'assign', name: 'Devoir'}
            ],
            selectedOption: {id: undefined, name: 'Choisissez votre type'}
        };
        $scope.show.imgCompatibleMoodle = false;

        /**
         * Create a course
         */
        $scope.createCourse = async (): Promise<void> => {
            $scope.course.folderId = parseInt($scope.folders.folderIdMoveIn);
            if ($scope.course.fullname.length >= 4) {
                $scope.show.submitWait = true;
                if ($scope.course.infoImg != undefined) {
                    let reg = new RegExp(".jpg|.jpeg|.gif|.png$");
                    $scope.arraySplitImg = reg.exec($scope.course.infoImg.name.toLowerCase());
                    $scope.course.imageurl = $scope.course.imageurl.concat($scope.arraySplitImg[0]);
                }
                await $scope.course.create()
                    .then(async (): Promise<void> => {
                        $scope.show.activityType = $scope.course.typeA;
                        $scope.show.currentFolderId = $scope.folders.folderIdMoveIn;
                        $scope.folders.folderIdMoveIn = undefined;
                        $scope.showToaster();
                        await $scope.courses.getCoursesByUser(model.me.userId);
                        $scope.show.lightboxes = $scope.show.submitWait = false;
                    })
                    .catch((): boolean => $scope.show.submitWait = $scope.show.lightboxes = false);
            } else
                notify.error(i18n.translate("moodle.info.short.title"));
            await Utils.safeApply($scope);
        };

        $scope.changeTypeA = function (course: Course) {
            course.typeA = $scope.typeActivity.selectedOption.id;
        };

        /**
         * get info image
         */
        $scope.getTypeImage = function () {
            $timeout(() => {
                if ($scope.course.imageurl) {
                    $scope.course.setInfoImg();
                    $timeout(() =>
                            $scope.show.imgCompatibleMoodle = $scope.course.infoImg.compatibleMoodle
                        , 1000)
                }
            }, 1000)
            Utils.safeApply($scope);
        };

    }]);
