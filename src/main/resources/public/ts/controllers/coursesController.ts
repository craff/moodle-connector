import {_, model, moment, ng, template, idiom} from "entcore";
import {Course, Courses} from "../model";
import {Folder, Folders} from "../model/Folder";
import {Utils} from "../utils/Utils";
import http from "axios";

export const mainController = ng.controller('MoodleController', ['$scope', '$timeout', 'route', '$rootScope', '$interval', ($scope, $timeout, route, $rootScope, $interval) => {


    route({
        dashboard: function (params) {
            $scope.initController();
            $scope.initDashBoardTab();
        },
        courses: function (params) {
            $scope.initController();
            $scope.initCoursesTab();
        },
        library: function (params) {
            $scope.initController();
            $scope.initLibraryTab();
        }

    });

    $scope.switchTab = function (current: string) {

        $scope.toasterShow = false;
        $scope.currentTab = current;
        $scope.resetSelect();
        if ($scope.currentTab == 'courses') {
            $scope.initCoursesTab();
        } else if ($scope.currentTab == 'dashboard') {
            $scope.initDashBoardTab();
        } else if ($scope.currentTab == 'library') {
            $scope.initLibraryTab();
        } else {
            $scope.initDashBoardTab();
        }
    };

    $scope.initDashBoardTab = async function () {
        $scope.currentTab = 'dashboard';
        template.open('main', 'dashboard/dashboard_home');
        await $scope.courses.getChoice();
        Utils.safeApply($scope);
        // TODO recupérer de la bdd, selon le choix de l'utilisateur connecté

        if ($scope.courses.isSynchronized === undefined || $scope.courses.isSynchronized === false) {
            $scope.displayMessageLoader = true;
            Utils.safeApply($scope);
            await $scope.courses.getCoursesbyUser(model.me.userId);
            $scope.displayMessageLoader = false;
        }

        Utils.safeApply($scope);
    };

    $scope.initCoursesTab = async function () {
        $scope.currentTab = 'courses';
        template.open('main', 'my-courses');
        Utils.safeApply($scope);
        // TODO ne charger que si besoin
        if ($scope.courses.isSynchronized === undefined || $scope.courses.isSynchronized === false) {
            $scope.displayMessageLoader = true;
            Utils.safeApply($scope);
            await $scope.courses.getCoursesbyUser(model.me.userId);
            $scope.displayMessageLoader = false;
        }
        Utils.safeApply($scope);
    };

    $scope.initLibraryTab = async function () {
        $scope.currentTab = 'library';
        template.open('main', 'page-library');
        Utils.safeApply($scope);

    };

    $scope.initController = async function () {
        $scope.toasterShow = false;
        $scope.courses = new Courses();
        $scope.currentTab = 'dashboard';
        $scope.lightboxes = {};
        $scope.params = {};
        $scope.printmenufolder = true;
        $scope.printmenucourseShared = false;
        $scope.currentfolderid = 0;
        $scope.printfolders = true;
        $scope.folders = new Folders();
        $scope.openLightbox = false;
        $scope.searchbar = {};
        $scope.openLightboxFolder = false;
        $scope.lightboxFolderMove = false;
        $scope.successDelete = false;
        $scope.successDuplicate = false;
        $scope.typeFilter = [true, true];
        $scope.nbFoldersSelect = 0;
        $scope.nbCoursesSelect = 0;
        $scope.disableDeleteSend = true;
        $scope.disableDuplicateSend = true;
        $scope.typeShow = {
            availableOptions: [
                {id: 'all', name: 'Tout'},
                {id: 'doing', name: 'En cours'},
                {id: 'favorites', name: 'Favoris'},
                {id: 'finished', name: 'Terminés'},
                {id: 'masked', name: 'Masqués'}
            ],
            selectedToDoOption: {id: 'doing', name: 'En cours'},
            selectedToComeOption: {id: 'all', name: 'Tout'}
        };
        $scope.firstCoursesToDo = 0;
        $scope.lastCoursesToDo = $scope.countToDo();
        $scope.firstCoursesToCome = 0;
        $scope.lastCoursesToCome = $scope.countToCome();
        $scope.showInfoSharePanel = false;
        $scope.viewModeToDo = "icons";
        $scope.viewModeToCome = "icons";
        $scope.initFolders();
        $scope.imgCompatibleMoodle = false;
        $scope.typeActivity = {
            availableOptions: [
                {id: 'quiz', name: 'Quizz'},
                {id: 'resource', name: 'Fichier'},
                {id: 'page', name: 'Page'},
                {id: 'assign', name: 'Devoir'}
            ],
            selectedOption: {id: undefined, name: 'Choisissez votre type'}
        };
        $scope.nameFolder="";
        if ($(window).width() < 800) {
            if($scope.typeShow.selectedToDoOption.id == 'doing' || $scope.typeShow.selectedToDoOption.id == 'finished')
                $scope.typeShow.selectedToDoOption.id = "all";
            $scope.viewModeToDo = "icons";
            $scope.viewModeToCome = "icons";
        }
    };

    $scope.isPrintMenuFolder = function () {
        if($scope.currentfolderid != 0 || $scope.printmenucourseShared) {
            $scope.initFolders();
            $scope.printmenufolder = true;
            $scope.printmenucourseShared = false;
            $scope.currentfolderid = 0;
            $scope.printfolders = true;
        }
    };
    $scope.isPrintMenuCoursesShared = function () {
        $scope.printmenucourseShared = true;
        $scope.printmenufolder = false;
        $scope.printfolders = false;
        $scope.courses.order = {
            field: "modificationDate",
            desc: false
        };
        $scope.currentfolderid = 0;
        $scope.initAllCouresbyuser();
        $scope.setprintsubfolderValue();
    };

    $scope.setprintsubfolderValue = function () {
        $scope.folders.all.forEach(function (e) {
            e.printsubfolder = false;
        });
        Utils.safeApply($scope);
    };

    $scope.setprintsubfolderValuebyFolder = function (folder: Folder, printsubfolder: boolean) {
        $scope.folders.all.forEach(function (e) {
            if (e.id != folder.parent_id && e.id != folder.id && e.id != 0)
                e.printsubfolder = false;
        });
        folder.printsubfolder = printsubfolder;
        $scope.parent = folder.parent_id;
        while ($scope.parent != 0) {
            $scope.folders.all.forEach(function (e) {
                if (e.id == $scope.parent) {
                    e.printsubfolder = true;
                    $scope.parent = e.parent_id;
                }
            });
        }
        Utils.safeApply($scope);
    };

    $scope.isPrintSubFolder= function(folder:Folder){
        $scope.folders.all.forEach(function (e) {
            if (e.id == folder.id) {
                e.printsubfolder = !e.printsubfolder;
                folder.printsubfolder = e.printsubfolder;
            }
        });
       $scope.printfolders = true;
       if(folder.printsubfolder) {
           $scope.currentfolderid = folder.id;
           $scope.folders.folderIdMoveIn = $scope.currentfolderid;
       }
       else {
           $scope.currentfolderid = folder.parent_id;
           $scope.folders.folderIdMoveIn = $scope.currentfolderid;
       }
       $scope.setprintsubfolderValuebyFolder(folder, folder.printsubfolder);

    };

    $scope.isPrintSubFolderNumber= function(folderId:number){
        $scope.folders.all.forEach(function (e) {
            if (e.id == folderId) {
                e.printsubfolder = true;
                $scope.printfolders = true;
                $scope.currentfolderid=folderId;
                $scope.setprintsubfolderValuebyFolder(e, true);
            }
        });
    };


    $scope.isPrintTargetSubFolder= function(folder:Folder){
        $scope.folders.folderIdMoveIn = folder.id;
        $scope.folders.all.forEach(function (e) {
            if(e.id==folder.id){
                e.printTargetsubfolder=!e.printTargetsubfolder;
                folder.printTargetsubfolder=e.printTargetsubfolder;
            }
        });
        $scope.setprintTargetsubfolderValuebyFolder(folder, folder.printTargetsubfolder);
    };

    $scope.targetMenu = function(){
        $scope.folders.folderIdMoveIn= 0;
        $scope.folders.all.forEach(function (e) {
             e.printTargetsubfolder=false;
        });
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
        $scope.folders.all.forEach(function (e) {
            if(e.parent_id === 0) {
                e.printsubfolder = true;
            }
        });
        Utils.safeApply($scope);
    };

    $scope.initCoursesbyuser = async function () {
        await $scope.courses.getCoursesbyUser(model.me.userId);
        Utils.safeApply($scope);
    };

    $scope.initAllCouresbyuser = async function(){
            await $scope.courses.getCoursesAndSharedByFolder();
            Utils.safeApply($scope);
    };

    $scope.initFolders = async function () {
        $scope.folders = new Folders();
        await $scope.folders.sync();
        $scope.resetSelect();
        $scope.showToaster();
        $scope.isPrintSubFolderNumber($scope.currentfolderid)
        $scope.folders.getAllsubfolders();
        Utils.safeApply($scope);
    };


    $scope.getFolderParent = function (): Folder[] {
        return $scope.folders.getparentFolder();
    };
    $scope.getSubFolder = function (folder: Folder): Folder[] {
        return $scope.folders.getSubFolder(folder.id);
    };

	route({
        view: function(params){
		    template.open('main', 'main');
            Utils.safeApply($scope);

		}
    });

    /**
     * Open creation course lightbox
     */
    $scope.openPopUp = function () {
        $scope.folders.folderIdMoveIn = $scope.currentfolderid;
        Utils.safeApply($scope);
        $scope.course = new Course();
        template.open('ligthBoxContainer', 'courses/createCourseLightbox');
        $scope.openLightbox = true;
        $scope.folders.folderIdMoveIn = $scope.currentfolderid;
        Utils.safeApply($scope);
    };

    /**
     * Close lightbox
     */
    $scope.closePopUp = function () {
        $scope.openLightbox = false;
        $scope.folders.all.filter(folder => folder.select).map(folder => folder.selectConfirm = false);
        $scope.courses.allCourses.filter(course => course.select).map(course => course.selectConfirm = false);
        $scope.folders.folderIdMoveIn = undefined;
    };

    /**
     * Create a course
     */
    $scope.createCourse = async function () {
        // TODO get current folder id
        $scope.course.folderid = parseInt($scope.folders.folderIdMoveIn);
        $scope.course.submitWait = true;
        await $scope.course.create();
        await $scope.courses.getCoursesbyUser(model.me.userId);
        $scope.openLightbox = false;
        $scope.showToaster();
        $scope.currentfolderid = $scope.folders.folderIdMoveIn;
        $scope.initFolders();
        $scope.course.submitWait = false;
        $scope.folders.folderIdMoveIn = undefined;
        Utils.safeApply($scope);
    };

    $scope.changeTypeA = function(course : Course) {
        course.typeA = $scope.typeActivity.selectedOption.id;
    };

    /**
     * Filter Type
     */
    $scope.checkTypeFilterSelected = function (id: number) {
        $scope.typeFilter[id] = !$scope.typeFilter[id];
    };

    $scope.showCourse = function (courseType: string) {
        if (courseType == "topics")
            return $scope.typeFilter[0];
        else
            return $scope.typeFilter[1];
    };

    $scope.getAllFolders = function () {
        return $scope.folders.getAllFoldersModel();
    };

    $scope.getAllCourses = function () {
        return $scope.courses.getAllCoursesModel();
    };

    $scope.resetSelect = function () {
        $scope.folders.all.map(folder => folder.select = false);
        $scope.courses.allCourses.map(course => course.select = false);
    };
    /**
     * toaster show
     * */
    $scope.showToaster = function () {
        $scope.myCourse = undefined;
        template.open('toaster', 'toaster');
        $scope.toasterShow = !!($scope.folders.all.some(folder => folder.select) || $scope.courses.allCourses.some(course => course.select));
        $scope.countFoldersCourses();
        if($scope.nbCoursesSelect == 1)
            $scope.myCourse = _.findWhere($scope.courses.coursesByUser, {select: true});
        if ($scope.toasterShow === true) {
            $scope.selectedCourse = _.findWhere($scope.courses.coursesByUser, {select: true});
        }
    };

    /**
     * count folders and courses select
     * */
    $scope.countFoldersCourses = function () {
        $scope.nbFoldersSelect = $scope.folders.all.filter(folder => folder.select).length;
        $scope.nbCoursesSelect = $scope.courses.allCourses.filter(course => course.select).length;

    };

    /**
     * display folder name
     */

    $scope.folderName = function() {
        if($scope.printfolders){
            if ($scope.currentfolderid != 0) {
                $scope.folders.all.forEach(function (e) {
                    if (e.id == $scope.currentfolderid) {
                        $scope.nameFolder = e.name;
                    }
                });
            } else
                $scope.nameFolder = "Mes cours";
        }else
            $scope.nameFolder = "Cours partagés avec moi";
        return $scope.nameFolder;
    };

    $scope.targetFolderName = function() {
        var nameToReturn;
            if ($scope.folders.folderIdMoveIn != 0) {
                $scope.folders.all.forEach(function (e) {
                    if (e.id == $scope.folders.folderIdMoveIn) {
                        nameToReturn = e.name;
                    }
                });
            } else
                nameToReturn = "Mes cours";
        return nameToReturn;
    }

    /**
     * create folder
     * */
    $scope.openPopUpFolder = function () {
        $scope.folder = new Folder();
        template.open('ligthBoxContainer', 'courses/createFolderLightbox');
        $scope.openLightbox = true;
    };

    $scope.createFolder = async function () {
        $scope.folder.parent_id = $scope.currentfolderid;
        await $scope.folder.create();
        $scope.initFolders();
        $scope.openLightbox = false;
        Utils.safeApply($scope);

    };

    /**
     * delete elements
     * */
    $scope.openPopUpDelete = function () {
        template.open('ligthBoxContainer', 'courses/deleteLightbox');
        $scope.folders.all.filter(folder => folder.select).map(folder => folder.selectConfirm = true);
        $scope.courses.allCourses.filter(course => course.select).map(course => course.selectConfirm = true);
        $scope.confirmDeleteSend();
        $scope.openLightbox = true;
    };

    $scope.hideSuccessDelete = function () {
        $scope.successDelete = false;
    };

    $scope.deleteElements = async function () {
        $scope.disableDeleteSend = false;
        $scope.openLightbox = false;
        if ($scope.folders.all.some(folder => folder.selectConfirm)) {
            await $scope.folders.foldersDelete();
            let idAllFoldersToDelete = $scope.folders.all.filter(folder => folder.selectConfirm).map(folder => folder.id);
            while(idAllFoldersToDelete.length != 0){
                let newFoldersToDelete =[];
                idAllFoldersToDelete.forEach(function (idFolder) {
                    $scope.courses.allCourses.filter(course => course.folderid == idFolder).map(course => course.selectConfirm = true);
                    $scope.folders.getSubFolder(idFolder).map(folder => folder.id).forEach(function (id) {
                        newFoldersToDelete.push(id)
                    });
                });
                idAllFoldersToDelete = newFoldersToDelete;
            }
        }
        if ($scope.courses.allCourses.some(course => course.selectConfirm)) {
            await $scope.courses.coursesDelete();
            await $scope.courses.getCoursesbyUser(model.me.userId);
        }
        $scope.successDelete = true;
        $timeout(() =>
                $scope.hideSuccessDelete()
            , 3000);
        $scope.initFolders();
    };

    /**
     * duplicate elements
     * */
    $scope.openPopUpDuplicate = function () {
        $scope.courses.numberDuplication=undefined;
        template.open('ligthBoxContainer', 'courses/duplicateLightbox');
        $scope.courses.allCourses.filter(course => course.select).map(course => course.selectConfirm = true);
        $scope.confirmDuplicateSend();
        $scope.openLightbox = true;
    };

    $scope.duplicateElements = async function () {
        $scope.disableDuplicateSend = false;
        $scope.openLightbox = false;
        if ($scope.courses.allCourses.some(course => course.selectConfirm)) {
            $scope.courses.folderid = ($scope.courses.allCourses.filter(course => course.selectConfirm))[0].folderid;
            await $scope.courses.coursesDuplicate();
        }
        $timeout(() =>
                $scope.initCoursesbyuser
            , 2000);
        $scope.initFolders();
    };

    /**
     * move folders & courses
     * */
    $scope.openPopUpMove = function () {
        template.open('ligthBoxContainer', 'courses/moveElementLightbox');
        $scope.openLightbox = true;
    };

    $scope.closePopUpMove = function () {
        $scope.folders.folderIdMoveIn = undefined;
        $scope.openLightbox = false;
    };

    $scope.move = async function() {
        if($scope.nbFoldersSelect > 0)
            await $scope.folders.moveToFolder(undefined);
        if($scope.nbCoursesSelect > 0)
            await $scope.courses.moveToFolder($scope.folders.folderIdMoveIn);
        $scope.currentfolderid = $scope.folders.folderIdMoveIn;
        $scope.initFolders();
        $scope.folders.all.filter(folder => folder.select).map(folder => folder.selectConfirm = false);
        $scope.courses.allCourses.filter(course => course.select).map(course => course.selectConfirm = false);
        $scope.folders.folderIdMoveIn = undefined;
        $scope.openLightbox = false;
        Utils.safeApply($scope);
    };

    $scope.countItems = function (folder : Folder) {
        return _.where($scope.courses.coursesByUser,{folderid: folder.id}).length + _.where($scope.folders.all,{parent_id: folder.id}).length;
    };

    /**
     * Drag & drop file adn course
     */
    $scope.dropped  = async function(dragEl, dropEl) {
        if (dragEl == dropEl)
            return;
        // this is your application logic, do whatever makes sense
        let originalItem = $('#'+dragEl);
        let targetItem = $('#'+dropEl);
        let idOriginalItem = originalItem[0].children[0].textContent;
        let idTargetItem = targetItem[0].children[0].textContent;
        let typeOriginalItem = originalItem[0].classList[0];

        if(typeOriginalItem == "Folder"){
            $scope.folders.all.filter(folder => folder.select).map(folder => folder.select = false);
            $scope.folders.all.forEach(function (e) {
                if (e.id.toString() === idOriginalItem) {
                    e.select = true;
                }
            });
            await $scope.folders.moveToFolder(parseInt(idTargetItem,10));
        }
        else if(typeOriginalItem == "Course") {
            $scope.courses.allCourses.filter(course => course.select).map(course => course.select = false);
            $scope.courses.allCourses.forEach(function (e) {
                if (e.id.toString() === idOriginalItem) {
                    e.select = true;
                }
            });
            await $scope.courses.moveToFolder(parseInt(idTargetItem,10));
        }
        await $scope.initFolders();
    };

    /**
     * confirm delete
     * */
    $scope.confirmDeleteSend = function () {
        $scope.disableDeleteSend = !!($scope.folders.all.some(folder => folder.selectConfirm) || $scope.courses.allCourses.some(course => course.selectConfirm));
    };

    /**
     * confirm duplicate
     * */
    $scope.confirmDuplicateSend = function () {
        $scope.disableDuplicateSend = !!($scope.folders.all.some(folder => folder.selectConfirm) || $scope.courses.allCourses.some(course => course.selectConfirm));
    };

    /**
     * next and previous button to show courses
     * */
    $scope.previousCoursesToDoButton = function () {
        $scope.firstCoursesToDo -= $scope.countToDo();
        $scope.lastCoursesToDo -= $scope.countToDo();
        if ($scope.firstCoursesToDo < 0) {
            $scope.firstCoursesToDo = 0;
            $scope.lastCoursesToDo = $scope.countToDo();
        }
    };

    $scope.nextCoursesToDoButton = function () {
        $scope.firstCoursesToDo = $scope.lastCoursesToDo;
        $scope.lastCoursesToDo += $scope.countToDo();
    };

    $scope.countToCome = function () {
        if ($scope.viewModeToCome == 'list') {
            return 5;
        } else {
            return 4;
        }
    };

    $scope.countToDo = function () {
        if ($scope.viewModeToDo == 'list') {
            return 5;
        } else {
            if($scope.firstCoursesToDo==0)
                if($(window).width() < 800)
                    return 4;
                else
                    return 7;
            else
                if($(window).width() < 800)
                    return 4;
                else
                    return 8;
        }
    };


    $scope.previousCoursesToComeButton = function () {
        $scope.firstCoursesToCome -= $scope.countToCome();
        $scope.lastCoursesToCome -= $scope.countToCome();
        if($scope.firstCoursesToCome < 0){
            $scope.firstCoursesToCome = 0;
            $scope.lastCoursesToCome = $scope.countToCome();
        }
    };

    $scope.nextCoursesToComeButton = function () {
        $scope.firstCoursesToCome = $scope.lastCoursesToCome ;
        $scope.lastCoursesToCome += $scope.countToCome();
    };

    /**
     * print the right format of course data
     * */

    $scope.printRightFormatDate = function (course: Course, spec: string) {
        let format = "DD/MM/YYYY";
        if (spec == "modified") {
            if(course.timemodified.toString() == course.date.toString())
                return "Créé le : " + moment(course.timemodified.toString() + "000", "x").format(format);
            else
                return "Modifié le : " + moment(course.timemodified.toString() + "000", "x").format(format);
        } else if (spec == "enddate") {
            return moment(course.enddate + "000", "x").format(format);
        } else if (spec == "begindate") {
            return moment(course.startdate + "000", "x").format(format);
        }
        return moment();
    };

    $scope.printRightFormatAuthor = function (course: Course) {
        let author = course.auteur[0].firstname[0] + ". " + course.auteur[0].lastname[0].toUpperCase() + course.auteur[0].lastname.slice(1).toLowerCase();
        return author;
    };

    /**
     * change and get the view mode of courses to do and to come
     * */

    $scope.changeViewModeToCome = function (view : string) {
        $scope.viewModeToCome = view;
        $scope.lastCoursesToCome = $scope.firstCoursesToCome + $scope.countToCome();
    };

    $scope.changeViewModeToDo = function (view : string) {
        $scope.viewModeToDo = view;
    };

    $scope.changeShowCoursesToDoDesktop = function() {
        $scope.firstCoursesToDo=0;
    };

    $scope.changeShowCoursesToDo = function(id:string) {
        $scope.firstCoursesToDo=0;
        $scope.typeShow.selectedToDoOption.id = id;
    };

    $scope.changeShowCoursesToComeDesktop = function() {
        $scope.firstCoursesToCome=0;
    };

    $scope.changeShowCoursesToCome = function(id:string) {
        $scope.firstCoursesToCome=0;
        $scope.typeShow.selectedToComeOption.id = id;
    };

    $scope.getSelectedCourses = function () {
        return _.where($scope.courses.coursesByUser, {select: true});
    };

    // TODO remplacer par balise authorize dans toaster.html
    // <authorize name="manage" resource="uploads.selection()"> ...
    $scope.showShareButton = function () {
        return $scope.getSelectedCourses().length === 1;
    };

    /**
     * get info image
     * */
    $scope.getTypeImage = function () {
        if ($scope.course.imageurl) {
            $scope.course.setInfoImg();
            $timeout(() =>
                    $scope.imgCompatibleMoodle = $scope.course.infoImg.compatibleMoodle
                , 1000)
        }
        Utils.safeApply($scope);
    };

    /**
     * refresh after a Share
     */

    $scope.OpenShareLightBox = () => {
        template.open('ligthBoxContainer', 'courses/shareLightbox');
        $scope.openLightbox = true;
    };

    $scope.submitShareCourse = async function () {
        $scope.myCourse = undefined;
        $scope.resetSelect();
        $scope.closePopUp();
        $scope.toasterShow = !!($scope.folders.all.some(folder => folder.select) || $scope.courses.allCourses.some(course => course.select));
        Utils.safeApply($scope);
        $timeout(() =>
            $scope.initCoursesbyuser()
            , 1500)

    };

    /**
     * set timeout in order to update the status of duplicate course
     */
    $scope.updateCourse = async function () {
            let duplicateCourses = await $scope.courses.getDuplicateCourse();
            let needToGetCourses = false;
            let needRefresh = false;
        duplicateCourses.forEach(async function (duplicateCourse){
            let findDuplicate = false;
            $scope.courses.coursesByUser.filter(course => course.duplication != "non").forEach(async function (course) {
                if(duplicateCourse.id == course.courseid){
                    findDuplicate = true;
                    if(duplicateCourse.status != course.duplication){
                        course.duplication = duplicateCourse.status;
                        needRefresh=true;
                    }
                }
            });
            if(!findDuplicate && !$scope.openLightbox) {
                needToGetCourses = true;
            }
        });
        if(needToGetCourses && !$scope.openLightbox)
            $scope.initCoursesbyuser();
        else if(needRefresh && !$scope.openLightbox)
            Utils.safeApply($scope);
    };

    $interval( function(){ $scope.updateCourse(); }, 5000, $scope.courses != undefined);

    $(window).resize(function(){
        if ($(window).width() < 800) {
            if($scope.typeShow.selectedToDoOption.id == 'doing' || $scope.typeShow.selectedToDoOption.id == 'finished')
                $scope.typeShow.selectedToDoOption.id = "all";
            $scope.viewModeToDo = "icons";
            $scope.viewModeToCome = "icons";
        }
    });

}]);
