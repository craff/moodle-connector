import {ng, routes} from 'entcore';
import * as controllers from './controllers';
import * as directives from './directives';

for (let directive in directives) {
    ng.directives.push(directives[directive]);
}
for(let controller in controllers){
    ng.controllers.push(controllers[controller]);
}

//declare let require: any;

routes.define(function($routeProvider){
	$routeProvider
        .when('/dashboard', {
            action: 'dashboard'
        })
		.when('/', {
			action: 'dashboard'
		})
        .when('/courses', {
            action: 'courses'
        })
        .when('/library', {
            action: 'library'
        })
        .otherwise({
        redirectTo: '/'
    });
});

//require("ng-clamp1");
//ng.addRequiredModule('ng-clamp');