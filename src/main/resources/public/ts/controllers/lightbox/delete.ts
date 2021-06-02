import {model, ng, notify} from "entcore";

export const deleteController = ng.controller('deleteController', ['$scope',
    ($scope) => {

        $scope.deleteElements = async function () {
            $scope.show.disableDeleteSend = false;
            $scope.show.submitWait = true;
            if ($scope.folders.all.some(folder => folder.selectConfirm)) {
                await $scope.folders.foldersDelete();
                let idAllFoldersToDelete = $scope.folders.all.filter(folder => folder.selectConfirm).map(folder => folder.id);
                while (idAllFoldersToDelete.length != 0) {
                    let newFoldersToDelete = [];
                    idAllFoldersToDelete.forEach(function (idFolder) {
                        $scope.courses.allCourses.filter(course => course.folderId == idFolder).map(course => course.selectConfirm = true);
                        $scope.folders.getSubfolder(idFolder).map(folder => folder.id).forEach(function (id) {
                            newFoldersToDelete.push(id)
                        });
                    });
                    idAllFoldersToDelete = newFoldersToDelete;
                }
            }
            if ($scope.courses.allCourses.some(course => course.selectConfirm)) {
                $scope.courses.categoryType = $scope.show.principal_folder == 'PUBLISHED';
                await $scope.courses.coursesDelete()
                    .then(async (): Promise<void> => {
                        notify.success('moodle.info.deleteTextConfirmSuccess');
                    });
                await $scope.courses.getCoursesByUser(model.me.userId);
            }
            $scope.show.lightboxes = false;
            $scope.show.submitWait = false;
            await $scope.initFolders();
        };

    }]);
