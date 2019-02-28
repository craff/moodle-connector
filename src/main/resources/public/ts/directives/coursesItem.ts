/**
 * Created by jolloiss on 21/02/2019.
 */
import {ng, appPrefix, template} from 'entcore';
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
            view : '='
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
            }

            /*const maxLineNumber = $scope.place === 'coursesToCome' ?  2 : 6;
            const selector = $scope.place === 'coursesToCome' ? '.clamp-me' : '.big-clamp-me';

            const element = $element[0].querySelector(selector);
            shave(element, maxLineNumber,'...');*/
        }]
    };
});
