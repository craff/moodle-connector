import {ng} from "entcore";
import {Folder} from "../../model";
import {Utils} from "../../utils/Utils";

export const folderTargetTreeController = ng.controller('folderTargetTreeController', ['$scope',
    ($scope) => {

        $scope.isPrintTargetSubfolder = function (folder: Folder) {
            $scope.folders.folderIdMoveIn = folder.id;
            $scope.folders.all.forEach(function (e) {
                if (e.id == folder.id) {
                    e.printTargetSubfolder = !e.printTargetSubfolder;
                    folder.printTargetSubfolder = e.printTargetSubfolder;
                }
            });
            setPrintTargetSubfolderValueByFolder(folder, folder.printTargetSubfolder);
        };

        $scope.targetMenu = function () {
            $scope.folders.folderIdMoveIn = 0;
            $scope.folders.all.forEach(function (e) {
                e.printTargetSubfolder = false;
            });
        };

        const setPrintTargetSubfolderValueByFolder = function (folder: Folder, printTargetSubfolder: boolean) {
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

    }]);
