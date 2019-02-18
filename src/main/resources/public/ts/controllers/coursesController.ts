import {ng, template, _} from 'entcore';
import {Course,Courses} from "../model";
import {Folder, Folders} from "../model/Folder";
import {Utils} from "../utils/Utils";
import http from "axios";

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

    $scope.setprintsubfolderValuebyFolder = function (folder:Folder, printsubfolder:boolean){
        $scope.folders.all.forEach(function (e) {
                e.printsubfolder=false;
        });
        folder.printsubfolder=printsubfolder;
        $scope.parent= folder.parent_id;
        while ($scope.parent != 0){
            $scope.folders.all.forEach(function (e) {
                if(e.id == $scope.parent) {
                    e.printsubfolder = true;
                    $scope.parent = e.parent_id;
                }
            });
        }
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
           $scope.currentfolderid=folder.parent_id;
           $scope.printCouresbySubFolder($scope.currentfolderid);
       }
       $scope.setprintsubfolderValuebyFolder(folder, folder.printsubfolder);

    };

    $scope.targetFolder = undefined;

    $scope.isPrintTargetSubFolder= function(folder:Folder){
        $scope.targetFolder=folder.id;
        $scope.folders.all.forEach(function (e) {
            if(e.id==folder.id){
                e.printTargetsubfolder=!e.printTargetsubfolder;
                folder.printTargetsubfolder=e.printTargetsubfolder;
            }
        });
        $scope.setprintTargetsubfolderValuebyFolder(folder, folder.printTargetsubfolder);
    };

    $scope.targetMenu = function(){
        $scope.targetFolder= 0;
        $scope.folders.all.forEach(function (e) {
             e.printTargetsubfolder=false;
        });
    };

    $scope.isSelectRoot = function(){
        $scope.targetFolder = 0;
    };

    $scope.setprintTargetsubfolderValuebyFolder = function (folder:Folder, printTargetsubfolder:boolean){
        $scope.folders.all.forEach(function (e) {
            e.printTargetsubfolder=false;
        });
        folder.printTargetsubfolder=printTargetsubfolder;
        $scope.parent= folder.parent_id;
        while ($scope.parent != 0){
            $scope.folders.all.forEach(function (e) {
                if(e.id == $scope.parent) {
                    e.printTargetsubfolder = true;
                    $scope.parent = e.parent_id;
                }
            });
        }
        Utils.safeApply($scope);
    };

    $scope.setInitialprintsubfolderValuebyFolder = function (targetFolder:number){
        $scope.folders.all.forEach(function (e) {
            e.printsubfolder=false;
        });

        $scope.folders.all.forEach(function (e) {
            if(e.id === targetFolder)
            e.printsubfolder=true;
            $scope.parent= e.parent_id;
        });
        while ($scope.parent != 0){
            $scope.folders.all.forEach(function (e) {
                if(e.id === $scope.parent) {
                    e.printsubfolder = true;
                    $scope.parent = e.parent_id;
                }
            });
        }
        Utils.safeApply($scope);
    };

    $scope.printCouresbySubFolder= function(idfolder:number){
        $scope.initCouresbyFolder(idfolder);
        $scope.printcours=true;
        $scope.printfolders=true;

    };

    $scope.initCoursesbyuser = async function(){
            await $scope.courses.getCoursesbyUser();
            Utils.safeApply($scope);
    };
	$scope.initCouresbyFolder = async function(idfolder:number){
            await $scope.courses.getCoursesbyFolder(idfolder);
            Utils.safeApply($scope);
    };
    $scope.initAllCouresbyuser = async function(){
            await $scope.courses.getCoursesAndSharedByFolder();
            Utils.safeApply($scope);
    };

    $scope.initFolders = async function(){
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
        await $scope.folder.create();
        $scope.openLightboxFolder = false;
        $scope.initFolders();
        //setTimeout (()=>$scope.initFolders(), 1000); good
    };

    $scope.openLightboxMove = false;
    /**
     * Move a folder
     */
    $scope.openPopUpMoveFolder = function () {
        $scope.folders.all.forEach(function (e) {
            if(e.selected){
                $scope.folders.selectedFolders.push(e.id);
                $scope.folders.listSelectedFolders.push(e);
            }
        });
        $scope.openLightboxMove = true;
    };

    $scope.closePopUpMoveFolder = function () {
        $scope.targetFolder = undefined;
        $scope.openLightboxMove = false;
    };

    $scope.moveFolder = async function() {
        await $scope.folders.moveFolders($scope.targetFolder);
        $scope.initFolders();
        $scope.setInitialprintsubfolderValuebyFolder($scope.targetFolder);
        $scope.refreshSelectedFolders();
        $scope.targetFolder = undefined;
        $scope.openLightboxMove = false;
    };
    $scope.refreshSelectedFolders = function(){
        $scope.folders.all.forEach(function (e) {
            e.selected=false;
        });
    };

    template.open('toaster', 'toaster');

    $scope.oneOrMoreFolderSelected = function(){
        $scope.count=0;
        $scope.folders.all.forEach(function (e) {
            if(e.selected){
                $scope.count ++;
            }
        });
        if ($scope.count>0)
            return true;
        else
            return false;
    }



}]);
