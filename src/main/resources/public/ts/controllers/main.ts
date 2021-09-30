import {_, model, ng, template, idiom as lang} from "entcore";
import {Folder, Folders, Course, Courses} from "../model";
import {Utils} from "../utils/Utils";
import {TIME_TO_REFRESH_DUPLICATION, STATUS} from "../constantes";

export const mainController = ng.controller('mainController', ['$scope', '$timeout', '$templateCache', 'route',
    ($scope, $timeout, $templateCache, route) => {

        route({
            dashboard: async function () {
                await initController();
                await initDashBoardTab();
            },
            courses: async function () {
                await initController();
                await initCoursesTab();
            },
            library: async function () {
                await initController();
                await initLibraryTab();
            },
        });

        $scope.switchTab = function (current: string) {
            $scope.show.currentTab = current;
            $scope.resetSelect();
            $scope.show.toaster = !!($scope.folders.all.some(folder => folder.select) || $scope.courses.allCourses.some(course => course.select));
            if ($scope.show.currentTab == 'courses')
                initCoursesTab();
            else if ($scope.show.currentTab == 'library')
                initLibraryTab();
            else
                initDashBoardTab();
        };

        const initDashBoardTab = async function (): Promise<void> {
            $scope.show.currentTab = 'dashboard';
            await template.open('main', 'dashboard_home');
            await $scope.courses.getChoice();
            await initViewLoading();
            await Utils.safeApply($scope);
        };

        const initCoursesTab = async function (): Promise<void> {
            $scope.show.currentTab = 'courses';
            await template.open('main', 'my-courses');
            await initViewLoading();
            $scope.isPrintMenuCourses('my_courses');
            await Utils.safeApply($scope);
        };

        const initViewLoading = async (): Promise<void> => {
            if ($scope.courses.isSynchronized === undefined || $scope.courses.isSynchronized === false) {
                $scope.displayMessageLoader = true;
                await $scope.courses.getCoursesByUser(model.me.userId)
                    .then(() => $scope.displayMessageLoader = false)
                    .catch(() => $scope.displayMessageLoader = false);
            }
        };

        const initLibraryTab = async function () {
            $scope.show.currentTab = 'library';
            await template.open('main', 'page-library');
            await Utils.safeApply($scope);
        };

        const initController = async function () {
            $scope.lang= lang;
            $scope.utils = Utils;
            $scope.courses = new Courses();
            $scope.show = {
                lightboxes: false,
                toaster: false,
                sharedCourse : false,
                currentTab : 'dashboard',
                principal_folder : Utils.PRINCIPAL_FOLDER_TYPE.my_courses,
                currentFolderId : 0,
                printFolders : true,
                typeFilter : [true, true],
                nbFoldersSelect : 0,
                nbCoursesSelect : 0,
                disableDeleteSend : true,
                firstCoursesToDo : 0,
                firstCoursesToCome : 0,
                viewModeToDo : "icons",
                viewModeToCome : "icons",
                viewModeMyCourses : "icons",
                coursesMine : "coursesMine",
                coursesShared : "coursesShared",
                coursesPublished : "coursesPublished",
                categoryBoolean : true,
                activityType : undefined,
                submitWait : false,
                isCreating : false
            };
            $scope.show.lastCoursesToDo = $scope.count("ToDo");
            $scope.show.lastCoursesToCome = $scope.count("ToCome");
            $scope.folders = new Folders();
            $scope.folder = {
                toRename : new Folder()
            };
            $scope.filterChoice = {
                levels : [],
                disciplines : [],
                plain_text : []
            };
            if ($(window).width() < 800) {
                if ($scope.courses.coursestodosort[0].id == 'finished')
                    $scope.courses.coursestodosort = $scope.courses.typeShow[0];
            }
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

        const isPrintSubfolderNumber = function (folderId: number) {
            $scope.folders.all.forEach(function (e:Folder) {
                if (e.id == folderId) {
                    e.printSubfolder = true;
                    $scope.show.printFolders = true;
                    $scope.show.currentFolderId = folderId;
                    $scope.setPrintSubfolderValueByFolder(e, true);
                }
            });
        };

        $scope.initCoursesByUser = async function () {
            await $scope.courses.getCoursesByUser(model.me.userId);
            await Utils.safeApply($scope);
        };

        $scope.initFolders = async function () {
            $scope.folders = new Folders();
            await $scope.folders.sync();
            $scope.resetSelect();
            $scope.showToaster();
            $scope.folders.folderIdMoveIn = $scope.show.currentFolderId;
            isPrintSubfolderNumber($scope.show.currentFolderId);
            $scope.folders.getAllSubfolder();
            await Utils.safeApply($scope);
        };

        /**
         * Open creation course lightbox
         */
        $scope.openCreateCoursePopUp = function () {
            $scope.folders.folderIdMoveIn = $scope.show.currentFolderId;
            if ($scope.show.submitWait == false) {
                $scope.course = new Course();
                if ($scope.show.activityType != undefined) {
                    $scope.course.typeA = $scope.show.activityType;
                }
            }
            template.open('lightboxContainer', 'courses/lightbox/createCourseLightbox');
            $scope.show.lightboxes = true;
            Utils.safeApply($scope);
        };

        /**
         * Close lightbox
         */
        $scope.closePopUp = function () {
            if ($scope.show.nbCoursesSelect == 1)
                $scope.myCourse = _.findWhere($scope.courses.allCourses, {select: true});
            $scope.folders.all.filter(folder => folder.select).map(folder => folder.selectConfirm = false);
            $scope.courses.allCourses.filter(course => course.select).map(course => course.selectConfirm = false);
            $scope.folders.folderIdMoveIn = undefined;
            template.close('lightboxContainer');
            $scope.show.lightboxes = false;
        };

        $scope.resetSelect = function () {
            $scope.folders.all.map(folder => folder.select = false);
            $scope.courses.allCourses.map(course => course.select = false);
        };

        /**
         * toaster show
         */
        $scope.showToaster = function (place, course) {
            $scope.show.categoryBoolean = true;
            if (place == $scope.show.coursesShared && course.select) {
                $scope.courses.allCourses.forEach(course => course.select = false);
                course.select = true;
            }
            template.open('toaster', 'toaster');
            $scope.show.toaster = !!($scope.folders.all.some(folder => folder.select) || $scope.courses.allCourses.some(course => course.select));
            countFoldersCourses();
            $scope.show.sharedCourse = (place == $scope.show.coursesShared);
            if ($scope.show.principal_folder == Utils.PRINCIPAL_FOLDER_TYPE.published)
                checkCategoryId();
            if ($scope.show.nbCoursesSelect == 1) {
                $scope.myCourse = _.findWhere($scope.courses.allCourses, {select: true});
            }
            if ($scope.show.toaster === true)
                $scope.selectedCourse = _.findWhere($scope.courses.allCourses, {select: true});
        };

        /**
         * count folders and courses select
         */
        const countFoldersCourses = function () {
            $scope.show.nbFoldersSelect = $scope.folders.all.filter(folder => folder.select).length;
            $scope.show.nbCoursesSelect = $scope.courses.allCourses.filter(course => course.select).length;
        };

        /**
         * create folder
         */
        $scope.openPopUpFolder = function () {
            $scope.folder = new Folder();
            $scope.show.isCreating = false;
            template.open('lightboxContainer', 'folders/lightbox/createFolderLightbox');
            $scope.show.lightboxes = true;
        };

        /**
         * confirm delete
         */
        $scope.confirmDeleteSend = function () {
            $scope.show.disableDeleteSend = !!($scope.folders.all.some(folder => folder.selectConfirm) ||
                $scope.courses.allCourses.some(course => course.selectConfirm));
        };

        $scope.count = function (place: string) {
            if (place == "ToCome") {
                if ($scope.show.viewModeToCome == 'list')
                    return 7;
                else
                    return 4;
            }
            else if (place == "ToDo") {
                if ($scope.show.viewModeToDo == 'list')
                    return 7;
                else {
                    if ($scope.show.firstCoursesToDo == 0)
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
         * change and get the view mode of courses to do and to come
         */
        $scope.changeViewMode = function (place: string, view: string) {
            if (place == 'ToCome') {
                $scope.show.viewModeToCome = view;
                $scope.show.lastCoursesToCome = $scope.show.firstCoursesToCome + $scope.count("ToCome");
            } else if (place == 'ToDo') {
                $scope.show.viewModeToDo = view;
                $scope.show.lastCoursesToDo = $scope.show.firstCoursesToDo + $scope.count("ToDo");
            } else if (place == "MyCourses")
                $scope.show.viewModeMyCourses = view;
        };

        /**
         * set timeout in order to update the status of duplicate course
         */
        $scope.updateCourse = async (): Promise<void> => {
            const duplicateCourses: Course[] = await $scope.courses.getDuplicateCourse();
            const coursesChecked: Course[] = duplicateCourses
                .filter((course: Course): boolean => course.status === STATUS.WAITING || course.status === STATUS.PENDING);
            $scope.isStartDuplicationCheck = coursesChecked.length !== 0;
            if ($scope.isStartDuplicationCheck) {
                if (coursesChecked.length !== $scope.numberCoursesPending && $scope.numberCoursesPending)
                    await $scope.initCoursesByUser();
                $scope.numberCoursesPending = coursesChecked.length;
                $timeout((): void =>
                        $scope.updateCourse()
                    , TIME_TO_REFRESH_DUPLICATION);
            }
            else await $scope.initCoursesByUser();
        };

        $(window).resize(function () {
            if ($(window).width() < 800) {
                if ($scope.courses.coursestodosort[0].id == 'finished')
                    $scope.courses.coursestodosort = $scope.courses.typeShow.filter(type => type.id == "all");
                $scope.show.viewModeToDo = "icons";
                $scope.show.viewModeToCome = "icons";
                $scope.show.viewModeMyCourses = "icons";
            }
            $scope.show.firstCoursesToCome = 0;
            $scope.show.lastCoursesToCome = $scope.count("ToCome");
            $scope.show.firstCoursesToDo = 0;
            $scope.show.lastCoursesToDo = $scope.count("ToDo");
        });

        $scope.closeNavMyCourses = function () {
            if(document.getElementById("mySidenavCourses")){
                document.getElementById("mySidenavCourses").style.width = "0";
            }
        };

        $scope.initSearch = function (place: string) {
            if (place == "ToCome") {
                $scope.show.firstCoursesToCome = 0;
                $scope.show.lastCoursesToCome = $scope.count("ToCome");
            }
            else if (place == "ToDo") {
                $scope.show.firstCoursesToDo = 0;
                $scope.show.lastCoursesToDo = $scope.count("ToDo");
            }
        };

        const checkCategoryId = () => {
            if ($scope.courses.allCourses.filter(course => course.select).length > 0 &&
                $scope.courses.allCourses.filter(course => course.select)[0].categoryid == $scope.courses.publicBankCategoryId) {
                $scope.show.categoryBoolean = false;
            }
        };

        $scope.isPrintMenuCourses = function (folderSelected:string) {
            $scope.closeNavMyCourses();
            $scope.resetSelect();
            $scope.show.toaster = !!($scope.folders.all.some(folder => folder.select) ||
                $scope.courses.allCourses.some(course => course.select));
            $scope.show.sharedCourse = false;
            $scope.show.currentFolderId = 0;
            $scope.show.principal_folder = Utils.PRINCIPAL_FOLDER_TYPE[folderSelected];
            if ($scope.show.principal_folder === Utils.PRINCIPAL_FOLDER_TYPE.my_courses){
                $scope.courses.searchInput.MyCourse = '';
                $scope.initFolders();
                $scope.folders.searchInFolders = 0;
                $scope.show.printFolders = true;
            }
            else{
                $scope.myCourse = undefined;
                $scope.courses.order = {
                    field: "creationDate",
                    desc: false
                };
                $scope.setPrintSubfolderValue();
                $scope.show.printFolders = false;
            }
        };

        $scope.getSelectedCourses = function () {
            return _.where([...$scope.courses.coursesByUser, ...$scope.courses.coursesShared, ...$scope.courses.coursesPublished], {select: true});
        };

        $scope.goConverter = async () : Promise<void> => {
            $scope.show.currentTab = 'converter';
            await template.open('main', 'converter');
            await Utils.safeApply($scope);
        };


    }]);
