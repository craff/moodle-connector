import {ng} from "entcore";
import {Utils} from "../../utils/Utils";

export const shareController = ng.controller('shareController', ['$scope', '$timeout',
    ($scope, $timeout) => {

        $scope.submitShareCourse = async function () {
            $scope.myCourse = undefined;
            $scope.resetSelect();
            $scope.closePopUp();
            $scope.show.toaster = !!($scope.folders.all.some(folder => folder.select) ||
                $scope.courses.allCourses.some(course => course.select));
            await Utils.safeApply($scope);
            $timeout(() =>
                    $scope.initCoursesByUser()
                , 5000)
        };

    }]);
