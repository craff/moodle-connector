import { ng, template } from 'entcore';

/**
	Wrapper controller
	------------------
	Main controller.
**/

export const mainController = ng.controller('MoodleController', ['$scope', 'route',($scope, route) => {
	$scope.lightboxes = {};
	$scope.params = {};
	
	route({
		view: function(params){
			template.open('main', 'main');
		}
	});
}]);
