import {ng} from "entcore";
import {Course} from "../../model";

export const duplicateController = ng.controller('duplicateController', ['$scope', '$timeout', '$templateCache',
    ($scope, $timeout, $templateCache) => {
        /**
         * confirm duplicate
         */
        $scope.confirmDuplicateSend = function () {
            return !!($scope.folders.all.some(folder => folder.selectConfirm) || $scope.courses.allCourses.some(course => course.selectConfirm));
        };

        $scope.duplicateElements = async (): Promise<void> => {
            $scope.show.lightboxes = $scope.show.toaster = false;
            if ($scope.courses.allCourses.some((course: Course): boolean => course.selectConfirm)) {
                $templateCache.removeAll();
                await $scope.courses.coursesDuplicate($scope.show.currentFolderId);
                if (!$scope.isStartDuplicationCheck)
                    await $scope.updateCourse();
                await $scope.initCoursesByUser();
            }
        };

    }]);
