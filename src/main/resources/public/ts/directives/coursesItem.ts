/**
 * Created by jolloiss on 21/02/2019.
 */
import {appPrefix, ng} from 'entcore';

export let coursesItem = ng.directive("coursesItem", function () {

    return {
        restrict: 'E',
        scope: {
            place: '=',
            course: '=',
            check: '=',
            dateFunction: '=',
            authorFunction: '=',
            refreshFunction: '=',
            view: '=',
            first: '='
        },
        templateUrl: `/${appPrefix}/public/template/directives/coursesItem.html`,
        controller: ['$scope', '$element', function ($scope, $element) {
            $scope.printDate = (item, number) => {
                return $scope.dateFunction(item, number);
            };
            $scope.printAuthor = (item, number) => {
                return $scope.authorFunction(item, number);
            };
            $scope.deleteDuplicateFailed = (item) => {
                item.deleteDuplication();
                $scope.refreshFunction();
            };
        }]
    };
});
