import {ng, template} from 'entcore';
import {Course,Courses} from "../model";
import {Folder, Folders} from "../model/Folder";


export const mainController = ng.controller('MoodleController', ['$scope', 'route',($scope, route) => {
	$scope.lightboxes = {};
	$scope.params = {};
	$scope.currentTab='courses';
	$scope.printmenufolder=false;
    $scope.printmenusubfolder=false;
    $scope.printcours=false;
    $scope.printfolders=false;
    $scope.printfoldersshared=false;
	$scope.courses= new Courses();
    $scope.folders=new Folders();

    $scope.isprintMenuFolder= function(){
        $scope.printmenufolder=!$scope.printmenufolder;
        $scope.printmenusubfolder=false;
        if($scope.printmenufolder){
            $scope.printfolders=true;
            $scope.printcours=false;
            $scope.printfoldersshared=false;
        }else{
            $scope.printfolders=false;
            $scope.printcours=false;
            $scope.printfoldersshared=false;
        }
    }
    $scope.isprintSubMenuFolder= function(){
        $scope.printmenusubfolder=!$scope.printmenusubfolder;
        $scope.printmenufolder=false;
        if($scope.printmenusubfolder){
            $scope.printfolders=false;
            $scope.printcours=false;
            $scope.printfoldersshared=true;
        }else{
            $scope.printfolders=false;
            $scope.printcours=false;
            $scope.printfoldersshared=false;
        }
    }
    $scope.isprintSubFolder= function(folder:Folder){
       $scope.folders.forEach(e=>{
           if(e.id==folder.id){
               e.printsubfolder=!e.printsubfolder;
               folder.printsubfolder=e.printsubfolder
           }
       });
        if(folder.printsubfolder){
            $scope.printcours=true;
            $scope.printcours=true;
        }
       $scope.$apply();
    }


	$scope.initCoures = async function(idfolder:number){
        Promise.all([
            await $scope.courses.sync(idfolder)
        ]).then( $scope.$apply());
    }
    $scope.initFolders = async function(){
        Promise.all([
            await $scope.folders.sync()
        ]).then( $scope.$apply());
    }
    $scope.printCouresbyFolder= function(idfolder:number){
        $scope.initCoures(idfolder);
        $scope.printcours=true;
        $scope.printfolders=false;
        $scope.$apply();
    }

    $scope.getFolderParent= function (): Folder[]{
        return $scope.folders.getparentFolder();
    }
    $scope.getSubFolder= function (folder:Folder): Folder[]{
        return $scope.folders.getSubFolder(folder.id);
    }
    $scope.printFoldershared= function (){
	    alert("none");
    }
    $scope.countItems =async function (folder:Folder){
        Promise.all([
            await folder.countitems()
        ]).then( $scope.$apply());
    }
    $scope.switchTab= function(current: string){
        $scope.currentTab=current;
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
