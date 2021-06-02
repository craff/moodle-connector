import {ng} from "entcore";
import {Course} from "../../model";
import {Utils} from "../../utils/Utils";

export const coursesController = ng.controller('coursesController', ['$scope',
    ($scope) => {

        $scope.showCourse = function (courseType: string) {
            if (courseType == "topics")
                return $scope.show.typeFilter[0];
            else
                return $scope.show.typeFilter[1];
        };

        $scope.is_in_category = (course : Course): boolean => {
            return ( $scope.show.principal_folder === Utils.PRINCIPAL_FOLDER_TYPE.my_courses
                && course.categoryid !== $scope.courses.publicBankCategoryId)
                || ($scope.show.principal_folder === Utils.PRINCIPAL_FOLDER_TYPE.published
                    && course.categoryid === $scope.courses.publicBankCategoryId)
        };

    }]);
