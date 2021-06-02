/**
 * Created by jolloiss on 21/02/2019.
 */
import {appPrefix, ng, $, moment} from 'entcore';
import {Course} from "../model";

export let coursesItem = ng.directive("coursesItem", function () {

    return {
        restrict: 'E',
        scope: {
            place: '=',
            course: '=',
            check: '=',
            refreshFunction: '=',
            view: '=',
            first: '='
        },
        templateUrl: `/${appPrefix}/public/template/directives/coursesItem.html`,
        controller: ['$scope','$timeout', function ($scope,$timeout) {
            /**
             * print the right format of course data
             */
            $scope.printDate = (course, spec) => {
                let format = "DD/MM/YYYY";
                if (spec == "modified") {
                    if (course.timemodified.toString() == course.date.toString())
                        return "Créé le : " + moment(course.timemodified.toString() + "000", "x").format(format);
                    else
                        return "Modifié le : " + moment(course.timemodified.toString() + "000", "x").format(format);
                } else if (spec == "enddate")
                    return moment(course.enddate + "000", "x").format(format);
                else if (spec == "begindate")
                    return moment(course.startdate + "000", "x").format(format);
                return moment();
            };
            $scope.printAuthor = (course : Course) => {
                let author = "";
                if (course.auteur[0] !== null && course.auteur[0] !== undefined && course.auteur[0].firstname !== null &&
                    course.auteur[0].lastname !== null)
                    author = course.auteur[0].firstname[0] + ". " + course.auteur[0].lastname[0].toUpperCase() +
                        course.auteur[0].lastname.slice(1).toLowerCase();
                return author;
            };
            $scope.deleteDuplicateFailed = (item) => {
                item.deleteDuplication();
                $scope.refreshFunction();
            };
            $(document).click(function() {
                let $opener = $('.opener');
                $opener.click(function () {
                    if ($opener.offsetParent().hasClass('opened')) {
                        $timeout(() =>
                                $opener.offsetParent().removeClass('opened')
                            , 10);
                    }
                });
            });
        }]
    };
});
