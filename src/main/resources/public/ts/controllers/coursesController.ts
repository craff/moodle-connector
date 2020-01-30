import {_, idiom as i18n, model, moment, ng, notify, template} from "entcore";
import {Course, Courses} from "../model";
import {Folder, Folders} from "../model/Folder";
import {Utils} from "../utils/Utils";
import {STATUS, TIME_TO_REFRESH_DUPLICATION} from "../constantes";

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
        },
    });
    $scope.isCreating = false;
    $scope.switchTab = function (current: string) {
        $scope.toasterShow = false;
        $scope.currentTab = current;
        $scope.resetSelect();
        $scope.toasterShow = !!($scope.folders.all.some(folder => folder.select) || $scope.courses.allCourses.some(course => course.select));
        if ($scope.currentTab == 'courses')
            $scope.initCoursesTab();
        else if ($scope.currentTab == 'dashboard')
            $scope.initDashBoardTab();
        else if ($scope.currentTab == 'library')
            $scope.initLibraryTab();
        else
            $scope.initDashBoardTab();
    };

    $scope.initDashBoardTab = async function (): Promise<void> {
        $scope.currentTab = 'dashboard';
        template.open('main', 'dashboard/dashboard_home');
        await $scope.courses.getChoice();
        await initViewLoading();
        Utils.safeApply($scope);
    };

    $scope.initCoursesTab = async function (): Promise<void> {
        $scope.currentTab = 'courses';
        template.open('main', 'my-courses');
        await initViewLoading();
        $scope.isPrintMenuFolder();
        Utils.safeApply($scope);
    };

    const initViewLoading = async (): Promise<void> => {
        if ($scope.courses.isSynchronized === undefined || $scope.courses.isSynchronized === false) {
            $scope.displayMessageLoader = true;
            await $scope.courses.getCoursesByUser(model.me.userId)
                .then(() => $scope.displayMessageLoader = false)
                .catch(() => $scope.displayMessageLoader = false);
        }
    }

    $scope.initLibraryTab = async function () {
        $scope.currentTab = 'library';
        template.open('main', 'page-library');
        Utils.safeApply($scope);
    };

    $scope.initController = async function () {
        $scope.toasterShow = false;
        $scope.courses = new Courses();
        $scope.sharedCourse = false;
        $scope.currentTab = 'dashboard';
        $scope.lightboxes = {};
        $scope.params = {};
        $scope.printMenuFolder = true;
        $scope.printMenuCourseShared = false;
        $scope.currentFolderId = 0;
        $scope.printFolders = true;
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
        $scope.firstCoursesToDo = 0;
        $scope.lastCoursesToDo = $scope.count("ToDo");
        $scope.firstCoursesToCome = 0;
        $scope.lastCoursesToCome = $scope.count("ToCome");
        $scope.showInfoSharePanel = false;
        $scope.viewModeToDo = "icons";
        $scope.viewModeToCome = "icons";
        $scope.viewModeMyCourses = "icons";
        $scope.coursesMine = "coursesMine";
        $scope.coursesShared = "coursesShared";
        $scope.initFolders();
        $scope.activityType = undefined;
        $scope.imgCompatibleMoodle = false;
        $scope.typeActivity = {
            availableOptions: [
                {id: 'quiz', name: 'Quizz'},
                {id: 'resource', name: 'Fichier'},
                {id: 'page', name: 'Page'},
                {id: 'assign', name: 'Devoir'},
                {id: 'hvp', name: 'H5P'}
            ],
            selectedOption: {id: undefined, name: 'Choisissez votre type'}
        };
        $scope.nameFolder = "";
        if ($(window).width() < 800) {
            if ($scope.courses.coursestodosort[0].id == 'finished')
                $scope.courses.coursestodosort = $scope.courses.typeShow[0];
        }
        $scope.submitWait = false;
    };

    $scope.isPrintMenuFolder = function () {
        $scope.folders.searchInFolders = 0;
        $scope.courses.searchInput.MyCourse = '';
        $scope.sharedCourse = false;
        $scope.closeNavMyCourses();
        $scope.resetSelect();
        $scope.toasterShow = !!($scope.folders.all.some(folder => folder.select) || $scope.courses.allCourses.some(course => course.select));
        if ($scope.currentFolderId != 0 || $scope.printMenuCourseShared) {
            $scope.initFolders();
            $scope.folders.searchInFolders = 0;
            $scope.printMenuFolder = true;
            $scope.printMenuCourseShared = false;
            $scope.currentFolderId = 0;
            $scope.printFolders = true;
        }
    };

    $scope.selectSearchFolder = function () {
        if ($scope.folders.searchInFolders !== undefined)
            $scope.isPrintSubfolder($scope.folders.listOfSubfolder.filter(folder => folder.id == $scope.folders.searchInFolders)[0]);
        Utils.safeApply($scope);
    };

    $scope.isPrintMenuCoursesShared = function () {
        $scope.resetSelect();
        $scope.toasterShow = !!($scope.folders.all.some(folder => folder.select) || $scope.courses.allCourses.some(course => course.select));
        $scope.closeNavMyCourses();
        $scope.myCourse = undefined;
        $scope.printMenuCourseShared = true;
        $scope.sharedCourse = false;
        $scope.printMenuFolder = false;
        $scope.printFolders = false;
        $scope.courses.order = {
            field: "creationDate",
            desc: false
        };
        $scope.currentFolderId = 0;
        $scope.setPrintSubfolderValue();
    };

    $scope.setPrintSubfolderValue = function () {
        $scope.folders.all.forEach(function (e) {
            e.printSubfolder = false;
        });
        Utils.safeApply($scope);
    };

    $scope.setPrintSubfolderValueByFolder = function (folder: Folder, printSubfolder: boolean) {
        $scope.folders.listOfSubfolder.forEach(function (e) {
            if (e.id != folder.parent_id && e.id != folder.id && e.id != 0)
                e.printSubfolder = false;
            else if (e.id == folder.parent_id)
                e.printSubfolder = false
        });
        folder.printSubfolder = printSubfolder;
        $scope.parent = folder.parent_id;
        while ($scope.parent != 0) {
            if ($scope.folders.listOfSubfolder.length === 0) break;
            $scope.folders.listOfSubfolder.forEach(function (e) {
                if (e.id == $scope.parent) {
                    e.printSubfolder = true;
                    $scope.parent = e.parent_id;
                }
            });
        }
        Utils.safeApply($scope);
    };

    $scope.isPrintSubfolder = function (folder: Folder) {
        $scope.resetSelect();
        $scope.toasterShow = !!($scope.folders.listOfSubfolder.some(folder => folder.select) || $scope.courses.allCourses.some(course => course.select));
        $scope.closeNavMyCourses();
        $scope.folders.listOfSubfolder.forEach(function (folderSearch) {
            if (folderSearch.id == folder.id) {
                folderSearch.printSubfolder = true;
                folder.printSubfolder = folderSearch.printSubfolder;
            }
        });
        $scope.printFolders = true;
        if (folder.printSubfolder) {
            $scope.currentFolderId = folder.id;
            $scope.folders.folderIdMoveIn = $scope.currentFolderId;
        } else {
            $scope.currentFolderId = folder.parent_id;
            $scope.folders.folderIdMoveIn = $scope.currentFolderId;
        }
        $scope.setPrintSubfolderValueByFolder(folder, folder.printSubfolder);
        if ($scope.courses.searchInput.MyCourse != '')
            $scope.courses.searchInput.MyCourse = '';
        if ($scope.folders.searchInFolders != $scope.currentFolderId)
            $scope.folders.searchInFolders = $scope.currentFolderId;
        Utils.safeApply($scope);
    };

    $scope.isPrintSubfolderNumber = function (folderId: number) {
        $scope.folders.all.forEach(function (e) {
            if (e.id == folderId) {
                e.printSubfolder = true;
                $scope.printFolders = true;
                $scope.currentFolderId = folderId;
                $scope.setPrintSubfolderValueByFolder(e, true);
            }
        });
    };

    $scope.isPrintTargetSubfolder = function (folder: Folder) {
        $scope.folders.folderIdMoveIn = folder.id;
        $scope.folders.all.forEach(function (e) {
            if (e.id == folder.id) {
                e.printTargetSubfolder = !e.printTargetSubfolder;
                folder.printTargetSubfolder = e.printTargetSubfolder;
            }
        });
        $scope.setPrintTargetSubfolderValueByFolder(folder, folder.printTargetSubfolder);
    };

    $scope.targetMenu = function () {
        $scope.folders.folderIdMoveIn = 0;
        $scope.folders.all.forEach(function (e) {
            e.printTargetSubfolder = false;
        });
    };

    $scope.setPrintTargetSubfolderValueByFolder = function (folder: Folder, printTargetSubfolder: boolean) {
        $scope.folders.all.forEach(function (e) {
            e.printTargetSubfolder = false;
        });
        folder.printTargetSubfolder = printTargetSubfolder;
        $scope.parent = folder.parent_id;
        while ($scope.parent != 0) {
            $scope.folders.all.forEach(function (e) {
                if (e.id == $scope.parent) {
                    e.printTargetSubfolder = true;
                    $scope.parent = e.parent_id;
                }
            });
        }
        Utils.safeApply($scope);
    };

    $scope.setInitialPrintSubfolderValueByFolder = function (targetFolder: number) {
        $scope.folders.all.forEach(function (e) {
            e.printSubfolder = false;
        });
        $scope.folders.all.forEach(function (e) {
            if (e.id === targetFolder)
                e.printSubfolder = true;
            $scope.parent = e.parent_id;
        });
        while ($scope.parent != 0) {
            $scope.folders.all.forEach(function (e) {
                if (e.id === $scope.parent)
                    e.printSubfolder = true;
                $scope.parent = e.parent_id
            });
        }
        $scope.folders.all.forEach(function (e) {
            if (e.parent_id === 0)
                e.printSubfolder = true
        });
        Utils.safeApply($scope);
    };

    $scope.initCoursesByUser = async function () {
        await $scope.courses.getCoursesByUser(model.me.userId);
        Utils.safeApply($scope);
    };

    $scope.initFolders = async function () {
        $scope.folders = new Folders();
        await $scope.folders.sync();
        $scope.resetSelect();
        $scope.showToaster();
        $scope.folders.folderIdMoveIn = $scope.currentFolderId;
        $scope.isPrintSubfolderNumber($scope.currentFolderId);
        $scope.folders.getAllSubfolder();
        Utils.safeApply($scope);
    };


    $scope.getFolderParent = function (): Folder[] {
        return $scope.folders.getParentFolder();
    };

    $scope.getSubfolder = function (folder: Folder): Folder[] {
        return $scope.folders.getSubfolder(folder.id);
    };

    route({
        view: function (params) {
            template.open('main', 'main');
            Utils.safeApply($scope);
        }
    });

    /**
     * Open creation course lightbox
     */
    $scope.openPopUp = function () {
        $scope.folders.folderIdMoveIn = $scope.currentFolderId;
        if ($scope.submitWait == false) {
            if ($scope.activityType == undefined) {
                $scope.course = new Course();
                template.open('lightboxContainer', 'courses/createCourseLightbox');
                $scope.openLightbox = true;
                Utils.safeApply($scope);
            } else {
                $scope.course = new Course();
                $scope.course.typeA = $scope.activityType;
                template.open('lightboxContainer', 'courses/createCourseLightbox');
                $scope.openLightbox = true;
                Utils.safeApply($scope);
            }
        } else if ($scope.submitWait == true) {
            template.open('lightboxContainer', 'courses/createCourseLightbox');
            $scope.openLightbox = true;
            Utils.safeApply($scope);
        }
    };

    /**
     * Close lightbox
     */
    $scope.closePopUp = function () {
        $scope.openLightbox = false;
        if ($scope.nbCoursesSelect == 1)
            $scope.myCourse = _.findWhere($scope.courses.allCourses, {select: true});
        $scope.folders.all.filter(folder => folder.select).map(folder => folder.selectConfirm = false);
        $scope.courses.allCourses.filter(course => course.select).map(course => course.selectConfirm = false);
        $scope.folders.folderIdMoveIn = undefined;
        template.close('lightboxContainer');
        $scope.openLightbox = false;
    };

    /**
     * Create a course
     */
    $scope.createCourse = async (): Promise<void> => {
        $scope.course.folderid = parseInt($scope.folders.folderIdMoveIn);
        if ($scope.course.fullname.length >= 4) {
            $scope.submitWait = true;
            if ($scope.course.infoImg != undefined) {
                let reg = new RegExp(".jpg|.jpeg|.gif|.png$");
                $scope.arraySplitImg = reg.exec($scope.course.infoImg.name.toLowerCase());
                $scope.course.imageurl = $scope.course.imageurl.concat($scope.arraySplitImg[0]);
            }
            await $scope.course.create()
                .then(async (): Promise<void> => {
                    $scope.activityType = $scope.course.typeA;
                    $scope.currentFolderId = $scope.folders.folderIdMoveIn;
                    $scope.folders.folderIdMoveIn = undefined;
                    $scope.showToaster();
                    await $scope.courses.getCoursesByUser(model.me.userId);
                    $scope.openLightbox = $scope.submitWait = false;
                })
                .catch((): boolean => $scope.submitWait = $scope.openLightbox = false);
        } else
            notify.error(i18n.translate("moodle.info.short.title"));
        Utils.safeApply($scope);
    };

    $scope.changeTypeA = function (course: Course) {
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
     */
    $scope.showToaster = function (place, course) {
        if (place == $scope.coursesShared && course.select) {
            $scope.courses.allCourses.forEach(course => course.select = false);
            course.select = true;
        }
        $scope.sharedCourse = false;
        template.open('toaster', 'toaster');
        $scope.toasterShow = !!($scope.folders.all.some(folder => folder.select) || $scope.courses.allCourses.some(course => course.select));
        $scope.countFoldersCourses();
        if (place == "coursesShared") {
            $scope.isSharedCourseSelected();
        }
        if ($scope.nbCoursesSelect == 1)
            $scope.myCourse = _.findWhere($scope.courses.allCourses, {select: true});
        if ($scope.toasterShow === true)
            $scope.selectedCourse = _.findWhere($scope.courses.allCourses, {select: true});
    };

    /**
     * count folders and courses select
     */
    $scope.countFoldersCourses = function () {
        $scope.nbFoldersSelect = $scope.folders.all.filter(folder => folder.select).length;
        $scope.nbCoursesSelect = $scope.courses.allCourses.filter(course => course.select).length;
    };

    /**
     * display folder name
     */
    $scope.folderName = function () {
        if ($scope.printFolders) {
            if ($scope.currentFolderId != 0) {
                $scope.folders.all.forEach(function (e) {
                    if (e.id == $scope.currentFolderId)
                        $scope.nameFolder = e.name;
                });
            } else
                $scope.nameFolder = "Mes cours";
        } else
            $scope.nameFolder = "Cours partagés avec moi";
        return $scope.nameFolder;
    };

    $scope.targetFolderName = function () {
        let nameToReturn;
        if ($scope.folders.folderIdMoveIn != 0) {
            $scope.folders.all.forEach(function (e) {
                if (e.id == $scope.folders.folderIdMoveIn)
                    nameToReturn = e.name;
            });
        } else
            nameToReturn = "Mes cours";
        return nameToReturn;
    };

    /**
     * create folder
     */
    $scope.openPopUpFolder = function () {
        $scope.folder = new Folder();
        $scope.isCreating = false;
        template.open('lightboxContainer', 'courses/createFolderLightbox');
        $scope.openLightbox = true;
    };

    $scope.createFolder = async function () {
        $scope.folder.parent_id = $scope.currentFolderId;
        if (!$scope.isCreating) {
            $scope.isCreating = true;
            Utils.safeApply($scope);
            let status = await $scope.folder.create();
            if (status === 200) {
                template.close('lightBoxContainer')
                $scope.openLightbox = false;
            }
            await $scope.initFolders();
        }
        Utils.safeApply($scope);
    };


    /**
     * rename folder
     */
    $scope.openPopUpFolderRename = function () {
        $scope.folder = new Folder();
        $scope.folder = $scope.folders.all.filter(folder => folder.select)[0];
        template.open('lightboxContainer', 'courses/renameFolderLightbox');
        $scope.openLightbox = true;
    };

    $scope.renameFolder = async function () {
        $scope.folders.all.filter(folder => folder.select).forEach(async function (folder) {
            await folder.rename();
        });
        $scope.openLightbox = false;
        Utils.safeApply($scope);
        $timeout(() =>
                $scope.initFolders()
            , 1500);
    };

    /**
     * delete elements
     */
    $scope.openPopUpDelete = function () {
        template.open('lightboxContainer', 'courses/deleteLightbox');
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
        $scope.submitWait = true;
        if ($scope.folders.all.some(folder => folder.selectConfirm)) {
            await $scope.folders.foldersDelete();
            let idAllFoldersToDelete = $scope.folders.all.filter(folder => folder.selectConfirm).map(folder => folder.id);
            while (idAllFoldersToDelete.length != 0) {
                let newFoldersToDelete = [];
                idAllFoldersToDelete.forEach(function (idFolder) {
                    $scope.courses.allCourses.filter(course => course.folderid == idFolder).map(course => course.selectConfirm = true);
                    $scope.folders.getSubfolder(idFolder).map(folder => folder.id).forEach(function (id) {
                        newFoldersToDelete.push(id)
                    });
                });
                idAllFoldersToDelete = newFoldersToDelete;
            }
        }
        if ($scope.courses.allCourses.some(course => course.selectConfirm)) {
            await $scope.courses.coursesDelete();
            await $scope.courses.getCoursesByUser(model.me.userId);
        }
        $scope.openLightbox = false;
        $scope.submitWait = false;
        $scope.successDelete = true;
        $timeout(() =>
                $scope.hideSuccessDelete()
            , 3000);
        $scope.initFolders();
    };

    /**
     * move folders & courses
     */
    $scope.openPopUpMove = function () {
        template.open('lightboxContainer', 'courses/moveElementLightbox');
        $scope.openLightbox = true;
    };

    $scope.closePopUpMove = function () {
        $scope.folders.folderIdMoveIn = undefined;
        $scope.openLightbox = false;
    };

    $scope.move = async function () {
        if ($scope.nbFoldersSelect > 0)
            await $scope.folders.moveToFolder(undefined);
        if ($scope.nbCoursesSelect > 0)
            await $scope.courses.moveToFolder($scope.folders.folderIdMoveIn);
        $scope.currentFolderId = $scope.folders.folderIdMoveIn;
        await $scope.initFolders();
        $scope.folders.all.filter(folder => folder.select).map(folder => folder.selectConfirm = false);
        $scope.courses.allCourses.filter(course => course.select).map(course => course.selectConfirm = false);
        $scope.folders.folderIdMoveIn = undefined;
        $scope.openLightbox = false;
        Utils.safeApply($scope);
    };

    $scope.countItems = function (folder: Folder) {
        return _.where($scope.courses.coursesByUser, {folderid: folder.id}).length + _.where($scope.folders.all, {parent_id: folder.id}).length;
    };

    /**
     * Drag & drop file adn course
     */
    $scope.dropped = async function (dragEl, dropEl) {
        if (dragEl == dropEl)
            return;
        // this is your application logic, do whatever makes sense
        let originalItem = $('#' + dragEl);
        let targetItem = $('#' + dropEl);
        let idOriginalItem = originalItem[0].children[0].textContent;
        let idTargetItem = targetItem[0].children[0].textContent;
        let typeOriginalItem = originalItem[0].classList[0];

        if (typeOriginalItem == "Folder") {
            $scope.folders.all.filter(folder => folder.select).map(folder => folder.select = false);
            $scope.folders.all.forEach(function (e) {
                if (e.id.toString() === idOriginalItem)
                    e.select = true;
            });
            await $scope.folders.moveToFolder(parseInt(idTargetItem, 10));
        } else if (typeOriginalItem == "Course") {
            $scope.courses.allCourses.filter(course => course.select).map(course => course.select = false);
            $scope.courses.allCourses.forEach(function (e) {
                if (e.id.toString() === idOriginalItem)
                    e.select = true;
            });
            await $scope.courses.moveToFolder(parseInt(idTargetItem, 10));
        }
        await $scope.initFolders();
    };

    $scope.isSharedCourseSelected = function () {
        $scope.sharedCourse = true;
    };

    /**
     * confirm delete
     */
    $scope.confirmDeleteSend = function () {
        $scope.disableDeleteSend = !!($scope.folders.all.some(folder => folder.selectConfirm) || $scope.courses.allCourses.some(course => course.selectConfirm));
    };

    /**
     * confirm duplicate
     */
    $scope.confirmDuplicateSend = function () {
        return !!($scope.folders.all.some(folder => folder.selectConfirm) || $scope.courses.allCourses.some(course => course.selectConfirm));
    };

    /**
     * next and previous button to show courses
     */
    $scope.previousCoursesButton = function (place: string) {
        if (place == "ToDo") {
            $scope.firstCoursesToDo -= $scope.count("ToDo");
            $scope.lastCoursesToDo -= $scope.count("ToDo");
            if ($scope.firstCoursesToDo < 0) {
                $scope.firstCoursesToDo = 0;
                $scope.lastCoursesToDo = $scope.count("ToDo");
            }
        } else if (place == "ToCome") {
            $scope.firstCoursesToCome -= $scope.count("ToCome");
            $scope.lastCoursesToCome -= $scope.count("ToCome");
            if ($scope.firstCoursesToCome < 0) {
                $scope.firstCoursesToCome = 0;
                $scope.lastCoursesToCome = $scope.count("ToCome");
            }
        }
    };

    $scope.nextCoursesButton = function (place: string) {
        if (place == "ToDo") {
            $scope.firstCoursesToDo = $scope.lastCoursesToDo;
            $scope.lastCoursesToDo += $scope.count("ToDo");
        } else if (place == "ToCome") {
            $scope.firstCoursesToCome = $scope.lastCoursesToCome;
            $scope.lastCoursesToCome += $scope.count("ToCome");
        }
    };

    $scope.count = function (place: string) {
        if (place == "ToCome") {
            if ($scope.viewModeToCome == 'list')
                return 7;
            else
                return 4;
        } else if (place == "ToDo") {
            if ($scope.viewModeToDo == 'list')
                return 7;
            else {
                if ($scope.firstCoursesToDo == 0)
                    if ($(window).width() < 800)
                        return 4;
                    else
                        return 7;
                else if ($(window).width() < 800)
                    return 4;
                else
                    return 8;
            }
        }
    };

    /**
     * print the right format of course data
     */
    $scope.printRightFormatDate = function (course: Course, spec: string) {
        let format = "DD/MM/YYYY";
        if (spec == "modified") {
            if (course.timemodified.toString() == course.date.toString())
                return "Créé le : " + moment(course.timemodified.toString() + "000", "x").format(format);
            else
                return "Modifié le : " + moment(course.timemodified.toString() + "000", "x").format(format);
        } else if (spec == "enddate")
            return moment(course.enddate + "000", "x").format(format);
        else if (spec == "begindate")
            return moment(course.startdate + "000", "x").format(format);
        return moment();
    };

    $scope.printRightFormatAuthor = function (course: Course) {
        let author = "";
        if (course.auteur[0] !== null && course.auteur[0] !== undefined && course.auteur[0].firstname !== null && course.auteur[0].lastname !== null)
            author = course.auteur[0].firstname[0] + ". " + course.auteur[0].lastname[0].toUpperCase() + course.auteur[0].lastname.slice(1).toLowerCase();
        return author;
    };

    /**
     * change and get the view mode of courses to do and to come
     */
    $scope.changeViewMode = function (place: string, view: string) {
        if (place == 'ToCome') {
            $scope.viewModeToCome = view;
            $scope.lastCoursesToCome = $scope.firstCoursesToCome + $scope.count("ToCome");
        } else if (place == 'ToDo') {
            $scope.viewModeToDo = view;
            $scope.lastCoursesToDo = $scope.firstCoursesToDo + $scope.count("ToDo");
        } else if (place == "MyCourses")
            $scope.viewModeMyCourses = view;
    };

    $scope.changeShowCoursesDesktop = async function (place: string) {
        if (place == "ToDo") {
            $scope.firstCoursesToDo = 0;
            $scope.lastCoursesToDo = $scope.count("ToDo");
            await $scope.courses.setChoice(4);
        } else if (place == "ToCome") {
            $scope.firstCoursesToCome = 0;
            $scope.lastCoursesToCome = $scope.count("ToCome");
            await $scope.courses.setChoice(5);
        }
    };

    $scope.changeShowCourses = async function (place: string, id: string) {
        if (place == "ToDo") {
            $scope.firstCoursesToDo = 0;
            $scope.lastCoursesToDo = $scope.count("ToDo");
            $scope.courses.coursestodosort = $scope.courses.typeShow.filter(type => type.id == id);
            await $scope.courses.setChoice(4);
        } else if (place == "ToCome") {
            $scope.firstCoursesToCome = 0;
            $scope.lastCoursesToCome = $scope.count("ToCome");
            $scope.courses.coursestocomesort = $scope.courses.typeShow.filter(type => type.id == id);
            await $scope.courses.setChoice(5);
        }
    };

    $scope.getSelectedCourses = function () {
        return _.where([...$scope.courses.coursesByUser, ...$scope.courses.coursesShared], {select: true});
    };

    // TODO remplacer par balise authorize dans toaster.html
    // <authorize name="manage" resource="uploads.selection()"> ...
    $scope.showShareButton = function () {
        return $scope.getSelectedCourses().length === 1;
    };

    /**
     * get info image
     */
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
    $scope.openShareLightBox = () => {
        $scope.openLightbox = true;
        template.open('lightboxContainer', 'courses/shareLightbox');
        Utils.safeApply($scope);
    };

    /**
     * close share pop-up
     */
    $scope.closeShareLightbox = () => {
        template.close('lightboxContainer');
        $scope.openLightbox = false;
    };

    $scope.submitShareCourse = async function () {
        $scope.myCourse = undefined;
        $scope.resetSelect();
        $scope.closePopUp();
        $scope.toasterShow = !!($scope.folders.all.some(folder => folder.select) || $scope.courses.allCourses.some(course => course.select));
        Utils.safeApply($scope);
        $timeout(() =>
                $scope.initCoursesByUser()
            , 5000)
    };

    /**
     * duplicate elements
     */
    let isStartDuplicationCheck: Boolean, numberCoursesPending: Number;
    $scope.openPopUpDuplicate = (): void => {
        $scope.courses.allCourses = $scope.courses.allCourses.map((course: Course): Course => {
            course.selectConfirm = course.select;
            return course
        });
        template.open('lightboxContainer', 'courses/duplicateLightbox');
        $scope.openLightbox = true;
    };

    $scope.duplicateElements = async (): Promise<void> => {
        $scope.disableDuplicateSend = $scope.openLightbox = $scope.toasterShow = false;
        if ($scope.courses.allCourses.some((course: Course): boolean => course.selectConfirm)) {
            await $scope.courses.coursesDuplicate($scope.currentFolderId);
            if (!isStartDuplicationCheck)
                await $scope.updateCourse();
            await $scope.initCoursesByUser();
        }
    };
    /**
     * set timeout in order to update the status of duplicate course
     */
    $scope.updateCourse = async (): Promise<void> => {
        const duplicateCourses: Course[] = await $scope.courses.getDuplicateCourse();
        const coursesChecked: Course[] = duplicateCourses
            .filter((course: Course): boolean => course.status === STATUS.WAITING || course.status === STATUS.PENDING);
        isStartDuplicationCheck = coursesChecked.length !== 0;
        if (isStartDuplicationCheck) {
            if (coursesChecked.length !== numberCoursesPending && numberCoursesPending)
                await $scope.initCoursesByUser();
            numberCoursesPending = coursesChecked.length;
            $timeout((): void =>
                    $scope.updateCourse()
                , TIME_TO_REFRESH_DUPLICATION);
        } else
            await $scope.initCoursesByUser();
    };

    $(window).resize(function () {
        if ($(window).width() < 800) {
            if ($scope.courses.coursestodosort[0].id == 'finished')
                $scope.courses.coursestodosort = $scope.courses.typeShow.filter(type => type.id == "all");
            $scope.viewModeToDo = "icons";
            $scope.viewModeToCome = "icons";
            $scope.viewModeMyCourses = "icons";
        }
        $scope.firstCoursesToCome = 0;
        $scope.lastCoursesToCome = $scope.count("ToCome");
        $scope.firstCoursesToDo = 0;
        $scope.lastCoursesToDo = $scope.count("ToDo");
    });

    $scope.openNavMyCourses = function () {
        document.getElementById("mySidenavCourses").style.width = "200px";
    };

    $scope.closeNavMyCourses = function () {
        document.getElementById("mySidenavCourses").style.width = "0";
    };

    $scope.initSearch = function (place: string) {
        if (place == "ToCome") {
            $scope.firstCoursesToCome = 0;
            $scope.lastCoursesToCome = $scope.count("ToCome");
        } else if (place == "ToDo") {
            $scope.firstCoursesToDo = 0;
            $scope.lastCoursesToDo = $scope.count("ToDo");
        }
    };
}]);
