/**
 * Created by jolloiss on 21/02/2019.
 */
import {ng, appPrefix, template,_} from 'entcore';
import {Utils} from "../utils/Utils";
//import shave from 'yaclamp';

export let coursesItem = ng.directive("coursesItem", function(){
    return {
        restrict : 'E',
        scope : {
            place : '=',
            course : '=',
            check: '=',
            dateFunction : '=',
            authorFunction : '=',
            view : '=',
            first : '='
        },
        templateUrl : `/${appPrefix}/public/template/directives/coursesItem.html`,
        controller : ['$scope', '$element', function($scope, $element) {
            $scope.checkCourse = () => {
                $scope.check();
            };
            $scope.printDate = (item,number) => {
                return $scope.dateFunction(item,number);
            };
            $scope.printAuthor = (item,number) => {
                return $scope.authorFunction(item,number);
            };
        }]
    };
});
