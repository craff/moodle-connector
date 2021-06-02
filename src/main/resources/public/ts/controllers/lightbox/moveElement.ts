import {ng} from "entcore";
import {Utils} from "../../utils/Utils";

export const moveElementController = ng.controller('moveElementController', ['$scope',
    ($scope) => {

        $scope.targetFolderName = function () {
            let nameToReturn;
            if ($scope.folders.folderIdMoveIn != 0) {
                $scope.folders.all.forEach(function (e) {
                    if (e.id == $scope.folders.folderIdMoveIn)
                        nameToReturn = e.name;
                });
            } else
                nameToReturn = "Mes cours";
            return nameToReturn;
        };

        $scope.move = async function () {
            if ($scope.show.nbFoldersSelect > 0)
                await $scope.folders.moveToFolder(undefined);
            if ($scope.show.nbCoursesSelect > 0)
                await $scope.courses.moveToFolder($scope.folders.folderIdMoveIn);
            $scope.show.currentFolderId = $scope.folders.folderIdMoveIn;
            await $scope.initFolders();
            $scope.folders.all.filter(folder => folder.select).map(folder => folder.selectConfirm = false);
            $scope.courses.allCourses.filter(course => course.select).map(course => course.selectConfirm = false);
            $scope.folders.folderIdMoveIn = undefined;
            $scope.show.lightboxes = false;
            await Utils.safeApply($scope);
        };

    }]);
