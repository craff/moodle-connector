import {model, ng, template} from 'entcore';
import {Course, Courses} from "../model";
import {Folder, Folders} from "../model/Folder";
import {Utils} from "../utils/Utils";

export const mainController = ng.controller('MoodleController', ['$scope', '$timeout', 'route','$rootScope', ($scope, $timeout, route,$rootScope) => {


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

    $scope.switchTab = function(current: string){
        $scope.currentTab = current;
        if($scope.currentTab == 'courses'){
            $scope.initCoursesTab();
        }else if($scope.currentTab == 'dashboard'){
            $scope.initDashBoardTab();
        }else if($scope.currentTab == 'library'){
            $scope.initLibraryTab();
        } else {
            $scope.initDashBoardTab();
        }
    };

    $scope.initDashBoardTab = async function(){
        $scope.currentTab = 'dashboard';
        // TODO recupérer de la bdd, selon le choix de l'utilisateur connecté


        if($scope.courses.isSynchronized === undefined || $scope.courses.isSynchronized === false) {
            await $scope.courses.getCoursesbyUser(model.me.userId);
        }

        await $scope.courses.getChoice();

        template.open('main', 'dashboard/dashboard_home');
        Utils.safeApply($scope);
    };

    $scope.initCoursesTab = async function(){
        $scope.currentTab ='courses';
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
        $scope.courses = new Courses();
        $scope.currentTab ='dashboard';
        $scope.lightboxes = {};
        $scope.params = {};
        $scope.printmenufolder = false;
        $scope.printmenucourseShared =false;
        $scope.currentfolderid = 0;
        $scope.printcours = false;
        $scope.printfolders = false;
        $scope.folders = new Folders();
        $scope.toasterShow = false;
        $scope.openLightbox = false;
        $scope.searchbar = {};
        $scope.openLightboxFolder = false;
        $scope.lightboxFolderMove = false;
        $scope.successDelete = false;
        $scope.typeFilter = [true, true];
        $scope.nbFoldersSelect = 0;
        $scope.nbCoursesSelect = 0;
        $scope.disableDeleteSend = true;
        $scope.typeShowCourses = ["Tout","Autres"];
        $scope.firstCoursesToDo = 0;
        $scope.lastCoursesToDo = 5;
        $scope.firstCoursesToCome = 0;
        $scope.lastCoursesToCome = 5;
        $scope.showToDoCourses = $scope.typeShowCourses[0];
        $scope.showToComeCourses = $scope.typeShowCourses[0];
    };

    $scope.isPrintMenuFolder = function() {
        $scope.printmenufolder = !$scope.printmenufolder;
        $scope.printmenucourseShared = false;
        if ($scope.printmenufolder) {
            $scope.printfolders = true;
            $scope.printcours = true;
            $scope.currentfolderid = 0;
            $scope.printCouresbySubFolder($scope.currentfolderid);
        } else {
            $scope.printfolders = false;
            $scope.printcours = false;
            $scope.currentfolderid = 0;
            $scope.setprintsubfolderValue();
        }
    };
    $scope.isPrintMenuCoursesShared = function() {
        $scope.printmenucourseShared = !$scope.printmenucourseShared;
        $scope.printmenufolder = false;
        $scope.printfolders = false;
        $scope.currentfolderid = 0;
        if ($scope.printmenucourseShared) {
            $scope.printcours = true;
            $scope.initAllCouresbyuser();
        } else {
            $scope.printcours = false;
        }
        $scope.setprintsubfolderValue();
    };
    $scope.setprintsubfolderValue = function() {
        $scope.folders.all.forEach(function(e) {
                e.printsubfolder = false;
        });
        Utils.safeApply($scope);
    };
    $scope.setprintsubfolderValuebyFolder = function(folder:Folder, printsubfolder: boolean) {
        $scope.folders.all.forEach(function(e) {
            if(e.id != folder.parent_id && e.id != folder.id && e.id != 0)
            e.printsubfolder=false;
        });
        folder.printsubfolder = printsubfolder;
        $scope.parent= folder.parent_id;
        while ($scope.parent != 0) {
            $scope.folders.all.forEach(function(e) {
                if (e.id == $scope.parent) {
                    e.printsubfolder = true;
                    $scope.parent = e.parent_id;
                }
            });
        }
        Utils.safeApply($scope);
    };
    $scope.isPrintSubFolder = function(folder:Folder){
       $scope.folders.all.forEach(function (e) {
           if(e.id == folder.id){
               e.printsubfolder = !e.printsubfolder;
               folder.printsubfolder = e.printsubfolder;
           }
       });

       if(folder.printsubfolder){
            $scope.currentfolderid = folder.id;
            $scope.printcours = true;
           $scope.printCouresbySubFolder(folder.id);
       }else{
           (folder.parent_id != folder.id) ? $scope.currentfolderid = folder.parent_id : $scope.currentfolderid=0;
           $scope.printCouresbySubFolder($scope.currentfolderid);
       }
        $scope.setprintsubfolderValuebyFolder(folder, folder.printsubfolder);
    };

    $scope.printCouresbySubFolder = function(idfolder:number){
        $scope.initCouresbyFolder(idfolder);
        $scope.printcours = true;
        $scope.printfolders = true;

    };

    $scope.initCoursesbyuser = async function(){
        await $scope.courses.getCoursesbyUser();
        Utils.safeApply($scope);
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
        $scope.resetSelect();
        $scope.countItems();
        $scope.toasterShow = false;
        Utils.safeApply($scope);
    };

    $scope.getFolderParent = function (): Folder[]{
        return $scope.folders.getparentFolder();
    };
    $scope.getSubFolder = function (folder:Folder): Folder[]{
        return $scope.folders.getSubFolder(folder.id);
    };

    $scope.countItems =async function (folder:Folder){
        if(folder){
            await folder.countItemsModel();
        }
    };

    /**
     * Open creation course lightbox
     */
    $scope.openPopUp = function () {
        $scope.course = new Course();
        template.open('ligthBoxContainer', 'courses/createCourseLightbox');
        $scope.openLightbox = true;
    };
    /**
     * Close lightbox
     */
    $scope.closePopUp = function () {
        $scope.openLightbox = false;
        $scope.folders.all.filter(folder => folder.select).map(folder => folder.selectConfirm= false );
        $scope.courses.allCourses.filter(course => course.select).map(course => course.selectConfirm= false );
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
    };

    /**
     * Filter Type
     */
    $scope.checkTypeFilterSelected = function(id:number) {
        $scope.typeFilter[id] = !$scope.typeFilter[id];
    };

    $scope.showCourse = function(courseType:string) {
        if(courseType == "topics")
            return $scope.typeFilter[0];
        else
            return $scope.typeFilter[1];
    };

    $scope.getAllFolders = function (){
        return $scope.folders.getAllFoldersModel();
    };
    $scope.resetSelect = function (){
        $scope.folders.all.map(folder => folder.select= false );
        $scope.courses.allCourses.map(course => course.select= false );
    };
    /**
     * toaster show
     * */
    $scope.showToaster = function (){
        template.open('toaster', 'toaster');
        $scope.toasterShow = !!($scope.folders.all.some(folder => folder.select) || $scope.courses.allCourses.some(course => course.select));
        $scope.countFoldersCourses();
    };
    /**
     * count folders and courses select
     * */
    $scope.countFoldersCourses = function (){
        $scope.nbFoldersSelect = $scope.folders.all.filter(folder => folder.select).length;
        $scope.nbCoursesSelect = $scope.courses.allCourses.filter(course => course.select).length;

    };
    /**
     * create folder
     * */
    $scope.openPopUpFolder = function () {
        $scope.folder = new Folder();
        template.open('ligthBoxContainer', 'courses/createFolderLightbox');
        $scope.openLightbox = true;
    };

    $scope.createFolder = async function() {
        $scope.folder.parent_id = $scope.currentfolderid;
        await $scope.folder.create();
        $scope.initFolders();
        $scope.openLightbox = false;
    };
    /**
    * delete elements
    * */

    $scope.openPopUpDelete = function () {
        template.open('ligthBoxContainer', 'courses/deleteLightbox');
        $scope.folders.all.filter(folder => folder.select).map(folder => folder.selectConfirm= true );
        $scope.courses.allCourses.filter(course => course.select).map(course => course.selectConfirm= true );
        $scope.openLightbox = true;
    };
    $scope.hideSuccessDelete = function(){
        $scope.successDelete = false;
    };
    $scope.deleteElements = async function (){
        if($scope.folders.all.some(folder => folder.selectConfirm)){
            await $scope.folders.foldersDelete();
        }
        if($scope.courses.allCourses.some(course => course.selectConfirm)){
            await $scope.courses.coursesDelete();
        }
        $scope.openLightbox = false;
        $scope.successDelete = true;
        $timeout(()=>
            $scope.successDelete = false
        , 3000);
        $scope.initFolders();
    };
    /**
     * move folders
     * */
    $scope.openPopUpFolderMove = function () {
        template.open('ligthBoxContainer', 'courses/moveElementLightbox');
        $scope.openLightbox = true;
    };
    $scope.foldersMove = async function (){
        await $scope.folders.moveFolders();
        $scope.initFolders();
        $scope.openLightbox = false;
    };
    /**
     * confirm delete
     * */
    $scope.confirmDeleteSend = function (){
         $scope.disableDeleteSend = !!($scope.folders.all.some(folder => folder.selectConfirm) || $scope.courses.allCourses.some(course => course.selectConfirm));
    };

    /**
     * next and previous button to show courses
     * */

    $scope.previousCoursesToDoButton = function () {
        $scope.firstCoursesToDo -=5;
        $scope.lastCoursesToDo -=5;
    };
    $scope.nextCoursesToDoButton = function () {
        $scope.firstCoursesToDo +=5;
        $scope.lastCoursesToDo +=5;
    };

    $scope.previousCoursesToComeButton = function () {
        $scope.firstCoursesToCome -=5;
        $scope.lastCoursesToCome -=5;
    };

    $scope.nextCoursesToComeButton = function () {
        $scope.firstCoursesToCome +=5;
        $scope.lastCoursesToCome +=5;
    };

}]);
