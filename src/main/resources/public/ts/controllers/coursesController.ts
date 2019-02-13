import {ng, template, _} from 'entcore';
import {Course,Courses} from "../model";
import {Folder, Folders} from "../model/Folder";
import {Utils} from "../utils/Utils";

export const mainController = ng.controller('MoodleController', ['$scope', 'route','$rootScope',($scope, route,$rootScope) => {

    $scope.lightboxes = {};
	$scope.params = {};
	$scope.currentTab='dashboard';
	$scope.printmenufolder=false;
    $scope.printmenucourseShared=false;
    $scope.currentfolderid=null;
    $scope.printcours=false;
    $scope.printfolders=false;
    $scope.printcreatecoursesrecents=true;
	$scope.courses= new Courses();
    $scope.folders=new Folders();
    $scope.showToaster = false;
    $scope.openLightbox = false;
    $scope.searchbar = {};

    $scope.isPrintMenuFolder= function(){
        $scope.printmenufolder=!$scope.printmenufolder;
        $scope.printmenucourseShared=false;
        if($scope.printmenufolder){
            $scope.printfolders=true;
            $scope.printcours=true;
            $scope.currentfolderid=0;
            $scope.printCouresbySubFolder($scope.currentfolderid);
        }else{
            $scope.printfolders=false;
            $scope.printcours=false;
            $scope.currentfolderid=null;
            $scope.setprintsubfolderValue();
        }
    };
    $scope.isPrintMenuCoursesShared = function(){
        $scope.printmenucourseShared=!$scope.printmenucourseShared;
        $scope.printmenufolder=false;
        $scope.printfolders=false;
        $scope.currentfolderid=null;
        if($scope.printmenucourseShared){
            $scope.printcours = true;
            $scope.initAllCouresbyuser();
        }else{
            $scope.printcours = false;
        }
        $scope.setprintsubfolderValue();
    };
    $scope.setprintsubfolderValue = function (){
        $scope.folders.all.forEach(function (e) {
                e.printsubfolder=false;
        });
        Utils.safeApply($scope);
    };
    $scope.setprintsubfolderValuebyFolder = function (folder:Folder){
        $scope.folders.all.forEach(function (e) {
            if(e.id!=folder.parent_id && e.id!=folder.id && e.id!=0)
            e.printsubfolder=false;
        });
        Utils.safeApply($scope);
    };
    $scope.isPrintSubFolder= function(folder:Folder){
       $scope.folders.all.forEach(function (e) {
           if(e.id==folder.id){
               e.printsubfolder=!e.printsubfolder;
               folder.printsubfolder=e.printsubfolder;
           }
       });

       if(folder.printsubfolder){
            $scope.currentfolderid=folder.id;
            $scope.printcours=true;
           $scope.printCouresbySubFolder(folder.id);
       }else{
           (folder.parent_id!=folder.id)? $scope.currentfolderid=folder.parent_id :$scope.currentfolderid=0;
           $scope.printCouresbySubFolder($scope.currentfolderid);
       }
       $scope.setprintsubfolderValuebyFolder(folder);

    };

    $scope.printCouresbySubFolder= function(idfolder:number){
        $scope.initCouresbyFolder(idfolder);
        $scope.printcours=true;
        $scope.printfolders=true;

    };

    $scope.initCoursesbyuser = async function(){
        Promise.all([
            await $scope.courses.getCoursesbyUser()
        ]).then(()=>{Utils.safeApply($scope)});
    };
	$scope.initCouresbyFolder = async function(idfolder:number){
        Promise.all([
            await $scope.courses.getCoursesbyFolder(idfolder)
        ]).then(()=>{Utils.safeApply($scope)});
    };
    $scope.initAllCouresbyuser = async function(){
        Promise.all([
            await $scope.courses.getCoursesAndSheredbyFolder()
        ]).then(()=>{Utils.safeApply($scope)});
    };

    $scope.initFolders = async function(){
        Promise.all([
            await $scope.folders.sync()
        ]).then(()=>{Utils.safeApply($scope)});
    };

    $scope.getFolderParent= function (): Folder[]{
        return $scope.folders.getparentFolder();
    };
    $scope.getSubFolder= function (folder:Folder): Folder[]{
        return $scope.folders.getSubFolder(folder.id);
    };

    $scope.countItems =async function (folder:Folder){
        Promise.all([
            await folder.countitems()
        ]).then(()=>{Utils.safeApply($scope)});
    };
    $scope.switchTab= function(current: string){
        $scope.currentTab=current;
        if($scope.currentTab=='courses'){
            template.open('main', 'page-courses');
        }else if($scope.currentTab=='dashboard'){
            template.open('main', 'main');
        }else{
            template.open('main', 'page-library');
        }
        Utils.safeApply($scope);
    };
	route({
        view: function(params){
		    template.open('main', 'main');
            Utils.safeApply($scope);

		}
    });
    $scope.openLightboxCourse = false;

    /**
     * Open creation course lightbox
     */
    $scope.openPopUpCourse = function () {
        $scope.course = new Course();
        $scope.openLightboxCourse = true;
    };

    /**
     * Close creation course lightbox
     */
    $scope.closePopUp = function () {
        $scope.openLightboxCourse = false;
    };

    /**
     * Create a course
     */
    $scope.createCourse = function() {
        $scope.course.create();
        $scope.openLightboxCourse = false;
        Utils.safeApply($scope);
    };

    $scope.openLightboxFolder = false;
    /**
     * Create a folder
     */
    $scope.openPopUpFolder = function () {
        $scope.folder = new Folder();
        $scope.openLightboxFolder = true;
    };

    $scope.closePopUpFolder = function () {
        $scope.openLightboxFolder = false;
    };

    $scope.createFolder = async function() {
        $scope.folder.parent_id = $scope.currentfolderid;
        Promise.all([
            await $scope.folder.create()
        ]).then(()=>$scope.initFolders());
        $scope.openLightboxFolder = false;
        $scope.initFolders();
        //setTimeout (()=>$scope.initFolders(), 1000); good
    };
}]);
