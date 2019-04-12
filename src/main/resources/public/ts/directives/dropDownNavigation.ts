/**
 * Created by jolloiss on 12/04/2019.
 */
import {ng, appPrefix,_} from 'entcore';

export let dropDownNavigation = ng.directive("dropDownNavigation", function(){
    return {
        restrict : 'E',
        templateUrl : `/${appPrefix}/public/template/directives/dropDownNavigation.html`,
        controller : ['$scope', function($scope) {
            /* When the user clicks on the button, toggle between hiding and showing the dropdown content */
            $scope.showDropDownMenu = function() {
                document.getElementById("myDropdown").classList.toggle("show");
            };
            // Close the dropdown if the user clicks outside of it
            window.onclick = function(event) {
                if (!event.target.classList.contains('dropbtn')) {
                    var dropdowns = document.getElementsByClassName("dropdown-content");
                    var i;
                    for (i = 0; i < dropdowns.length; i++) {
                        var openDropdown = dropdowns[i];
                        if (openDropdown.classList.contains('show')) {
                            openDropdown.classList.remove('show');
                        }
                    }
                }
            };
        }]
    };
});