import {ng} from "entcore";

export const dashboardController = ng.controller('dashboardController', ['$scope',
    ($scope) => {
        /**
         * next and previous button to show courses
         */
        $scope.previousCoursesButton = function (place: string) {
            if (place == "ToDo") {
                $scope.show.firstCoursesToDo -= $scope.count("ToDo");
                $scope.show.lastCoursesToDo -= $scope.count("ToDo");
                if ($scope.show.firstCoursesToDo < 0) {
                    $scope.show.firstCoursesToDo = 0;
                    $scope.show.lastCoursesToDo = $scope.count("ToDo");
                }
            } else if (place == "ToCome") {
                $scope.show.firstCoursesToCome -= $scope.count("ToCome");
                $scope.show.lastCoursesToCome -= $scope.count("ToCome");
                if ($scope.show.firstCoursesToCome < 0) {
                    $scope.show.firstCoursesToCome = 0;
                    $scope.show.lastCoursesToCome = $scope.count("ToCome");
                }
            }
        };

        $scope.nextCoursesButton = function (place: string) {
            if (place == "ToDo") {
                $scope.show.firstCoursesToDo = $scope.show.lastCoursesToDo;
                $scope.show.lastCoursesToDo += $scope.count("ToDo");
            } else if (place == "ToCome") {
                $scope.show.firstCoursesToCome = $scope.show.lastCoursesToCome;
                $scope.show.lastCoursesToCome += $scope.count("ToCome");
            }
        };

        $scope.changeShowCoursesDesktop = async function (place: string) {
            if (place == "ToDo") {
                $scope.show.firstCoursesToDo = 0;
                $scope.show.lastCoursesToDo = $scope.count("ToDo");
                await $scope.courses.setChoice(4);
            } else if (place == "ToCome") {
                $scope.show.firstCoursesToCome = 0;
                $scope.show.lastCoursesToCome = $scope.count("ToCome");
                await $scope.courses.setChoice(5);
            }
        };

        $scope.changeShowCourses = async function (place: string, id: string) {
            if (place == "ToDo") {
                $scope.show.firstCoursesToDo = 0;
                $scope.show.lastCoursesToDo = $scope.count("ToDo");
                $scope.courses.coursestodosort = $scope.courses.typeShow.filter(type => type.id == id);
                await $scope.courses.setChoice(4);
            } else if (place == "ToCome") {
                $scope.show.firstCoursesToCome = 0;
                $scope.show.lastCoursesToCome = $scope.count("ToCome");
                $scope.courses.coursestocomesort = $scope.courses.typeShow.filter(type => type.id == id);
                await $scope.courses.setChoice(5);
            }
        };

    }]);
