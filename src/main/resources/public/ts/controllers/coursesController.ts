import {model, ng, template} from 'entcore';
import {Course, Courses} from "../model";
import {Folder, Folders} from "../model/Folder";
import {Utils} from "../utils/Utils";

export const mainController = ng.controller('MoodleController', ['$scope', 'route','$rootScope',($scope, route,$rootScope) => {

     route({
        dashboard: function(params){
            $scope.initController();
            $scope.initDashBoardTab();
        },
        courses: function(params){
            $scope.initController();
            $scope.initCoursesTab();
        },
         library: function(params){
             $scope.initController();
             $scope.initLibraryTab();
         }

    });

    $scope.switchTab= function(current: string){
        $scope.currentTab=current;
        if($scope.currentTab=='courses'){
            $scope.initCoursesTab();
        }else if($scope.currentTab=='dashboard'){
            $scope.initDashBoardTab();
        }else if($scope.currentTab=='library'){
            $scope.initLibraryTab();
        } else {
            $scope.initDashBoardTab();
        }
    };

    $scope.initDashBoardTab = async function(){
        $scope.currentTab='dashboard';
        // TODO recupérer de la bdd, selon le choix de l'utilisateur connecté
        $scope.printcreatecoursesrecents=true;


        if($scope.courses.isSynchronized === undefined || $scope.courses.isSynchronized === false) {
            await $scope.courses.getCoursesbyUser(model.me.userId);
        }

        template.open('main', 'dashboard/dashboard_home');
        Utils.safeApply($scope);
    };

    $scope.initCoursesTab = async function(){
        $scope.currentTab='courses';
        // TODO ne charger que si besoin
        if($scope.courses.isSynchronized === undefined || $scope.courses.isSynchronized === false) {
            await $scope.courses.getCoursesbyUser(model.me.userId);
        }

        // TODO gestion des dossiers
        // if($scope.folders.isSynchronized === undefined || $scope.folders.isSynchronized === false) {
        //     $scope.initFolders();
        // }

        template.open('main', 'my-courses');
        Utils.safeApply($scope);
    };

    $scope.initLibraryTab = async function() {
        $scope.currentTab = 'library';
        template.open('main', 'page-library');
        Utils.safeApply($scope);
    };

    $scope.initController = async function () {
        $scope.courses= new Courses();
        $scope.currentTab='dashboard';
        $scope.lightboxes = {};
        $scope.params = {};
        $scope.printmenufolder=false;
        $scope.printmenucourseShared=false;
        $scope.currentfolderid=null;
        $scope.printcours=false;
        $scope.printfolders=false;

        $scope.folders=new Folders();
        $scope.showToaster = false;
        $scope.openLightbox = false;
        $scope.searchbar = {};
    };



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


	$scope.initCouresbyFolder = async function(idfolder:number){
        await $scope.courses.getCoursesbyFolder(idfolder);
        Utils.safeApply($scope);
    };

	/*$scope.initAllCouresbyuser = async function(){
        await $scope.courses.getCoursesAndSheredbyFolder();
        Utils.safeApply($scope);
    };*/

    $scope.initFolders = async function(){
        $scope.folders = new Folders();
        await $scope.folders.sync();
        Utils.safeApply($scope);
    };

    $scope.getFolderParent= function (): Folder[]{
        return $scope.folders.getparentFolder();
    };
    $scope.getSubFolder= function (folder:Folder): Folder[]{
        return $scope.folders.getSubFolder(folder.id);
    };

    $scope.countItems =async function (folder:Folder){
        await folder.countitems();
        Utils.safeApply($scope);
    };


    /**
     * Open creation course lightbox
     */
    $scope.openPopUp = function () {
        $scope.course = new Course();
        $scope.openLightbox = true;
    };

    /**
     * Close creation course lightbox
     */
    $scope.closePopUp = function () {
        $scope.openLightbox = false;
    };

    /**
     * Create a course
     */
    $scope.createCourse = function() {
        // TODO get current folder id
        $scope.course.folderid = $scope.currentfolderid;
        $scope.course.create();
        $scope.openLightbox = false;
        Utils.safeApply($scope);
    };

    $scope.deleteCourse = function () {
        $scope.course.delete();
        Utils.safeApply($scope);
    }
}]);
