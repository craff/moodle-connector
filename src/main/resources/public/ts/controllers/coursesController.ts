import {ng, template} from 'entcore';
import {Course,Courses} from "../model";

export const mainController = ng.controller('MoodleController', ['$scope', 'route',($scope, route) => {
	$scope.lightboxes = {};
	$scope.params = {};

	$scope.courses= new Courses();

	$scope.initCoures = async function(){
        Promise.all([
            await $scope.courses.sync(1)
        ]).then( $scope.$apply());
    }
	route({
		view: function(params){
			template.open('main', 'main');
		}
	});

    $scope.openLightbox = false;

    /**
     * Create a course
     */
    $scope.openPopUp = function () {
        $scope.course = new Course();
        $scope.openLightbox = true;
    };

    $scope.openDeletePopUp = function () {
        $scope.course = new Course();
        $scope.course.delete();
    };

    $scope.closePopUp = function () {
        $scope.openLightbox = false;
    };

    $scope.createCourse = function() {
        $scope.course.create();
    };
}]);
