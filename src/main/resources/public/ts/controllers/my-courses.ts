import {_, ng} from "entcore";
import {Folder, Course} from "../model";
import {Utils} from "../utils/Utils";

export const myCoursesController = ng.controller('myCoursesController', ['$scope',
    ($scope) => {

        $scope.selectSearchFolder = function () {
            if ($scope.folders.searchInFolders !== undefined)
                $scope.isPrintSubfolder($scope.folders.listOfSubfolder.filter(folder => folder.id == $scope.folders.searchInFolders)[0]);
            Utils.safeApply($scope);
        };

        $scope.isPrintSubfolder = function (folder: Folder) {
            $scope.resetSelect();
            $scope.show.toaster = !!($scope.folders.listOfSubfolder.some(folder => folder.select) || $scope.courses.allCourses.some(course => course.select));
            $scope.closeNavMyCourses();
            $scope.folders.listOfSubfolder.forEach(function (folderSearch) {
                if (folderSearch.id == folder.id) {
                    folderSearch.printSubfolder = true;
                    folder.printSubfolder = folderSearch.printSubfolder;
                }
            });
            $scope.show.printFolders = true;
            if (folder.printSubfolder) {
                $scope.show.currentFolderId = folder.id;
                $scope.folders.folderIdMoveIn = $scope.show.currentFolderId;
            } else {
                $scope.show.currentFolderId = folder.parent_id;
                $scope.folders.folderIdMoveIn = $scope.show.currentFolderId;
            }
            $scope.setPrintSubfolderValueByFolder(folder, folder.printSubfolder);
            if ($scope.courses.searchInput.MyCourse != '')
                $scope.courses.searchInput.MyCourse = '';
            if ($scope.folders.searchInFolders != $scope.show.currentFolderId)
                $scope.folders.searchInFolders = $scope.show.currentFolderId;
            Utils.safeApply($scope);
        };

        /**
         * display folder name
         */
        $scope.folderName = function () {
            let name = "";
            if ($scope.show.printFolders) {
                if ($scope.show.currentFolderId != 0) {
                    $scope.folders.all.forEach(function (e) {
                        if (e.id == $scope.show.currentFolderId)
                            name = e.name;
                    });
                } else
                    name = "Mes cours";
            } else
            if ($scope.show.principal_folder === Utils.PRINCIPAL_FOLDER_TYPE.shared)
                name = "Cours partagés avec moi";
            else
                name = "Cours publiés";
            return name;
        };

        $scope.countItems = function (folder: Folder) {
            return _.where($scope.courses.coursesByUser, {folderId: folder.id}).length +
                _.where($scope.folders.all, {parent_id: folder.id}).length;
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

            async function moveTo(itemsAll,items) {
                itemsAll.filter(item => item.select).map(item => item.select = false);
                itemsAll.forEach(function (e) {
                    if (e.id.toString() === idOriginalItem)
                        e.select = true;
                });
                await items.moveToFolder(parseInt(idTargetItem, 10));
            }

            if (typeOriginalItem == "Folder") {
                await moveTo($scope.folders.all,$scope.folders);
            } else if (typeOriginalItem == "Course") {
                await moveTo($scope.courses.allCourses,$scope.courses);
            }
            await $scope.initFolders();
        };

        $scope.openNavMyCourses = function () {
            document.getElementById("mySidenavCourses").style.width = "200px";
        };

    }]);
